package com.custom.treadmill.ble

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class FTMSProtocol : ITreadmillProtocol {

    override val protocolName: String = "FTMS (стандарт)"

    private val _data = MutableStateFlow(TreadmillData())
    override val data: StateFlow<TreadmillData> = _data.asStateFlow()

    private var conn: BleConnection? = null
    private var controlPointUuid: UUID? = null
    override val writeCharacteristicUuid: UUID? get() = controlPointUuid

    /** Мьютекс — защищает от одновременных записей в Control Point. */
    private val writeMutex = Mutex()

    /** Пауза между командами в миллисекундах (эта дорожка требует ≥ 1000). */
    var commandGapMs: Long = 1500L

    /** Если true — округляем наклон до целых % (эта дорожка требует). */
    var roundInclineToWhole: Boolean = true

    // Последние отправленные значения — чтобы не дублировать команды
    private var lastSentSpeed: Double = -1.0
    private var lastSentIncline: Double = -1.0

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
        if (!controlFound) Log.w("FTMS", "Control Point не найден")

        // Feature Bitmap (для информации)
        val featureChar = conn.findCharacteristic(Uuids.FTMS_FEATURE)
        if (featureChar != null) {
            val bytes = conn.read(featureChar.uuid)
            if (bytes != null && bytes.size >= 4) {
                Log.d("FTMS", "Feature: ${bytes.toHex()}")
            }
        }

        if (conn.findCharacteristic(Uuids.FTMS_TREADMILL_DATA) != null) {
            conn.setNotify(Uuids.FTMS_TREADMILL_DATA, true, indicate = false)
        }
        // Пробуем подписаться на Control Point — некоторые дорожки отвечают indicate
        controlPointUuid?.let { conn.setNotify(it, true, indicate = true) }
        if (conn.findCharacteristic(Uuids.FTMS_STATUS) != null) {
            conn.setNotify(Uuids.FTMS_STATUS, true, indicate = false)
        }

        delay(300)

        // Запрашиваем контроль один раз при подключении
        requestControl()

        return controlFound
    }

    override suspend fun requestControl(): Boolean {
        val uuid = controlPointUuid ?: return false
        return safeWrite(uuid, byteArrayOf(0x00), withResponse = true, force = true)
    }

    override suspend fun start(): Boolean {
        val uuid = controlPointUuid ?: return false
        requestControl()
        return safeWrite(uuid, byteArrayOf(0x07), withResponse = true, force = true)
    }

    override suspend fun stop(): Boolean {
        val uuid = controlPointUuid ?: return false
        requestControl()
        return safeWrite(uuid, byteArrayOf(0x08, 0x01), withResponse = true, force = true)
    }

    override suspend fun setSpeed(speedKmh: Double): Boolean {
        val uuid = controlPointUuid ?: return false
        val safe = speedKmh.coerceAtLeast(0.0)

        // Не отправляем дубликат
        if (kotlin.math.abs(safe - lastSentSpeed) < 0.01) {
            Log.d("FTMS", "setSpeed: значение не изменилось ($safe), пропуск")
            return true
        }

        val raw = (safe * 100.0).toInt().coerceIn(0, 65535)
        val cmd = byteArrayOf(
            0x02,
            (raw and 0xFF).toByte(),
            ((raw shr 8) and 0xFF).toByte()
        )
        requestControl()
        val ok = safeWrite(uuid, cmd, withResponse = true, force = true)
        if (ok) lastSentSpeed = safe
        return ok
    }

    override suspend fun setIncline(percent: Double): Boolean {
        val uuid = controlPointUuid ?: return false

        // Дорожка принимает только целые % — округляем
        val target = if (roundInclineToWhole) {
            percent.coerceIn(0.0, 30.0).let { Math.round(it).toDouble() }
        } else {
            percent.coerceIn(0.0, 30.0)
        }

        if (kotlin.math.abs(target - lastSentIncline) < 0.01) {
            Log.d("FTMS", "setIncline: значение не изменилось ($target), пропуск")
            return true
        }

        val raw = (target * 10.0).toInt().coerceIn(0, 32767)
        val cmd = byteArrayOf(
            0x03,
            (raw and 0xFF).toByte(),
            ((raw shr 8) and 0xFF).toByte()
        )
        requestControl()
        val ok = safeWrite(uuid, cmd, withResponse = true, force = true)
        if (ok) lastSentIncline = target
        return ok
    }

    /**
     * Безопасная запись: сериализация через мьютекс + пауза между командами.
     * force=true — пауза всё равно соблюдается.
     */
    private suspend fun safeWrite(
        uuid: UUID,
        data: ByteArray,
        withResponse: Boolean,
        force: Boolean
    ): Boolean {
        return writeMutex.withLock {
            val c = conn ?: return@withLock false
            // Пауза перед командой (кроме самой первой)
            if (lastWriteTime > 0) {
                delay(commandGapMs)
            }
            val ok = c.write(uuid, data, withResponse)
            lastWriteTime = System.currentTimeMillis()
            ok
        }
    }

    private var lastWriteTime: Long = 0

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
            val resultText = when (result) {
                0x01 -> "SUCCESS"
                0x02 -> "OP CODE NOT SUPPORTED"
                0x03 -> "INVALID PARAMETER"
                0x04 -> "OPERATION FAILED"
                0x05 -> "CONTROL NOT PERMITTED"
                else -> "unknown ($result)"
            }
            Log.d("FTMS", "← CP response: op=0x%02X result=%s".format(reqOp, resultText))
        }
    }

    private fun parseTreadmillData(b: ByteArray) {
        if (b.size < 4) return
        try {
            var o = 0
            val flags = u16(b, 0); o = 2

            var speed = _data.value.speedKmh
            // ВАЖНО: инициализируем наклон нулём — если бит 3 не установлен,
            // значит дорожка сейчас не сообщает наклон
            var incline = 0.0
            var distance = _data.value.distanceKm
            var calories = _data.value.calories
            var elapsed = _data.value.elapsedSec
            var hr = _data.value.heartRate

            if (o + 2 <= b.size) { speed = u16(b, o) / 100.0; o += 2 }
            if (flags and (1 shl 1) != 0) o += 2          // Average Speed
            if (flags and (1 shl 2) != 0) {               // Total Distance (uint24)
                if (o + 3 <= b.size) distance = u24(b, o) / 1000.0
                o += 3
            }
            if (flags and (1 shl 3) != 0) {               // Inclination + Ramp Angle
                if (o + 4 <= b.size) {
                    incline = s16(b, o) / 10.0
                }
                o += 4
            }
            if (flags and (1 shl 4) != 0) o += 4          // Elevation Gain
            if (flags and (1 shl 5) != 0) o += 1          // Instantaneous Pace
            if (flags and (1 shl 6) != 0) o += 1          // Average Pace
            if (flags and (1 shl 7) != 0) {               // Expended Energy
                if (o + 2 <= b.size) calories = u16(b, o)
                o += 5
            }
            if (flags and (1 shl 8) != 0) {               // Heart Rate
                if (o + 1 <= b.size) hr = b[o].toInt() and 0xFF
                o += 1
            }
            if (flags and (1 shl 9) != 0) o += 1          // MET
            if (flags and (1 shl 10) != 0) {              // Elapsed Time
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
