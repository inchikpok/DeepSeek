package com.custom.treadmill.ble

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    override val data: StateFlow<TreadmillData> = _data

    private var conn: BleConnection? = null
    private var controlPointUuid: UUID? = null
    override val writeCharacteristicUuid: UUID? get() = controlPointUuid

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sendMutex = Mutex()

    var onLog: ((String) -> Unit)? = null

    var sendStartCommand: Boolean = true
    var startPreCommandDelayMs: Long = 200L

    /** Разрешение скорости. 0.0 = не округлять. */
    var speedResolutionKmh: Double = 0.0

    var roundInclineToWhole: Boolean = true

    /** Что мы в последний раз просили у belt'а. */
    @Volatile private var lastRequestedSpeed = 0.0
    @Volatile private var lastRequestedIncline = 0.0

    /** Когда последний раз отправляли. */
    @Volatile private var lastSpeedSentAt = 0L
    @Volatile private var lastInclineSentAt = 0L

    private var retryJob: Job? = null

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

        lastRequestedSpeed = 0.0
        lastRequestedIncline = 0.0
        lastSpeedSentAt = 0L
        lastInclineSentAt = 0L

        delay(300)
        requestControl()
        wakeUp()
        startRetryLoop()

        return controlFound
    }

    private suspend fun wakeUp() {
        val uuid = controlPointUuid ?: return
        val c = conn ?: return
        try {
            sendMutex.withLock {
                c.write(uuid, byteArrayOf(0x00), true); delay(200)
                c.write(uuid, byteArrayOf(0x01), true); delay(200)
                c.write(uuid, byteArrayOf(0x00), true)
            }
            log("wake-up burst отправлен (00 01 00)")
        } catch (e: Exception) {
            Log.w("FTMS", "wake-up failed: ${e.message}")
        }
    }

    /**
     * Retry-цикл: если belt отклонился от целевой скорости или наклона
     * и с момента последней отправки прошло > 5 сек — повторяем.
     *
     * Это защита от:
     * • watchdog'а belt'а (belt сам сбрасывает скорость через 20–30 сек);
     * • потери отдельных команд (belt иногда пропускает первую).
     */
    private fun startRetryLoop() {
        if (retryJob?.isActive == true) return
        retryJob = scope.launch {
            while (isActive) {
                delay(2500L)
                val now = System.currentTimeMillis()

                // --- Скорость ---
                val speedTarget = lastRequestedSpeed
                if (speedTarget >= 0.5 && now - lastSpeedSentAt >= 5000L) {
                    val actual = _data.value.speedKmh
                    if (abs(actual - speedTarget) >= 0.5) {
                        sendSpeedRaw(speedTarget)
                        lastSpeedSentAt = now
                        log("retry speed $speedTarget (belt at $actual)")
                    }
                }

                // --- Наклон ---
                if (now - lastInclineSentAt >= 5000L) {
                    val incTarget = lastRequestedIncline
                    val actual = _data.value.inclinePercent
                    if (abs(actual - incTarget) >= 0.5) {
                        sendInclineRaw(incTarget)
                        lastInclineSentAt = now
                        log("retry incline $incTarget (belt at $actual)")
                    }
                }
            }
        }
    }

    private fun applySpeedResolution(v: Double): Double {
        if (v <= 0.0) return 0.0
        val res = speedResolutionKmh
        if (res <= 0.0) return v
        val floored = floor(v / res) * res
        return floored.coerceAtLeast(res)
    }

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

    /**
     * Отправка наклона. Дублируем с паузой 200 мс — belt иногда пропускает
     * первую команду.
     */
    private suspend fun sendInclineRaw(percent: Double) {
        val uuid = controlPointUuid ?: return
        val c = conn ?: return
        val raw = (percent * 10.0).roundToInt().coerceIn(0, 32767)
        val cmd = byteArrayOf(0x03, (raw and 0xFF).toByte(), ((raw shr 8) and 0xFF).toByte())

        sendMutex.withLock { c.write(uuid, cmd, true) }
        delay(200)
        sendMutex.withLock { c.write(uuid, cmd, true) }
    }

    override suspend fun requestControl(): Boolean {
        val uuid = controlPointUuid ?: return false
        return conn?.write(uuid, byteArrayOf(0x00), true) ?: false
    }

    override suspend fun start(): Boolean {
        val uuid = controlPointUuid ?: return false
        val c = conn ?: return false

        sendMutex.withLock {
            c.write(uuid, byteArrayOf(0x00), true)
            delay(startPreCommandDelayMs)
            if (sendStartCommand) {
                c.write(uuid, byteArrayOf(0x07), true)
            }
        }
        log("старт (00+07)")
        return true
    }

    override suspend fun stop(): Boolean {
        val uuid = controlPointUuid ?: return false
        lastRequestedSpeed = 0.0
        sendMutex.withLock {
            conn?.write(uuid, byteArrayOf(0x02, 0x00, 0x00), true)
        }
        log("стоп (02 00 00)")
        return true
    }

    override suspend fun setSpeed(speedKmh: Double): Boolean {
        val v = applySpeedResolution(speedKmh.coerceAtLeast(0.0))
        lastRequestedSpeed = v
        lastSpeedSentAt = System.currentTimeMillis()
        sendSpeedRaw(v)
        log("скорость $v")
        return true
    }

    override suspend fun setIncline(percent: Double): Boolean {
        val t = if (roundInclineToWhole)
            percent.coerceIn(0.0, 30.0).roundToInt().toDouble()
        else percent.coerceIn(0.0, 30.0)
        lastRequestedIncline = t
        lastInclineSentAt = System.currentTimeMillis()
        sendInclineRaw(t)
        log("наклон $t%")
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

    /**
     * Разбор Treadmill Data (0x2ACD).
     *
     * ВАЖНО: если флаг incline отсутствует — НЕ сбрасываем incline в 0,
     * а оставляем последнее известное значение. Belt часто присылает
     * несколько фреймов без incline, хотя наклон реально установлен.
     */
    private fun parseTreadmillData(b: ByteArray) {
        if (b.size < 4) return
        try {
            var o = 2
            val flags = u16(b, 0)
            val prev = _data.value

            var speed = prev.speedKmh
            var incline = prev.inclinePercent      // ← сохраняем, не сбрасываем
            var distance = prev.distanceKm
            var calories = prev.calories
            var elapsed = prev.elapsedSec
            var hr = prev.heartRate

            if (o + 2 <= b.size) {
                speed = u16(b, o) / 100.0
                o += 2
            }
            if (flags and (1 shl 1) != 0) o += 2  // Average Speed
            if (flags and (1 shl 2) != 0) {        // Total Distance
                if (o + 3 <= b.size) distance = u24(b, o) / 1000.0
                o += 3
            }
            if (flags and (1 shl 3) != 0) {        // Inclination + Ramp Angle
                if (o + 4 <= b.size) incline = s16(b, o) / 10.0
                o += 4
            }
            if (flags and (1 shl 4) != 0) o += 4   // Elevation Gain
            if (flags and (1 shl 5) != 0) o += 1   // Inst. Pace
            if (flags and (1 shl 6) != 0) o += 1   // Avg. Pace
            if (flags and (1 shl 7) != 0) {        // Expended Energy
                if (o + 2 <= b.size) calories = u16(b, o)
                o += 5
            }
            if (flags and (1 shl 8) != 0) {        // Heart Rate
                if (o + 1 <= b.size) hr = b[o].toInt() and 0xFF
                o += 1
            }
            if (flags and (1 shl 9) != 0) o += 1   // Metabolic Eq
            if (flags and (1 shl 10) != 0) {       // Elapsed Time
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
