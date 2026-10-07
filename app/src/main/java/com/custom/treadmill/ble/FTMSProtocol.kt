package com.custom.treadmill.ble

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
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
    var debounceMs: Long = 200L

    /** Сколько ждать подтверждения между попытками (мс). */
    var confirmTimeoutMs: Long = 800L

    /** Максимум попыток. */
    var maxAttempts: Int = 2

    /** Округлять наклон до целых %. */
    var roundInclineToWhole: Boolean = true

    // ========================================================================
    //  Тумблеры совместимости (меняются в Настройках, применяются сразу)
    // ========================================================================

    /** Отправлять 0x07 (Start/Resume) в методе start(). */
    var sendStartCommand: Boolean = true

    /**
     * Отправлять 0x00 (Request Control) перед каждой командой скорости/наклона.
     * В логе belt его спокойно принимает — по умолчанию ВКЛ.
     */
    var requestControlBeforeEachCommand: Boolean = true

    /**
     * Отправлять 0x07 (Start/Resume) ПОСЛЕ каждой команды скорости.
     *
     * КЛЮЧЕВОЕ: судя по логу, этот belt не применяет новую скорость, пока
     * не получит 0x07. Тот же приём уже используется в start() — поэтому
     * дорожка реагирует на «Старт», но игнорирует кнопки +/− скорости.
     *
     * По умолчанию ВКЛ.
     */
    var sendStartAfterSpeedChange: Boolean = true

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

                var confirmed = false
                for (attempt in 1..maxAttempts) {
                    sendMutex.withLock { sendSpeedNow(target) }
                    lastSentSpeed = target
                    delay(confirmTimeoutMs)
                    if (abs(_data.value.speedKmh - target) < 0.15 ||
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
     * Команда скорости. Формат, который реально понимает этот belt:
     *   0x00           — Request Control (опционально)
     *   0x02 XX XX     — Set Target Speed, XX XX = км/ч × 100
     *   0x07           — Start/Resume: belt ПРИМЕНЯЕТ скорость только после этого
     */
    private suspend fun sendSpeedNow(speedKmh: Double) {
        val uuid = controlPointUuid ?: return
        val c = conn ?: return
        val raw = (speedKmh * 100.0).roundToInt().coerceIn(0, 65535)
        val cmd = byteArrayOf(0x02, (raw and 0xFF).toByte(), ((raw shr 8) and 0xFF).toByte())

        if (requestControlBeforeEachCommand) {
            c.write(uuid, byteArrayOf(0x00), true)
            delay(80)
        }
        c.write(uuid, cmd, true)

        // Ключевой момент: без 0x07 belt НЕ применяет новую скорость.
        if (sendStartAfterSpeedChange && speedKmh > 0.0) {
            delay(150)
            c.write(uuid, byteArrayOf(0x07), true)
        }

        Log.d(
            "FTMS",
            "-> speed $speedKmh" +
                    (if (requestControlBeforeEachCommand) " [RC]" else "") +
                    (if (sendStartAfterSpeedChange && speedKmh > 0.0) " [07]" else "")
        )
    }

    /**
     * Команда наклона. Belt принимает 0x03 XX XX сам по себе — 0x07 не нужен.
     * XX XX = % × 10.
     */
    private suspend fun sendInclineNow(percent: Double) {
        val uuid = controlPointUuid ?: return
        val c = conn ?: return
        val raw = (percent * 10.0).roundToInt().coerceIn(0, 32767)
        val cmd = byteArrayOf(0x03, (raw and 0xFF).toByte(), ((raw shr 8) and 0xFF).toByte())

        if (requestControlBeforeEachCommand) {
            c.write(uuid, byteArrayOf(0x00), true)
            delay(80)
        }
        c.write(uuid, cmd, true)
        Log.d("FTMS", "-> incline $percent%")
    }

    override suspend fun requestControl(): Boolean {
        val uuid = controlPointUuid ?: return false
        return conn?.write(uuid, byteArrayOf(0x00), true) ?: false
    }

    /**
     * Запуск дорожки. Тот же паттерн, что и у скорости:
     *   0x00 → 0x02 XX XX → 0x07
     * Минимальная скорость — 1.0 км/ч (belt не любит 0.5).
     */
    override suspend fun start(): Boolean {
        val uuid = controlPointUuid ?: return false
        val c = conn ?: return false
        val s = desiredSpeed.value ?: 1.0
        val speed = s.coerceAtLeast(1.0)
        val raw = (speed * 100.0).roundToInt().coerceIn(100, 65535)

        sendMutex.withLock {
            c.write(uuid, byteArrayOf(0x00), true)
            delay(100)
            c.write(
                uuid,
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
        Log.d("FTMS", "-> start (speed=$speed, cmd07=$sendStartCommand)")
        return true
    }

    /**
     * Стоп. Сначала 02 00 00 (belt реагирует именно на это).
     * Если через 900 мс скорость не упала — вторая попытка через 08 01.
     */
    override suspend fun stop(): Boolean {
        val uuid = controlPointUuid ?: return false
        val c = conn ?: return false

        sendMutex.withLock {
            c.write(uuid, byteArrayOf(0x00), true); delay(80)
            c.write(uuid, byteArrayOf(0x02, 0x00, 0x00), true); delay(120)
            c.write(uuid, byteArrayOf(0x02, 0x00, 0x00), true)
        }
        lastSentSpeed = 0.0
        desiredSpeed.value = 0.0

        delay(900)
        if (_data.value.speedKmh < 0.2) {
            Log.d("FTMS", "OK stop")
            return true
        }

        Log.w("FTMS", "stop через 02 00 00 не сработал, пробуем 08 01")
        sendMutex.withLock {
            c.write(uuid, byteArrayOf(0x08, 0x01), true)
        }
        delay(600)
        val confirmed = _data.value.speedKmh < 0.2
        Log.d("FTMS", if (confirmed) "OK stop (08 01)" else "FAIL stop не подтверждён")
        return confirmed
    }

    override suspend fun setSpeed(speedKmh: Double): Boolean {
        desiredSpeed.value = speedKmh.coerceAtLeast(0.0)
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
