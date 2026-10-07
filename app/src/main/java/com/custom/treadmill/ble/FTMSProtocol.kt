package com.custom.treadmill.ble

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
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
    private val desiredSpeed = MutableStateFlow<Double?>(null)
    private val desiredIncline = MutableStateFlow<Double?>(null)
    private var lastSentSpeed = -1.0
    private var lastSentIncline = -1.0
    private var sendersStarted = false

    /** Задержка после последнего нажатия перед отправкой (мс). */
    var debounceMs: Long = 350L

    /** Сколько ждать подтверждения между попытками (мс). */
    var confirmTimeoutMs: Long = 900L

    /** Максимум попыток. */
    var maxAttempts: Int = 2

    /** Округлять наклон до целых %. */
    var roundInclineToWhole: Boolean = true

    /** Округлять скорость вниз до целых км/ч (belt не понимает дробные). */
    var speedResolutionKmh: Double = 1.0

    /** Отправлять 0x07 (Start). Belt не поедет из STOP без него. */
    var sendStartCommand: Boolean = true

    /**
     * Период повторения команды скорости, пока belt едет (сек).
     * Belt держит скорость ~20 сек, потом сам возвращается на 1.00.
     */
    var speedKeepAliveSec: Int = 10

    private var keepAliveJob: Job? = null

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
            desiredSpeed.filterNotNull().collectLatest { target ->
                if (abs(target - lastSentSpeed) < 0.01) return@collectLatest
                delay(debounceMs)

                // "belt был остановлен" — это когда _data показывает 0 и НЕ
                // (lastSentSpeed был > 0.5 и мы его только что задали).
                // Проще: смотрим текущую реальную скорость belt'а.
                val wasStopped = _data.value.speedKmh < 0.5

                var confirmed = false
                for (attempt in 1..maxAttempts) {
                    sendMutex.withLock { sendSpeedNow(target, wasStopped) }
                    lastSentSpeed = target
                    delay(confirmTimeoutMs)
                    if (abs(_data.value.speedKmh - target) < 0.6 ||
                        (target == 0.0 && _data.value.speedKmh < 0.2)
                    ) {
                        confirmed = true
                        break
                    }
                    Log.w("FTMS", "Speed $target не подтверждена (попытка $attempt)")
                }
                Log.d("FTMS", if (confirmed)
                    "OK speed $target км/ч"
                else "FAIL speed $target после $maxAttempts попыток")

                if (target > 0.5) startKeepAlive(target) else stopKeepAlive()
            }
        }

        // ---------- Отправитель НАКЛОНА ----------
        scope.launch {
            desiredIncline.filterNotNull().collectLatest { target ->
                if (abs(target - lastSentIncline) < 0.01) return@collectLatest
                delay(debounceMs)

                var confirmed = false
                for (attempt in 1..maxAttempts) {
                    sendMutex.withLock { sendInclineNow(target) }
                    lastSentIncline = target
                    delay(confirmTimeoutMs)
                    if (abs(_data.value.inclinePercent - target) < 0.6) {
                        confirmed = true
                        break
                    }
                    Log.w("FTMS", "Incline $target% не подтверждён (попытка $attempt)")
                }
                Log.d("FTMS", if (confirmed)
                    "OK incline $target%"
                else "FAIL incline $target% после $maxAttempts попыток")
            }
        }
    }

    /**
     * Keep-alive: пока belt РЕАЛЬНО едет, повторяем 02 XX XX
     * каждые speedKeepAliveSec секунд. Belt держит скорость ~20 сек,
     * потом сам возвращается на 1.00.
     *
     * ВАЖНО: не работает, если belt реально стоит — иначе мы будем слать
     * команды скорости и мешать старту.
     */
    private fun startKeepAlive(target: Double) {
        stopKeepAlive()
        keepAliveJob = scope.launch {
            while (isActive) {
                delay(speedKeepAliveSec.coerceAtLeast(3) * 1000L)
                val current = desiredSpeed.value ?: return@launch
                if (current <= 0.5) return@launch
                // Дополнительная защита: если belt реально стоит — не шлём
                if (_data.value.speedKmh < 0.5) {
                    Log.d("FTMS", "keep-alive пропущен (belt стоит)")
                    continue
                }
                val raw = (current * 100.0).roundToInt().coerceIn(0, 65535)
                val uuid = controlPointUuid ?: return@launch
                val c = conn ?: return@launch
                sendMutex.withLock {
                    c.write(uuid, byteArrayOf(0x02, (raw and 0xFF).toByte(), ((raw shr 8) and 0xFF).toByte()), true)
                }
                Log.d("FTMS", "keep-alive speed $current")
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

    /**
     * Команда скорости. Два режима:
     *
     *   beltWasStopped = true  →  02 XX XX  07
     *     Старт с нуля. 00 убран — он тут ломает последовательность
     *     (в логе 20:35:52 видно, что "00 02 XX 07" belt не поднимает).
     *
     *   beltWasStopped = false →  02 XX XX
     *     Belt едет — просто меняем целевую.
     */
    private suspend fun sendSpeedNow(speedKmh: Double, beltWasStopped: Boolean) {
        val uuid = controlPointUuid ?: return
        val c = conn ?: return
        val raw = (speedKmh * 100.0).roundToInt().coerceIn(0, 65535)
        val cmd = byteArrayOf(0x02, (raw and 0xFF).toByte(), ((raw shr 8) and 0xFF).toByte())

        if (beltWasStopped && speedKmh > 0.0 && sendStartCommand) {
            c.write(uuid, cmd, true); delay(200)
            c.write(uuid, byteArrayOf(0x07), true)
            Log.d("FTMS", "-> speed $speedKmh [start 02+07]")
        } else {
            c.write(uuid, cmd, true)
            Log.d("FTMS", "-> speed $speedKmh [run 02]")
        }
    }

    private suspend fun sendInclineNow(percent: Double) {
        val uuid = controlPointUuid ?: return
        val c = conn ?: return
        val raw = (percent * 10.0).roundToInt().coerceIn(0, 32767)
        val cmd = byteArrayOf(0x03, (raw and 0xFF).toByte(), ((raw shr 8) and 0xFF).toByte())
        c.write(uuid, cmd, true)
        Log.d("FTMS", "-> incline $percent%")
    }

    override suspend fun requestControl(): Boolean {
        val uuid = controlPointUuid ?: return false
        return conn?.write(uuid, byteArrayOf(0x00), true) ?: false
    }

    override suspend fun start(): Boolean {
        val uuid = controlPointUuid ?: return false
        val c = conn ?: return false
        val s = desiredSpeed.value ?: 1.0
        val speed = applySpeedResolution(s.coerceAtLeast(1.0))
        val raw = (speed * 100.0).roundToInt().coerceIn(100, 65535)

        sendMutex.withLock {
            c.write(uuid,
                byteArrayOf(0x02, (raw and 0xFF).toByte(), ((raw shr 8) and 0xFF).toByte()),
                true
            )
            delay(200)
            if (sendStartCommand) {
                c.write(uuid, byteArrayOf(0x07), true)
            }
        }

        lastSentSpeed = speed
        desiredSpeed.value = speed
        Log.d("FTMS", "-> start (speed=$speed)")
        // Keep-alive включим после того, как belt реально поедет —
        // через пару секунд он получит первую нотификацию с speed>0.
        scope.launch {
            delay(2500)
            if (_data.value.speedKmh > 0.5) startKeepAlive(speed)
        }
        return true
    }

    override suspend fun stop(): Boolean {
        val uuid = controlPointUuid ?: return false
        val c = conn ?: return false

        stopKeepAlive()

        sendMutex.withLock {
            c.write(uuid, byteArrayOf(0x02, 0x00, 0x00), true)
        }
        lastSentSpeed = 0.0
        desiredSpeed.value = 0.0

        delay(700)
        val confirmed = _data.value.speedKmh < 0.2
        Log.d("FTMS", if (confirmed) "OK stop [02 00 00]" else "FAIL stop")
        return confirmed
    }

    override suspend fun setSpeed(speedKmh: Double): Boolean {
        val v = applySpeedResolution(speedKmh.coerceAtLeast(0.0))
        desiredSpeed.value = v
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
