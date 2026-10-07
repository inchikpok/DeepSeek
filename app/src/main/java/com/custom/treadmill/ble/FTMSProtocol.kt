package com.custom.treadmill.ble

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import java.util.UUID

class FTMSProtocol : ITreadmillProtocol {

    override val protocolName: String = "FTMS (стандарт)"

    private val _data = MutableStateFlow(TreadmillData())
    override val data: StateFlow<TreadmillData> = _data.asStateFlow()

    private var conn: BleConnection? = null
    private var controlPointUuid: UUID? = null
    override val writeCharacteristicUuid: UUID? get() = controlPointUuid

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sendMutex = Mutex()

    /** Колбэк для отправки сообщений в Debug-экран приложения. */
    var onLog: ((String) -> Unit)? = null

    /**
     * Поток команд скорости. Используем SharedFlow, а не StateFlow,
     * чтобы повторная команда с тем же значением всё равно обрабатывалась
     * (например, повторный «Старт» на той же скорости).
     */
    private val speedCommands = MutableSharedFlow<Double>(
        replay = 0, extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    private val desiredSpeed = MutableStateFlow<Double?>(null)
    private val desiredIncline = MutableStateFlow<Double?>(null)

    private var lastSentSpeed = -1.0
    private var lastSentIncline = -1.0
    private var sendersStarted = false

    /** Задержка после последнего нажатия (мс). */
    var debounceMs: Long = 350L

    /** Округлять наклон до целых %. */
    var roundInclineToWhole: Boolean = true

    /** Округлять скорость вниз до целых км/ч (belt не понимает дробные). */
    var speedResolutionKmh: Double = 1.0

    /** Отправлять 0x07 (Start). Belt не поедет из STOP без него. */
    var sendStartCommand: Boolean = true

    /** Пауза между 0x00 и 0x07 в команде старта (мс). */
    var startPreCommandDelayMs: Long = 150L

    /** Сколько ждать после 0x07, пока belt отсчитает 3-2-1 и выйдет на 1.0 км/ч (мс). */
    var startCountdownMs: Long = 4500L

    /** Период повторения команды скорости, пока belt едет (сек). */
    var speedKeepAliveSec: Int = 10

    private var keepAliveJob: Job? = null

    /** Время последней отправки старта — чтобы не долбить belt повторно. */
    @Volatile private var lastStartSentAt = 0L

    private fun log(msg: String) {
        Log.d("FTMS", msg)
        onLog?.invoke("FTMS: $msg")
    }

    override suspend fun initialize(conn: BleConnection): Boolean {
        this.conn = conn
        var controlFound = false

        for (uuid in Uuids.CONTROL_POINT_CANDIDATES) {
            if (conn.findCharacteristic(uuid) != null) {
                controlPointUuid = uuid
                controlFound = true
                Log.d("FTMS", "Control Point: $uuid")
                break
            }
        }

        val featureChar = conn.findCharacteristic(Uuids.FTMS_FEATURE)
        if (featureChar != null) {
            conn.read(featureChar.uuid)?.let { Log.d("FTMS", "Feature: ${it.toHex()}") }
        }

        if (conn.findCharacteristic(Uuids.FTMS_TREADMILL_DATA) != null) {
            conn.setNotify(Uuids.FTMS_TREADMILL_DATA, true, indicate = false)
        }
        controlPointUuid?.let { conn.setNotify(it, true, indicate = true) }

        lastSentSpeed = -1.0
        lastSentIncline = -1.0
        lastStartSentAt = 0L

        if (!sendersStarted) {
            sendersStarted = true
            startSenders()
        }

        delay(300)
        requestControl()
        return controlFound
    }

    private fun startSenders() {
        // ---------- Отправитель СКОРОСТИ ----------
        scope.launch {
            speedCommands.collectLatest { target ->
                delay(debounceMs)

                val wasStopped = _data.value.speedKmh < 0.5

                if (target > 0.5 && wasStopped) {
                    // Belt стоит. Нужна команда 00 + 07. После countdown
                    // belt сам стартует на 1.00, потом доведём до target.
                    startBeltAndFollowUp(target)
                } else {
                    // Belt едет — просто меняем целевую.
                    sendSpeedRaw(target)
                    lastSentSpeed = target
                }

                if (target > 0.5) startKeepAlive(target) else stopKeepAlive()
            }
        }

        // ---------- Отправитель НАКЛОНА ----------
        scope.launch {
            desiredIncline.collectLatest { target ->
                if (target == null) return@collectLatest
                if (abs(target - lastSentIncline) < 0.01) return@collectLatest
                delay(debounceMs)
                sendInclineRaw(target)
                lastSentIncline = target
                log("incline $target%")
            }
        }
    }

    /**
     * Старт belt: 00 (Request Control) + пауза + 07 (Start).
     * Belt уходит в countdown ~4 сек, потом стартует на 1.00 км/ч.
     * После этого (если target ≠ 1.0) доводим скорость до target.
     *
     * Защита: если старт был меньше 6 секунд назад — не повторяем,
     * иначе belt снова уйдёт в countdown.
     */
    private suspend fun startBeltAndFollowUp(target: Double) {
        val uuid = controlPointUuid ?: return
        val c = conn ?: return

        val now = System.currentTimeMillis()
        val recentStart = now - lastStartSentAt < 6000L

        if (!recentStart) {
            lastStartSentAt = now
            sendMutex.withLock {
                c.write(uuid, byteArrayOf(0x00), true)
                delay(startPreCommandDelayMs)
                if (sendStartCommand) {
                    c.write(uuid, byteArrayOf(0x07), true)
                }
            }
            log("старт (00+07), belt уйдёт в countdown")
        } else {
            log("старт уже идёт, пропускаю повторный 07")
        }

        // Belt сам стартует на 1.0 через ~4 сек. Ждём.
        delay(startCountdownMs)

        // Теперь belt на 1.0. Если target отличается — шлём 02 XX XX.
        if (abs(target - 1.0) > 0.1 && target > 0.5) {
            sendSpeedRaw(target)
            log("после countdown → $target")
        }
        lastSentSpeed = target
    }

    /**
     * Keep-alive: пока belt едет — повторяем 02 XX XX каждые
     * speedKeepAliveSec секунд. Belt держит ~20 сек, повторение обманывает watchdog.
     */
    private fun startKeepAlive(target: Double) {
        stopKeepAlive()
        keepAliveJob = scope.launch {
            while (isActive) {
                delay(speedKeepAliveSec.coerceAtLeast(3) * 1000L)
                val current = desiredSpeed.value ?: return@launch
                if (current <= 0.5) return@launch
                if (_data.value.speedKmh < 0.5) continue
                sendSpeedRaw(current)
                log("keep-alive $current")
            }
        }
    }

    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    private fun applySpeedResolution(v: Double): Double {
        if (v <= 0.0) return 0.0
        val res = speedResolutionKmh
        if (res <= 0.0) return v
        val floored = floor(v / res) * res
        return floored.coerceAtLeast(res)
    }

    /** Отправка скорости: только 02 XX XX. */
    private suspend fun sendSpeedRaw(speedKmh: Double) {
        val uuid = controlPointUuid ?: return
        val c = conn ?: return
        val raw = (speedKmh * 100.0).roundToInt().coerceIn(0, 65535)
        sendMutex.withLock {
            c.write(
                uuid,
                byteArrayOf(0x02, (raw and 0xFF).toByte(), ((raw shr 8) and 0xFF).toByte()),
                true
            )
        }
    }

    /** Отправка наклона: 03 XX XX (всегда одним write). */
    private suspend fun sendInclineRaw(percent: Double) {
        val uuid = controlPointUuid ?: return
        val c = conn ?: return
        val raw = (percent * 10.0).roundToInt().coerceIn(0, 32767)
        sendMutex.withLock {
            c.write(
                uuid,
                byteArrayOf(0x03, (raw and 0xFF).toByte(), ((raw shr 8) and 0xFF).toByte()),
                true
            )
        }
    }

    override suspend fun requestControl(): Boolean {
        val uuid = controlPointUuid ?: return false
        return conn?.write(uuid, byteArrayOf(0x00), true) ?: false
    }

    override suspend fun start(): Boolean {
        val target = applySpeedResolution((desiredSpeed.value ?: 1.0).coerceAtLeast(1.0))
        desiredSpeed.value = target
        // Всегда эмитим — даже если target не изменился
        speedCommands.tryEmit(target)
        return true
    }

    override suspend fun stop(): Boolean {
        val uuid = controlPointUuid ?: return false
        stopKeepAlive()
        lastStartSentAt = 0L
        sendMutex.withLock {
            conn?.write(uuid, byteArrayOf(0x02, 0x00, 0x00), true)
        }
        lastSentSpeed = 0.0
        desiredSpeed.value = 0.0
        log("стоп (02 00 00)")
        return true
    }

    override suspend fun setSpeed(speedKmh: Double): Boolean {
        val v = applySpeedResolution(speedKmh.coerceAtLeast(0.0))
        desiredSpeed.value = v
        speedCommands.tryEmit(v)
        return true
    }

    override suspend fun setIncline(percent: Double): Boolean {
        val t = if (roundInclineToWhole)
            percent.coerceIn(0.0, 30.0).roundToInt().toDouble()
        else percent.coerceIn(0.0, 30.0)
        desiredIncline.value = t
        return true
    }

    override fun onNotification(n: BleNotification) {
        when (n.characteristicUuid) {
            Uuids.FTMS_TREADMILL_DATA -> parseTreadmillData(n.value)
            Uuids.FTMS_CONTROL_POINT,
            Uuids.FTMS_CONTROL_POINT_ALT -> parseControlPointResponse(n.value)
        }
    }

    private fun parseControlPointResponse(b: ByteArray) {
        if (b.size >= 3 && (b[0].toInt() and 0xFF) == 0x80) {
            val op = b[1].toInt() and 0xFF
            val res = b[2].toInt() and 0xFF
            Log.d("FTMS", "CP resp op=0x%02X res=%d".format(op, res))
        }
    }

    private fun parseTreadmillData(b: ByteArray) {
        if (b.size < 4) return
        try {
            var o = 2
            val flags = u16(b, 0)
            var speed = _data.value.speedKmh
            var incline = 0.0
            var distance = _data.value.distanceKm
            var calories = _data.value.calories
            var elapsed = _data.value.elapsedSec
            var hr = _data.value.heartRate

            if (o + 2 <= b.size) {
                speed = u16(b, o) / 100.0
                o += 2
            }
            if (flags and (1 shl 1) != 0) o += 2
            if (flags and (1 shl 2) != 0) {
                if (o + 3 <= b.size) distance = u24(b, o) / 1000.0
                o += 3
            }
            if (flags and (1 shl 3) != 0) {
                if (o + 4 <= b.size) incline = s16(b, o) / 10.0
                o += 4
            }
            if (flags and (1 shl 4) != 0) o += 4
            if (flags and (1 shl 5) != 0) o += 1
            if (flags and (1 shl 6) != 0) o += 1
            if (flags and (1 shl 7) != 0) {
                if (o + 2 <= b.size) calories = u16(b, o)
                o += 5
            }
            if (flags and (1 shl 8) != 0) {
                if (o + 1 <= b.size) hr = b[o].toInt() and 0xFF
                o += 1
            }
            if (flags and (1 shl 9) != 0) o += 1
            if (flags and (1 shl 10) != 0) {
                if (o + 2 <= b.size) elapsed = u16(b, o)
                o += 2
            }

            _data.value = TreadmillData(
                speedKmh = speed,
                inclinePercent = incline,
                distanceKm = distance,
                calories = calories,
                elapsedSec = elapsed,
                heartRate = hr,
                isRunning = speed > 0.05
            )
        } catch (e: Exception) {
            Log.w("FTMS", "Ошибка разбора: ${e.message}")
        }
    }

    private fun u16(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private fun s16(b: ByteArray, o: Int): Int {
        val raw = u16(b, o)
        return if (raw >= 0x8000) raw - 0x10000 else raw
    }

    private fun u24(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or
                ((b[o + 1].toInt() and 0xFF) shl 8) or
                ((b[o + 2].toInt() and 0xFF) shl 16)
}
