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

    /** Какую команду наклона использовать. Меняется через Debug-экран. */
    var inclineCommandMode: InclineCommandMode = InclineCommandMode.A4_BYTE

    enum class InclineCommandMode {
        A4_BYTE,        // A4 XX                          (XX = %)
        A4_BYTE_10X,    // A4 XX                          (XX = % * 10)
        A4_01_BYTE,     // A4 01 XX
        A4_BYTE_SIGN,   // A4 XX                          (XX = % signed)
        B0_BYTE,        // B0 XX
        A5_BYTE         // A5 XX
    }

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

    override suspend fun setIncline(percent: Double): Boolean {
        val uuid = writeUuid ?: run {
            Log.w("FitShow", "setIncline: характеристика записи не найдена")
            return false
        }
        val cmd = buildInclineCommand(inclineCommandMode, percent)
        Log.d("FitShow", "setIncline $percent% mode=$inclineCommandMode → ${cmd.toHex()}")
        return conn?.write(uuid, cmd, true) ?: false
    }

    private fun buildInclineCommand(mode: InclineCommandMode, percent: Double): ByteArray {
        val p = percent.coerceIn(-10.0, 20.0)
        return when (mode) {
            InclineCommandMode.A4_BYTE ->
                byteArrayOf(0xA4.toByte(), p.toInt().coerceIn(0, 255).toByte())

            InclineCommandMode.A4_BYTE_10X ->
                byteArrayOf(0xA4.toByte(), (p * 10).toInt().coerceIn(0, 255).toByte())

            InclineCommandMode.A4_01_BYTE ->
                byteArrayOf(0xA4.toByte(), 0x01, p.toInt().coerceIn(0, 255).toByte())

            InclineCommandMode.A4_BYTE_SIGN ->
                byteArrayOf(0xA4.toByte(), p.toInt().coerceIn(-128, 127).toByte())

            InclineCommandMode.B0_BYTE ->
                byteArrayOf(0xB0.toByte(), p.toInt().coerceIn(0, 255).toByte())

            InclineCommandMode.A5_BYTE ->
                byteArrayOf(0xA5.toByte(), p.toInt().coerceIn(0, 255).toByte())
        }
    }

    override fun onNotification(n: BleNotification) {
        if (n.characteristicUuid == notifyUuid) {
            Log.d("FitShow", "← notify ${n.value.toHex()}")
        }
    }
}
