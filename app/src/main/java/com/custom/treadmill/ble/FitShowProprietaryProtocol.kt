package com.custom.treadmill.ble

import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class FitShowProprietaryProtocol(
    private val manualWriteUuid: UUID? = null,
    private val manualNotifyUuid: UUID? = null
) : ITreadmillProtocol {

    override val protocolName: String = "FitShow (proprietary)"

    private val _data = MutableStateFlow(TreadmillData())
    override val data: StateFlow<TreadmillData> = _data.asStateFlow()

    private var conn: BleConnection? = null

    private var writeUuid: UUID? = null
    override val writeCharacteristicUuid: UUID? get() = writeUuid

    private var notifyUuid: UUID? = null

    var speedScale: Double = 10.0
    var inclineScale: Double = 10.0

    override suspend fun initialize(conn: BleConnection): Boolean {
        this.conn = conn

        if (manualWriteUuid != null && conn.findCharacteristic(manualWriteUuid) != null) {
            writeUuid = manualWriteUuid
        }
        if (manualNotifyUuid != null && conn.findCharacteristic(manualNotifyUuid) != null) {
            notifyUuid = manualNotifyUuid
        }

        val writeMask = BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
        val notifyMask = BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                BluetoothGattCharacteristic.PROPERTY_INDICATE

        if (writeUuid == null || notifyUuid == null) {
            for (info in conn.characteristics.value) {
                val service = info.serviceUuid.toString().lowercase()
                val isCandidate =
                    service.startsWith("0000fff") ||
                    service.startsWith("0000ffe") ||
                    service.startsWith("0000ff0") ||
                    service.startsWith("0000fee") ||
                    Uuids.FITSHOW_SERVICE_CANDIDATES.contains(info.serviceUuid)
                if (!isCandidate) continue
                if (writeUuid == null && (info.properties and writeMask) != 0) {
                    writeUuid = info.uuid
                    Log.d("FitShow", "write: ${info.uuid}")
                }
                if (notifyUuid == null && (info.properties and notifyMask) != 0) {
                    notifyUuid = info.uuid
                    Log.d("FitShow", "notify: ${info.uuid}")
                }
            }
        }

        if (writeUuid == null) {
            writeUuid = conn.characteristics.value
                .firstOrNull {
                    (it.properties and writeMask) != 0 &&
                    it.uuid != Uuids.FTMS_CONTROL_POINT &&
                    it.uuid != Uuids.FTMS_CONTROL_POINT_ALT
                }?.uuid
        }

        notifyUuid?.let { conn.setNotify(it, true, indicate = false) }
        delay(300)
        return writeUuid != null
    }

    override suspend fun requestControl(): Boolean = true

    override suspend fun start(): Boolean {
        val uuid = writeUuid ?: return false
        return conn?.write(uuid, byteArrayOf(0xA1.toByte()), true) ?: false
    }

    override suspend fun stop(): Boolean {
        val uuid = writeUuid ?: return false
        return conn?.write(uuid, byteArrayOf(0xA2.toByte()), true) ?: false
    }

    override suspend fun setSpeed(speedKmh: Double): Boolean {
        val uuid = writeUuid ?: return false
        val raw = (speedKmh * speedScale).toInt().coerceIn(0, 255).toByte()
        return conn?.write(uuid, byteArrayOf(0xA3.toByte(), raw), true) ?: false
    }

    /**
     * Гипотетическая команда наклона: 0xA4 + байт (percent * inclineScale).
     * Реальный протокол FitShow может отличаться — калибруется через Debug.
     */
    override suspend fun setIncline(percent: Double): Boolean {
        val uuid = writeUuid ?: return false
        val raw = (percent * inclineScale).toInt().coerceIn(0, 255).toByte()
        return conn?.write(uuid, byteArrayOf(0xA4.toByte(), raw), true) ?: false
    }

    override fun onNotification(n: BleNotification) {
        if (n.characteristicUuid == notifyUuid) {
            Log.d("FitShow", "notify ${n.value.toHex()}")
        }
    }
}
