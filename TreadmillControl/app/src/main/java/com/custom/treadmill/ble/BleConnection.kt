package com.custom.treadmill.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class BleConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, DISCOVERING, READY, FAILED
}

data class GattCharInfo(
    val serviceUuid: UUID,
    val uuid: UUID,
    val properties: Int
)

data class BleNotification(val characteristicUuid: UUID, val value: ByteArray)

@SuppressLint("MissingPermission")
class BleConnection(
    private val context: Context,
    private val tag: String
) {

    private var gatt: BluetoothGatt? = null

    private val _state = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val state: StateFlow<BleConnectionState> = _state.asStateFlow()

    private val _log = MutableSharedFlow<String>(extraBufferCapacity = 1024)
    val log: SharedFlow<String> = _log.asSharedFlow()

    private val _notifications = MutableSharedFlow<BleNotification>(extraBufferCapacity = 1024)
    val notifications: SharedFlow<BleNotification> = _notifications.asSharedFlow()

    private val _characteristics = MutableStateFlow<List<GattCharInfo>>(emptyList())
    val characteristics: StateFlow<List<GattCharInfo>> = _characteristics.asStateFlow()

    private fun log(msg: String) {
        Log.d(tag, msg)
        _log.tryEmit("[$tag] $msg")
    }

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            log("onConnectionStateChange status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _state.value = BleConnectionState.CONNECTED
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _state.value = BleConnectionState.DISCONNECTED
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            log("onServicesDiscovered status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = BleConnectionState.FAILED
                return
            }
            val list = mutableListOf<GattCharInfo>()
            for (service in g.services) {
                log("Service ${service.uuid}")
                for (c in service.characteristics) {
                    log("   Char ${c.uuid} props=${c.properties}")
                    list.add(GattCharInfo(service.uuid, c.uuid, c.properties))
                }
            }
            _characteristics.value = list
            _state.value = BleConnectionState.READY
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            log("<- ${c.uuid} ${value.toHex()}")
            _notifications.tryEmit(BleNotification(c.uuid, value))
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            val v = c.value ?: return
            log("<- ${c.uuid} ${v.toHex()}")
            _notifications.tryEmit(BleNotification(c.uuid, v))
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            status: Int
        ) {
            log("write ${c.uuid} status=$status")
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            log("descriptorWrite ${d.uuid} status=$status")
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            log("mtu=$mtu status=$status")
        }
    }

    fun connect(device: BluetoothDevice) {
        log("Подключение к ${device.address}…")
        _state.value = BleConnectionState.CONNECTING
        gatt = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(context, false, callback)
            }
        } catch (e: Exception) {
            log("Ошибка connectGatt: ${e.message}")
            null
        }
    }

    fun disconnect() {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
        _state.value = BleConnectionState.DISCONNECTED
    }

    fun isReady(): Boolean = _state.value == BleConnectionState.READY && gatt != null

    fun findCharacteristic(uuid: UUID): BluetoothGattCharacteristic? {
        val g = gatt ?: return null
        for (s in g.services) {
            for (c in s.characteristics) if (c.uuid == uuid) return c
        }
        return null
    }

    fun findCharacteristic(
        serviceUuid: UUID,
        requiredPropertyMask: Int
    ): BluetoothGattCharacteristic? {
        val g = gatt ?: return null
        val s = g.getService(serviceUuid) ?: return null
        return s.characteristics.firstOrNull { (it.properties and requiredPropertyMask) != 0 }
    }

    fun setNotify(uuid: UUID, enable: Boolean, indicate: Boolean = false): Boolean {
        val g = gatt ?: return false
        val c = findCharacteristic(uuid) ?: run {
            log("setNotify: характеристика $uuid не найдена")
            return false
        }
        if (!g.setCharacteristicNotification(c, enable)) {
            log("setCharacteristicNotification($uuid) = false")
        }
        val cccd = c.getDescriptor(Uuids.CCCD) ?: return enable
        val value = when {
            !enable -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            indicate -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            else -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(cccd, value) == 0
            } else {
                @Suppress("DEPRECATION")
                cccd.value = value
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        } catch (e: Exception) {
            log("Ошибка setNotify: ${e.message}")
            false
        }
    }

    fun write(uuid: UUID, data: ByteArray, withResponse: Boolean = true): Boolean {
        val c = findCharacteristic(uuid) ?: run {
            log("write: характеристика $uuid не найдена")
            return false
        }
        return write(c, data, withResponse)
    }

    fun write(c: BluetoothGattCharacteristic, data: ByteArray, withResponse: Boolean): Boolean {
        val g = gatt ?: return false
        log("-> ${c.uuid} ${data.toHex()}")
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                val wt = if (withResponse)
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                else
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                g.writeCharacteristic(c, data, wt) == 0
            } else {
                @Suppress("DEPRECATION")
                c.writeType = if (withResponse)
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                else
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                c.value = data
                @Suppress("DEPRECATION")
                g.writeCharacteristic(c)
            }
        } catch (e: Exception) {
            log("Ошибка write: ${e.message}")
            false
        }
    }

    fun requestMtu(mtu: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try { gatt?.requestMtu(mtu) } catch (_: Exception) {}
        }
    }
}
