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

    // ---- Коалесцирующие отправители с повтором ----
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sendMutex = Mutex()
    private val desiredSpeed = MutableStateFlow<Double?>(null)
    private val desiredIncline = MutableStateFlow<Double?>(null)
    private var lastSentSpeed = -1.0
    private var lastSentIncline = -1.0
    private var sendersStarted = false

    /** Задержка после последнего нажатия перед отправкой (мс). */
    var debounceMs: Long = 250L

    /** Пауза после успешной отправки команды (мс). */
    var commandGapMs: Long = 400L

    /** Сколько ждать подтверждения от дорожки (мс). */
    var confirmTimeoutMs: Long = 2500L

    /** Максимум попыток отправки (1 + 2 повтора). */
    var maxAttempts: Int = 3

    /** Округлять наклон до целых %. */
    var roundInclineToWhole: Boolean = true

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
                var attempt = 1
                while (attempt <= maxAttempts && !confirmed) {
                    sendMutex.withLock {
                        sendSpeedNow(target)
                    }
                    lastSentSpeed = target
                    confirmed = waitForSpeed(target, confirmTimeoutMs)
                    if (!confirmed) {
                        Log.w("FTMS", "Speed $target не подтверждена (попытка $attempt)")
                        attempt++
                        delay(150)
                    }
                }
                if (confirmed) {
                    Log.d("FTMS", "✓ speed $target км/ч (попыток: $attempt)")
                } else {
                    Log.e("FTMS", "✗ speed $target не удалось после $maxAttempts попыток")
                }
                delay(commandGapMs)
            }
        }

        // ---------- Отправитель НАКЛОНА ----------
        scope.launch {
            desiredIncline.filterNotNull().collectLatest { target ->
                if (abs(target - lastSentIncline) < 0.01) return@collectLatest
                delay(debounceMs)

                var confirmed = false
                var attempt = 1
                while (attempt <= maxAttempts && !confirmed) {
                    sendMutex.withLock {
                        sendInclineNow(target)
                    }
                    lastSentIncline = target
                    confirmed = waitForIncline(target, confirmTimeoutMs)
                    if (!confirmed) {
                        Log.w("FTMS", "Incline $target% не подтверждён (попытка $attempt)")
                        attempt++
                        delay(150)
                    }
                }
                if (confirmed) {
                    Log.d("FTMS", "✓ incline $target% (попыток: $attempt)")
                } else {
                    Log.e("FTMS", "✗ incline $target% не удалось после $maxAttempts попыток")
                }
                delay(commandGapMs)
            }
        }
    }

    /** Ждём, пока дорожка не подтвердит скорость ±0.15 км/ч. */
    private suspend fun waitForSpeed(target: Double, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (abs(_data.value.speedKmh - target) < 0.15) return true
            delay(100)
        }
        return false
    }

    /** Ждём, пока дорожка не подтвердит наклон ±0.6%. */
    private suspend fun waitForIncline(target: Double, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (abs(_data.value.inclinePercent - target) < 0.6) return true
            delay(100)
        }
        return false
    }

    private suspend fun sendSpeedNow(speedKmh: Double) {
        val uuid = controlPointUuid ?: return
        val c = conn ?: return
        val raw = (speedKmh * 100.0).roundToInt().coerceIn(0, 65535)
        c.write(uuid, byteArrayOf(0x00), true)
        delay(80)
        val cmd = byteArrayOf(0x02, (raw and 0xFF).toByte(), ((raw shr 8) and 0xFF).toByte())
        c.write(uuid, cmd, true)
        Log.d("FTMS", "→ speed $speedKmh км/ч  ($cmd)")
    }

    private suspend fun sendInclineNow(percent: Double) {
        val uuid = controlPointUuid ?: return
        val c = conn ?: return
        val raw = (percent * 10.0).roundToInt().coerceIn(0, 32767)
        c.write(uuid, byteArrayOf(0x00), true)
        delay(80)
        val cmd = byteArrayOf(0x03, (raw and 0xFF).toByte(), ((raw shr 8) and 0xFF).toByte())
        c.write(uuid, cmd, true)
        Log.d("FTMS", "→ incline $percent%  ($cmd)")
    }

    override suspend fun requestControl(): Boolean {
        val uuid = controlPointUuid ?: return false
        return conn?.write(uuid, byteArrayOf(0x00), true) ?: false
    }

    override suspend fun start(): Boolean {
        val uuid = controlPointUuid ?: return false
        sendMutex.withLock {
            conn?.write(uuid, byteArrayOf(0x00), true); delay(80)
            conn?.write(uuid, byteArrayOf(0x07), true)
        }
        Log.d("FTMS", "→ start")
        return true
    }

    /**
     * Стоп. Ключевая команда — установить скорость 0 (0x02 0x00 0x00).
     * 0x08 0x01 (Stop/Pause) на этой дорожке игнорируется, но отправляем
     * её второй — на всякий случай.
     */
    override suspend fun stop(): Boolean {
        val uuid = controlPointUuid ?: return false
        val c = conn ?: return false

        sendMutex.withLock {
            c.write(uuid, byteArrayOf(0x00), true); delay(80)
            c.write(uuid, byteArrayOf(0x02, 0x00, 0x00), true); delay(80)
            c.write(uuid, byteArrayOf(0x08, 0x01), true)
        }
        lastSentSpeed = 0.0
        desiredSpeed.value = null   // сброс очереди

        // Убедимся, что скорость реально падает
        var confirmed = waitForSpeed(0.0, confirmTimeoutMs)
        if (!confirmed) {
            sendMutex.withLock {
                c.write(uuid, byteArrayOf(0x00), true); delay(80)
                c.write(uuid, byteArrayOf(0x02, 0x00, 0x00), true)
            }
            confirmed = waitForSpeed(0.0, confirmTimeoutMs)
        }
        Log.d("FTMS", if (confirmed) "✓ stop" else "✗ stop не подтверждён")
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
            Log.d("FTMS", "CP resp op=0x%02X res=%d"
                .format(b[1].toInt() and 0xFF, b[2].toInt() and 0xFF))
        }
    }

    private fun parseTreadmillData(b: ByteArray) {
        if (b.size < 4) return
        try {
            var o = 0
            val flags = u16(b, 0); o = 2
            var speed = _data.value.speedKmh
            var incline = 0.0
            var distance = _data.value.distanceKm
            var calories = _data.value.calories
            var elapsed = _data.value.elapsedSec
            var hr = _data.value.heartRate

            if (o + 2 <= b.size) { speed = u16(b, o) / 100.0; o += 2 }
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
                speedKmh = speed, inclinePercent = incline, distanceKm = distance,
                calories = calories, elapsedSec = elapsed, heartRate = hr,
                isRunning = speed > 0.05
            )
        } catch (e: Exception) {
            Log.w("FTMS", "Ошибка разбора: ${e.message}")
        }
    }

    private fun u16(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private fun s16(b: ByteArray, o: Int): Int {
        val raw = u16(b, o); return if (raw >= 0x8000) raw - 0x10000 else raw
    }

    private fun u24(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
            or ((b[o + 2].toInt() and 0xFF) shl 16)
}
