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

    // ---- Коалесцирующие отправители ----
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sendMutex = Mutex()
    private val desiredSpeed = MutableStateFlow<Double?>(null)
    private val desiredIncline = MutableStateFlow<Double?>(null)
    private var lastSentSpeed = -1.0
    private var lastSentIncline = -1.0
    private var sendersStarted = false

    /** Задержка после последнего нажатия перед отправкой (мс). */
    var debounceMs: Long = 250L

    /** Пауза после отправки команды перед следующей (мс). */
    var commandGapMs: Long = 600L

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
        // Отправитель скорости — collectLatest отменяет предыдущий при новом значении
        scope.launch {
            desiredSpeed.filterNotNull().collectLatest { target ->
                if (abs(target - lastSentSpeed) < 0.01) return@collectLatest
                delay(debounceMs)
                sendMutex.withLock {
                    if (abs(target - lastSentSpeed) < 0.01) return@withLock
                    sendSpeedNow(target)
                    lastSentSpeed = target
                }
                delay(commandGapMs)
            }
        }
        // Отправитель наклона
        scope.launch {
            desiredIncline.filterNotNull().collectLatest { target ->
                if (abs(target - lastSentIncline) < 0.01) return@collectLatest
                delay(debounceMs)
                sendMutex.withLock {
                    if (abs(target - lastSentIncline) < 0.01) return@withLock
                    sendInclineNow(target)
                    lastSentIncline = target
                }
                delay(commandGapMs)
            }
        }
    }

    private suspend fun sendSpeedNow(speedKmh: Double) {
        val uuid = controlPointUuid ?: return
        val c = conn ?: return
        val raw = (speedKmh * 100.0).roundToInt().coerceIn(0, 65535)
        c.write(uuid, byteArrayOf(0x00), true)
        delay(80)
        val cmd = byteArrayOf(0x02, (raw and 0xFF).toByte(), ((raw shr 8) and 0xFF).toByte())
        c.write(uuid, cmd, true)
        Log.d("FTMS", "→ speed $speedKmh км/ч")
    }

    private suspend fun sendInclineNow(percent: Double) {
        val uuid = controlPointUuid ?: return
        val c = conn ?: return
        val raw = (percent * 10.0).roundToInt().coerceIn(0, 32767)
        c.write(uuid, byteArrayOf(0x00), true)
        delay(80)
        val cmd = byteArrayOf(0x03, (raw and 0xFF).toByte(), ((raw shr 8) and 0xFF).toByte())
        c.write(uuid, cmd, true)
        Log.d("FTMS", "→ incline $percent%")
    }

    override suspend fun requestControl(): Boolean {
        val uuid = controlPointUuid ?: return false
        return conn?.write(uuid, byteArrayOf(0x00), true) ?: false
    }

    override suspend fun start(): Boolean {
        val uuid = controlPointUuid ?: return false
        conn?.write(uuid, byteArrayOf(0x00), true); delay(80)
        return conn?.write(uuid, byteArrayOf(0x07), true) ?: false
    }

    override suspend fun stop(): Boolean {
        val uuid = controlPointUuid ?: return false
        conn?.write(uuid, byteArrayOf(0x00), true); delay(80)
        return conn?.write(uuid, byteArrayOf(0x08, 0x01), true) ?: false
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
            Log.d("FTMS", "CP resp op=0x%02X res=%d".format(b[1].toInt() and 0xFF, b[2].toInt() and 0xFF))
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
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or ((b[o + 2].toInt() and 0xFF) shl 16)
}
