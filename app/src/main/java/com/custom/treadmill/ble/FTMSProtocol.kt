package com.custom.treadmill.ble

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class FTMSProtocol : ITreadmillProtocol {

    override val protocolName: String = "FTMS (стандарт)"

    private val _data = MutableStateFlow(TreadmillData())
    override val data: StateFlow<TreadmillData> = _data.asStateFlow()

    private var conn: BleConnection? = null

    private var controlPointUuid: UUID? = null
    override val writeCharacteristicUuid: UUID? get() = controlPointUuid

    override suspend fun initialize(conn: BleConnection): Boolean {
        this.conn = conn
        var controlFound = false

        for (uuid in Uuids.CONTROL_POINT_CANDIDATES) {
            if (conn.findCharacteristic(uuid) != null) {
                controlPointUuid = uuid
                controlFound = true
                Log.d("FTMS", "Control Point найден: $uuid")
                break
            }
        }
        if (!controlFound) Log.w("FTMS", "Fitness Machine Control Point не найден")

        if (conn.findCharacteristic(Uuids.FTMS_TREADMILL_DATA) != null) {
            conn.setNotify(Uuids.FTMS_TREADMILL_DATA, true, indicate = false)
        }
        controlPointUuid?.let { conn.setNotify(it, true, indicate = true) }
        if (conn.findCharacteristic(Uuids.FTMS_STATUS) != null) {
            conn.setNotify(Uuids.FTMS_STATUS, true, indicate = false)
        }

        delay(300)
        return controlFound
    }

    override suspend fun requestControl(): Boolean {
        val uuid = controlPointUuid ?: return false
        return conn?.write(uuid, byteArrayOf(0x00), true) ?: false
    }

    override suspend fun start(): Boolean {
        val uuid = controlPointUuid ?: return false
        requestControl()
        delay(120)
        return conn?.write(uuid, byteArrayOf(0x07), true) ?: false
    }

    override suspend fun stop(): Boolean {
        val uuid = controlPointUuid ?: return false
        requestControl()
        delay(120)
        return conn?.write(uuid, byteArrayOf(0x08, 0x01), true) ?: false
    }

    override suspend fun setSpeed(speedKmh: Double): Boolean {
        val uuid = controlPointUuid ?: return false
        val raw = (speedKmh.coerceAtLeast(0.0) * 100.0).toInt().coerceIn(0, 65535)
        val cmd = byteArrayOf(
            0x02,
            (raw and 0xFF).toByte(),
            ((raw shr 8) and 0xFF).toByte()
        )
        requestControl()
        delay(80)
        return conn?.write(uuid, cmd, true) ?: false
    }

    override fun onNotification(n: BleNotification) {
        when (n.characteristicUuid) {
            Uuids.FTMS_TREADMILL_DATA -> parseTreadmillData(n.value)
            Uuids.FTMS_CONTROL_POINT,
            Uuids.FTMS_CONTROL_POINT_ALT -> parseControlPointResponse(n.value)
            Uuids.FTMS_STATUS -> Log.d("FTMS", "Status: ${n.value.toHex()}")
        }
    }

    private fun parseControlPointResponse(b: ByteArray) {
        if (b.size < 3) return
        if ((b[0].toInt() and 0xFF) == 0x80) {
            val reqOp = b[1].toInt() and 0xFF
            val result = b[2].toInt() and 0xFF
            Log.d("FTMS", "ControlPoint Response: op=$reqOp result=$result")
        }
    }

    private fun parseTreadmillData(b: ByteArray) {
        if (b.size < 4) return
        try {
            var o = 0
            val flags = u16(b, 0); o = 2

            var speed = _data.value.speedKmh
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
            if (flags and (1 shl 3) != 0) o += 4
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
                distanceKm = distance,
                calories = calories,
                elapsedSec = elapsed,
                heartRate = hr,
                isRunning = speed > 0.05
            )
        } catch (e: Exception) {
            Log.w("FTMS", "Ошибка разбора Treadmill Data: ${e.message}")
        }
    }

    private fun u16(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private fun u24(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or
        ((b[o + 1].toInt() and 0xFF) shl 8) or
        ((b[o + 2].toInt() and 0xFF) shl 16)
}
