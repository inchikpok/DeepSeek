package com.custom.treadmill.ble

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HeartRateService {

    private val _heartRate = MutableStateFlow(0)
    val heartRate: StateFlow<Int> = _heartRate.asStateFlow()

    private var conn: BleConnection? = null

    fun initialize(conn: BleConnection): Boolean {
        this.conn = conn
        val char = conn.findCharacteristic(Uuids.HR_MEASUREMENT) ?: return false
        return conn.setNotify(char.uuid, true, indicate = false)
    }

    fun onNotification(n: BleNotification) {
        if (n.characteristicUuid == Uuids.HR_MEASUREMENT) parse(n.value)
    }

    fun reset() { _heartRate.value = 0 }

    private fun parse(b: ByteArray) {
        if (b.isEmpty()) return
        try {
            val flags = b[0].toInt() and 0xFF
            val hr = if (flags and 0x01 != 0) {
                if (b.size < 3) return
                (b[1].toInt() and 0xFF) or ((b[2].toInt() and 0xFF) shl 8)
            } else {
                if (b.size < 2) return
                b[1].toInt() and 0xFF
            }
            _heartRate.value = hr
            Log.d("HR", "пульс = $hr")
        } catch (e: Exception) {
            Log.w("HR", "Ошибка разбора HR: ${e.message}")
        }
    }
}
