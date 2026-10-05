package com.custom.treadmill.ui.viewmodels

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.custom.treadmill.TreadmillApp
import com.custom.treadmill.ble.*
import com.custom.treadmill.data.database.ProgramData
import com.custom.treadmill.data.database.WorkoutLogEntity
import com.custom.treadmill.data.repository.AppSettings
import com.custom.treadmill.data.repository.HrMode
import com.custom.treadmill.data.repository.ProtocolType
import com.custom.treadmill.logic.HeartRateController
import com.custom.treadmill.logic.WorkoutManager
import com.custom.treadmill.ui.components.HrPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

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

    // Целевые значения — то, что показываем на плитках (мгновенно)
    private val _targetSpeed = MutableStateFlow(0.0)
    val targetSpeed: StateFlow<Double> = _targetSpeed.asStateFlow()

    private val _targetIncline = MutableStateFlow(0.0)
    val targetIncline: StateFlow<Double> = _targetIncline.asStateFlow()

    private var lastSpeedCmdTime = 0L
    private var lastInclineCmdTime = 0L

    private val _heartRate = MutableStateFlow(0)
    val heartRate: StateFlow<Int> = _heartRate.asStateFlow()

    private val _hrHistory = MutableStateFlow<List<HrPoint>>(emptyList())
    val hrHistory: StateFlow<List<HrPoint>> = _hrHistory.asStateFlow()

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

    private var hrSum = 0L; private var hrCount = 0L; private var hrMax = 0
    private var speedSum = 0.0; private var speedCount = 0L

    init {
        viewModelScope.launch { ble.scanLog.collect { addLog(it) } }
        viewModelScope.launch { ble.scanResults.collect { _scanResults.value = it } }

        viewModelScope.launch {
            _heartRate.collect { hr ->
                if (hr > 0) {
                    hrSum += hr; hrCount++
                    if (hr > hrMax) hrMax = hr
                    val now = System.currentTimeMillis()
                    val list = _hrHistory.value
                    val last = list.lastOrNull()
                    if (last == null || now - last.timestamp >= 900L) {
                        val cutoff = now - 30 * 60 * 1000L
                        _hrHistory.value = (list + HrPoint(now, hr)).filter { it.timestamp >= cutoff }
                    }
                }
            }
        }

        viewModelScope.launch {
            _treadmillData.collect { d ->
                if (d.speedKmh > 0.05) { speedSum += d.speedKmh; speedCount++ }

                // Синхронизация target с реальными данными, если давно не командовали
                val now = System.currentTimeMillis()
                if (now - lastSpeedCmdTime > 3500 && abs(_targetSpeed.value - d.speedKmh) > 0.1) {
                    _targetSpeed.value = d.speedKmh
                }
                if (now - lastInclineCmdTime > 3500 && abs(_targetIncline.value - d.inclinePercent) > 0.4) {
                    _targetIncline.value = d.inclinePercent
                }
            }
        }
    }

    fun resetHrHistory() { _hrHistory.value = emptyList() }

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
        viewModelScope.launch { conn.notifications.collect { protocol?.onNotification(it) } }
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
            _statusMessage.value = if (ok) "Дорожка готова" else "Не найдены нужные характеристики"
            // Стартовая синхронизация
            _targetSpeed.value = _treadmillData.value.speedKmh
            _targetIncline.value = _treadmillData.value.inclinePercent
        }
    }

    fun disconnectTreadmill() {
        protocol = null
        treadmillConn?.disconnect()
        treadmillConn = null
        _treadmillState.value = BleConnectionState.DISCONNECTED
        _treadmillData.value = TreadmillData()
        _targetSpeed.value = 0.0
        _targetIncline.value = 0.0
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
                    _statusMessage.value = "Пульсометр отключён"
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
        val s = settingsStore.settings.value
        val safe = kmh.coerceIn(s.minSpeedKmh, s.maxSpeedKmh)
        _targetSpeed.value = safe
        lastSpeedCmdTime = System.currentTimeMillis()
        viewModelScope.launch { protocol?.setSpeed(safe) }
    }

    fun setSpeedManual(kmh: Double) = setSpeed(kmh)

    fun setIncline(percent: Double) {
        val s = settingsStore.settings.value
        val safe = percent.coerceIn(0.0, s.maxInclinePercent)
        _targetIncline.value = safe
        lastInclineCmdTime = System.currentTimeMillis()
        viewModelScope.launch { protocol?.setIncline(safe) }
    }

    fun startTreadmill() { viewModelScope.launch { protocol?.start() } }
    fun stopTreadmill() { viewModelScope.launch { protocol?.stop() } }

    fun emergencyStop() {
        viewModelScope.launch {
            // Сначала остановка (внутри уже шлётся скорость 0)
            protocol?.stop()
            // Потом сбрасываем наклон
            protocol?.setIncline(0.0)
            workoutManager.stop()
            setAutoHrEnabled(false)
            _statusMessage.value = "ЭКСТРЕННАЯ ОСТАНОВКА"
            _targetSpeed.value = 0.0
            _targetIncline.value = 0.0
        }
    }

    fun sendRawHex(hex: String): Boolean {
        val conn = treadmillConn ?: return false
        val uuid = protocol?.writeCharacteristicUuid ?: return false
        return try { conn.write(uuid, hex.hexToBytes(), true) } catch (e: Exception) {
            addLog("Ошибка отправки hex: ${e.message}"); false
        }
    }

    fun setAutoHrEnabled(enabled: Boolean) {
        settingsStore.update { it.copy(hrEnabled = enabled) }
        if (enabled) startHrAutoLoop() else stopHrAutoLoop()
        addLog(if (enabled) "Авторегулировка ВКЛ" else "Авторегулировка ВЫКЛ")
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
                val cur = _targetSpeed.value
                val newSpeed = HeartRateController.evaluate(cfg, hr, cur) ?: continue
                setSpeed(newSpeed)
                addLog("HR-авто: пульс=$hr, скорость %.1f → %.1f".format(cur, newSpeed))
            }
        }
    }

    private fun stopHrAutoLoop() { hrAutoJob?.cancel(); hrAutoJob = null }

    fun startWorkout(program: ProgramData) {
        resetStats(); resetHrHistory()
        workoutManager.start(
            scope = viewModelScope, program = program,
            onSetSpeed = { setSpeed(it) },
            onSetIncline = { setIncline(it) }
        )
    }

    fun stopWorkout() = workoutManager.stop()
    fun resetWorkout() = workoutManager.reset()

    private fun resetStats() {
        hrSum = 0L; hrCount = 0L; hrMax = 0; speedSum = 0.0; speedCount = 0L
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
            addLog("Тренировка сохранена")
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

    private fun String.toUuidSafe(): UUID? =
        try { UUID.fromString(this) } catch (_: Exception) { null }

    override fun onCleared() {
        super.onCleared()
        disconnectTreadmill(); disconnectHeartRate(); stopHrAutoLoop()
    }
}
