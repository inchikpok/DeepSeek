package com.custom.treadmill.ui.viewmodels

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.custom.treadmill.TreadmillApp
import com.custom.treadmill.ble.BleConnection
import com.custom.treadmill.ble.BleConnectionState
import com.custom.treadmill.ble.FTMSProtocol
import com.custom.treadmill.ble.FitShowProprietaryProtocol
import com.custom.treadmill.ble.HeartRateService
import com.custom.treadmill.ble.ITreadmillProtocol
import com.custom.treadmill.ble.TreadmillData
import com.custom.treadmill.ble.hexToBytes
import com.custom.treadmill.data.database.ProgramData
import com.custom.treadmill.data.database.WorkoutLogEntity
import com.custom.treadmill.data.repository.AppSettings
import com.custom.treadmill.data.repository.HrMode
import com.custom.treadmill.data.repository.ProtocolType
import com.custom.treadmill.logic.HeartRateController
import com.custom.treadmill.logic.WorkoutManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class ScanMode { NONE, TREADMILL, HEART_RATE }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ble = TreadmillApp.instance.bleManager
    private val settingsStore = TreadmillApp.instance.settingsStore
    private val repository = TreadmillApp.instance.programRepository

    val settings: StateFlow<AppSettings> = settingsStore.settings

    private var treadmillConn: BleConnection? = null
    private var hrConn: BleConnection? = null
    private var protocol: ITreadmillProtocol? = null
    private val hrService = HeartRateService()

    private val _treadmillState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val treadmillState: StateFlow<BleConnectionState> = _treadmillState.asStateFlow()

    private val _hrState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val hrState: StateFlow<BleConnectionState> = _hrState.asStateFlow()

    private val _treadmillData = MutableStateFlow(TreadmillData())
    val treadmillData: StateFlow<TreadmillData> = _treadmillData.asStateFlow()

    private val _heartRate = MutableStateFlow(0)
    val heartRate: StateFlow<Int> = _heartRate.asStateFlow()

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()

    private val _scanMode = MutableStateFlow(ScanMode.NONE)
    val scanMode: StateFlow<ScanMode> = _scanMode.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val workoutManager = WorkoutManager()
    val workoutState = workoutManager.state

    private var hrAutoJob: Job? = null

    private var hrSum = 0L
    private var hrCount = 0L
    private var hrMax = 0
    private var speedSum = 0.0
    private var speedCount = 0L

    init {
        viewModelScope.launch { ble.scanLog.collect { addLog(it) } }
        viewModelScope.launch { ble.scanResults.collect { _scanResults.value = it } }
        viewModelScope.launch {
            _heartRate.collect { hr ->
                if (hr > 0) {
                    hrSum += hr; hrCount++
                    if (hr > hrMax) hrMax = hr
                }
            }
        }
        viewModelScope.launch {
            _treadmillData.collect { d ->
                if (d.speedKmh > 0.05) { speedSum += d.speedKmh; speedCount++ }
            }
        }
    }

    fun startScanTreadmill() { _scanMode.value = ScanMode.TREADMILL; ble.startScan() }
    fun startScanHeartRate() { _scanMode.value = ScanMode.HEART_RATE; ble.startScan() }
    fun stopScan() { ble.stopScan(); _scanMode.value = ScanMode.NONE }
    fun clearStatusMessage() { _statusMessage.value = null }

    fun connectTreadmill(device: BluetoothDevice) {
        disconnectTreadmill()
        val conn = BleConnection(getApplication(), "TR")
        treadmillConn = conn
        viewModelScope.launch { conn.log.collect { addLog(it) } }
        viewModelScope.launch {
            conn.state.collect { state ->
                _treadmillState.value = state
                if (state == BleConnectionState.READY) onTreadmillReady(conn)
                if (state == BleConnectionState.DISCONNECTED && protocol != null) {
                    _statusMessage.value = "Дорожка отключена"
                    workoutManager.stop()
                    setAutoHrEnabled(false)
                }
            }
        }
        viewModelScope.launch {
            conn.notifications.collect { protocol?.onNotification(it) }
        }
        conn.connect(device)
        stopScan()
    }

    private fun onTreadmillReady(conn: BleConnection) {
        viewModelScope.launch {
            val s = settingsStore.settings.value
            val p: ITreadmillProtocol = when (s.protocol) {
                ProtocolType.FTMS -> FTMSProtocol()
                ProtocolType.FITSHOW -> FitShowProprietaryProtocol(
                    manualWriteUuid = s.manualWriteUuid.takeIf { it.isNotBlank() }?.toUuidSafe(),
                    manualNotifyUuid = s.manualNotifyUuid.takeIf { it.isNotBlank() }?.toUuidSafe()
                )
            }
            protocol = p
            addLog("Протокол: ${p.protocolName}")
            viewModelScope.launch { p.data.collect { _treadmillData.value = it } }
            val ok = p.initialize(conn)
            addLog(if (ok) "Протокол инициализирован" else "Протокол инициализирован с ошибками")
            _statusMessage.value =
                if (ok) "Дорожка готова" else "Не найдены нужные характеристики"
        }
    }

    fun disconnectTreadmill() {
        protocol = null
        treadmillConn?.disconnect()
        treadmillConn = null
        _treadmillState.value = BleConnectionState.DISCONNECTED
        _treadmillData.value = TreadmillData()
    }

    fun connectHeartRate(device: BluetoothDevice) {
        disconnectHeartRate()
        val conn = BleConnection(getApplication(), "HR")
        hrConn = conn
        hrService.reset()
        viewModelScope.launch { conn.log.collect { addLog(it) } }
        viewModelScope.launch {
            conn.state.collect { state ->
                _hrState.value = state
                if (state == BleConnectionState.READY) {
                    val ok = hrService.initialize(conn)
                    addLog(if (ok) "HR-сервис инициализирован" else "HR Measurement не найдена")
                }
                if (state == BleConnectionState.DISCONNECTED) {
                    _heartRate.value = 0
                    _statusMessage.value = "Пульсометр отключён — автокоррекция приостановлена"
                }
            }
        }
        viewModelScope.launch {
            conn.notifications.collect {
                hrService.onNotification(it)
                _heartRate.value = hrService.heartRate.value
            }
        }
        conn.connect(device)
        stopScan()
    }

    fun disconnectHeartRate() {
        hrConn?.disconnect()
        hrConn = null
        _hrState.value = BleConnectionState.DISCONNECTED
        _heartRate.value = 0
    }

    fun setSpeed(kmh: Double) {
        viewModelScope.launch {
            val s = settingsStore.settings.value
            val safe = kmh.coerceIn(s.minSpeedKmh, s.maxSpeedKmh)
            protocol?.setSpeed(safe)
        }
    }

    fun setSpeedManual(kmh: Double) = setSpeed(kmh)

    fun startTreadmill() { viewModelScope.launch { protocol?.start() } }
    fun stopTreadmill() { viewModelScope.launch { protocol?.stop() } }

    fun emergencyStop() {
        viewModelScope.launch {
            protocol?.setSpeed(0.0)
            delay(200)
            protocol?.stop()
            workoutManager.stop()
            setAutoHrEnabled(false)
            _statusMessage.value = "ЭКСТРЕННАЯ ОСТАНОВКА"
        }
    }

    fun sendRawHex(hex: String): Boolean {
        val conn = treadmillConn ?: return false
        val uuid = protocol?.writeCharacteristicUuid ?: return false
        return try {
            conn.write(uuid, hex.hexToBytes(), true)
        } catch (e: Exception) {
            addLog("Ошибка отправки hex: ${e.message}")
            false
        }
    }

    fun setAutoHrEnabled(enabled: Boolean) {
        settingsStore.update { it.copy(hrEnabled = enabled) }
        if (enabled) startHrAutoLoop() else stopHrAutoLoop()
        addLog(if (enabled) "Авторегулировка по пульсу ВКЛ" else "Авторегулировка ВЫКЛ")
    }

    private fun startHrAutoLoop() {
        stopHrAutoLoop()
        hrAutoJob = viewModelScope.launch {
            while (isActive) {
                val cfg = settingsStore.settings.value
                delay(cfg.intervalSec.coerceAtLeast(5) * 1000L)
                if (!cfg.hrEnabled) continue
                val hr = _heartRate.value
                if (hr <= 0) continue
                val cur = _treadmillData.value.speedKmh
                val newSpeed = HeartRateController.evaluate(cfg, hr, cur) ?: continue
                setSpeed(newSpeed)
                addLog("HR-автокоррекция: пульс=$hr, скорость %.1f -> %.1f".format(cur, newSpeed))
            }
        }
    }

    private fun stopHrAutoLoop() { hrAutoJob?.cancel(); hrAutoJob = null }

    fun startWorkout(program: ProgramData) {
        resetStats()
        workoutManager.start(viewModelScope, program) { speed -> setSpeed(speed) }
    }

    fun stopWorkout() { workoutManager.stop() }
    fun resetWorkout() { workoutManager.reset() }

    private fun resetStats() {
        hrSum = 0L; hrCount = 0L; hrMax = 0
        speedSum = 0.0; speedCount = 0L
    }

    fun saveWorkoutLog() {
        val ws = workoutManager.state.value
        if (ws.totalElapsedSec <= 0) return
        viewModelScope.launch {
            repository.addLog(
                WorkoutLogEntity(
                    dateMillis = System.currentTimeMillis(),
                    programName = ws.programName.ifBlank { "Ручная тренировка" },
                    durationSec = ws.totalElapsedSec,
                    distanceKm = _treadmillData.value.distanceKm,
                    calories = _treadmillData.value.calories,
                    avgHeartRate = if (hrCount > 0) (hrSum / hrCount).toInt() else 0,
                    maxHeartRate = hrMax,
                    avgSpeedKmh = if (speedCount > 0) speedSum / speedCount else 0.0
                )
            )
            addLog("Тренировка сохранена в журнал")
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsStore.update(transform)
    fun setProtocolType(type: ProtocolType) { settingsStore.update { it.copy(protocol = type) } }
    fun setHrMode(mode: HrMode) { settingsStore.update { it.copy(hrMode = mode) } }

    private fun addLog(line: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        _logs.value = (_logs.value + "[$ts] $line").takeLast(500)
    }

    fun clearLogs() { _logs.value = emptyList() }

    private fun String.toUuidSafe(): UUID? = try {
        UUID.fromString(this)
    } catch (_: Exception) { null }

    override fun onCleared() {
        super.onCleared()
        disconnectTreadmill()
        disconnectHeartRate()
        stopHrAutoLoop()
    }
}
