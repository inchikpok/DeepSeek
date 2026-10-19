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

    // [ФИКС 2] Храним джобы для отмены
    private var treadmillStateJob: Job? = null
    private var treadmillNotifyJob: Job? = null
    private var treadmillLogJob: Job? = null
    private var hrStateJob: Job? = null
    private var hrNotifyJob: Job? = null

    private val _treadmillState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val treadmillState: StateFlow<BleConnectionState> = _treadmillState.asStateFlow()

    private val _treadmillName = MutableStateFlow<String?>(null)
    val treadmillName: StateFlow<String?> = _treadmillName.asStateFlow()

    private val _hrName = MutableStateFlow<String?>(null)
    val hrName: StateFlow<String?> = _hrName.asStateFlow()

    private val _hrState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val hrState: StateFlow<BleConnectionState> = _hrState.asStateFlow()

    private val _treadmillData = MutableStateFlow(TreadmillData())
    val treadmillData: StateFlow<TreadmillData> = _treadmillData.asStateFlow()

    private val _targetSpeed = MutableStateFlow(0.0)
    val targetSpeed: StateFlow<Double> = _targetSpeed.asStateFlow()

    private val _targetIncline = MutableStateFlow(0.0)
    val targetIncline: StateFlow<Double> = _targetIncline.asStateFlow()

    private var lastSpeedCmdTime = 0L
    private var lastInclineCmdTime = 0L
    private var isStartingBelt = false

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

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()
    private var speedBeforePause: Double = 0.0

    private val _uiElapsedSec = MutableStateFlow(0)
    val uiElapsedSec: StateFlow<Int> = _uiElapsedSec.asStateFlow()

    private val _uiDistanceKm = MutableStateFlow(0.0)
    val uiDistanceKm: StateFlow<Double> = _uiDistanceKm.asStateFlow()

    private val _uiCalories = MutableStateFlow(0)
    val uiCalories: StateFlow<Int> = _uiCalories.asStateFlow()

    private var metricsJob: Job? = null

    private val workoutManager = WorkoutManager()
    val workoutState = workoutManager.state

    private var hrAutoJob: Job? = null
    private var settingsCollectorJob: Job? = null
    private var workoutLogSaved = false

    private var hrSum = 0L; private var hrCount = 0L; private var hrMax = 0
    private var speedSum = 0.0; private var speedCount = 0L

    fun isTreadmillReady(): Boolean = _treadmillState.value == BleConnectionState.READY

    // [ФИКС 21] Метод для вывода сообщений из UI
    fun showMessage(msg: String) {
        _statusMessage.value = msg
    }

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
                val now = System.currentTimeMillis()
                if (!isStartingBelt && now - lastSpeedCmdTime > 3500 && abs(_targetSpeed.value - d.speedKmh) > 0.1) {
                    _targetSpeed.value = d.speedKmh
                }
                if (now - lastInclineCmdTime > 3500 && abs(_targetIncline.value - d.inclinePercent) > 0.4) {
                    _targetIncline.value = d.inclinePercent
                }
            }
        }

        // [ФИКС 3] Восстанавливаем автопульс при запуске приложения
        if (settingsStore.settings.value.hrEnabled) {
            startHrAutoLoop()
        }

        startMetricsLoop()
    }

    private fun startMetricsLoop() {
        if (metricsJob?.isActive == true) return
        metricsJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                if (!isTreadmillReady()) continue
                val speed = _treadmillData.value.speedKmh
                if (speed < 0.3) continue

                _uiElapsedSec.value += 1
                _uiDistanceKm.value += speed / 3600.0
                _uiCalories.value = (_uiDistanceKm.value * 60).toInt()
            }
        }
    }

    private fun resetMetrics() {
        _uiElapsedSec.value = 0
        _uiDistanceKm.value = 0.0
        _uiCalories.value = 0
    }

    fun resetHrHistory() { _hrHistory.value = emptyList() }

    fun startScanTreadmill() { _scanMode.value = ScanMode.TREADMILL; ble.startScan() }
    fun startScanHeartRate() { _scanMode.value = ScanMode.HEART_RATE; ble.startScan() }
    fun stopScan() { ble.stopScan(); _scanMode.value = ScanMode.NONE }
    fun clearStatusMessage() { _statusMessage.value = null }

    fun connectTreadmill(device: BluetoothDevice) {
        disconnectTreadmill()
        _treadmillName.value = try { device.name } catch (_: SecurityException) { null } ?: "Дорожка"

        val conn = BleConnection(getApplication(), "TR")
        treadmillConn = conn
        
        treadmillLogJob = viewModelScope.launch { conn.log.collect { addLog(it) } }
        treadmillStateJob = viewModelScope.launch {
            conn.state.collect { state ->
                _treadmillState.value = state
                if (state == BleConnectionState.READY) onTreadmillReady(conn)
                
                // [ФИКС 10] Если связь потерялась, ставим тренировку на паузу
                if (state == BleConnectionState.DISCONNECTED && treadmillConn == conn) {
                    _statusMessage.value = "Дорожка отключена"
                    if (workoutManager.state.value.running && !workoutManager.state.value.paused) {
                        workoutManager.pause()
                    }
                    // [ФИКС 11] Не выключаем галочку hrEnabled в настройках! Просто стопаем цикл.
                    stopHrAutoLoop()
                }
            }
        }
        treadmillNotifyJob = viewModelScope.launch { conn.notifications.collect { protocol?.onNotification(it) } }
        conn.connect(device)
        stopScan()
    }

    private fun onTreadmillReady(conn: BleConnection) {
        viewModelScope.launch {
            val s = settingsStore.settings.value
            val p: ITreadmillProtocol = when (s.protocol) {
                ProtocolType.FTMS -> FTMSProtocol().apply {
                    sendStartCommand = s.sendStartCommand
                    onLog = { msg -> addLog(msg) }
                }
                ProtocolType.FITSHOW -> FitShowProprietaryProtocol(
                    manualWriteUuid = s.manualWriteUuid.takeIf { it.isNotBlank() }?.toUuidSafe(),
                    manualNotifyUuid = s.manualNotifyUuid.takeIf { it.isNotBlank() }?.toUuidSafe()
                )
            }
            protocol = p
            addLog("Протокол: ${p.protocolName}")

            settingsCollectorJob?.cancel()
            settingsCollectorJob = viewModelScope.launch {
                settingsStore.settings.collect { newS ->
                    (p as? FTMSProtocol)?.apply {
                        sendStartCommand = newS.sendStartCommand
                    }
                }
            }

            viewModelScope.launch { p.data.collect { _treadmillData.value = it } }
            val ok = p.initialize(conn)
            addLog(if (ok) "Протокол инициализирован" else "Протокол инициализирован с ошибками")
            _statusMessage.value = if (ok) "Дорожка готова" else "Не найдены нужные характеристики"
            _targetSpeed.value = _treadmillData.value.speedKmh
            _targetIncline.value = _treadmillData.value.inclinePercent

            // [ФИКС 3] Если был включен автопульс — возобновляем цикл
            if (settingsStore.settings.value.hrEnabled) {
                startHrAutoLoop()
            }
        }
    }

    fun disconnectTreadmill() {
        settingsCollectorJob?.cancel()
        settingsCollectorJob = null
        
        // [ФИКС 2, 10] Отменяем старые джобы
        treadmillStateJob?.cancel()
        treadmillNotifyJob?.cancel()
        treadmillLogJob?.cancel()
        
        val conn = treadmillConn
        treadmillConn = null
        
        conn?.disconnect()
        protocol = null
        
        _treadmillState.value = BleConnectionState.DISCONNECTED
        _treadmillData.value = TreadmillData()
        _targetSpeed.value = 0.0
        _targetIncline.value = 0.0
        _treadmillName.value = null
        _isPaused.value = false
        speedBeforePause = 0.0
    }

    fun connectHeartRate(device: BluetoothDevice) {
        disconnectHeartRate()
        _hrName.value = try { device.name } catch (_: SecurityException) { null } ?: "Пульсометр"

        val conn = BleConnection(getApplication(), "HR")
        hrConn = conn
        hrService.reset()
        
        val logJob = viewModelScope.launch { conn.log.collect { addLog(it) } }
        hrStateJob = viewModelScope.launch {
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
        hrNotifyJob = viewModelScope.launch {
            conn.notifications.collect {
                hrService.onNotification(it)
                _heartRate.value = hrService.heartRate.value
            }
        }
        conn.connect(device)
        stopScan()
    }

    fun disconnectHeartRate() {
        hrStateJob?.cancel()
        hrNotifyJob?.cancel()
        hrConn?.disconnect()
        hrConn = null
        _hrState.value = BleConnectionState.DISCONNECTED
        _heartRate.value = 0
        _hrName.value = null
    }

    fun setSpeed(kmh: Double) {
        val s = settingsStore.settings.value
        val safe = kmh.coerceIn(s.minSpeedKmh, s.maxSpeedKmh)
        _targetSpeed.value = safe
        lastSpeedCmdTime = System.currentTimeMillis()
        viewModelScope.launch { protocol?.setSpeed(safe) }
    }

    fun setIncline(percent: Double) {
        val s = settingsStore.settings.value
        val safe = percent.coerceIn(0.0, s.maxInclinePercent)
        _targetIncline.value = safe
        lastInclineCmdTime = System.currentTimeMillis()
        viewModelScope.launch { protocol?.setIncline(safe) }
    }

    private suspend fun waitForBeltRunning(minSpeedKmh: Double = 0.9, timeoutMs: Long = 30000L): Boolean {
        val start = System.currentTimeMillis()
        var lastPingAt = 0L

        while (System.currentTimeMillis() - start < timeoutMs) {
            if (_treadmillData.value.speedKmh >= minSpeedKmh) return true
            val now = System.currentTimeMillis()
            if (now - lastPingAt >= 4000L) {
                (protocol as? FTMSProtocol)?.ping07()
                lastPingAt = now
                addLog("wait: belt ещё не поехал, повтор 07")
            }
            delay(200L)
        }
        return false
    }

    fun startTreadmill() {
        if (!isTreadmillReady()) {
            _statusMessage.value = "Сначала подключите дорожку"
            return
        }
        val isResume = _isPaused.value
        if (!isResume) resetMetrics()
        val resumeSpeed = if (isResume) speedBeforePause else 0.0

        if (isResume && resumeSpeed > 0.5) {
            _targetSpeed.value = resumeSpeed
        }

        _isPaused.value = false
        isStartingBelt = true

        viewModelScope.launch {
            try {
                protocol?.start()
                if (!waitForBeltRunning(0.9, 30000L)) {
                    _statusMessage.value = "Дорожка не отвечает. Нажмите любую кнопку и попробуйте снова."
                    return@launch
                }

                // [ФИКС 6] Не строгое неравенство, чтобы восстанавливать 1.0 км/ч
                if (resumeSpeed >= 1.0) {
                    protocol?.setSpeed(resumeSpeed)
                } else {
                    val targetSpeedLocal = _targetSpeed.value
                    if (targetSpeedLocal >= 1.0) {
                        protocol?.setSpeed(targetSpeedLocal)
                    }
                }
                val targetInclineLocal = _targetIncline.value
                if (targetInclineLocal > 0.0) {
                    protocol?.setIncline(targetInclineLocal)
                }
            } finally {
                isStartingBelt = false
            }
        }
    }

    fun pauseTreadmill() {
        if (!isTreadmillReady()) return
        if (_isPaused.value) return
        speedBeforePause = _targetSpeed.value.coerceAtLeast(1.0)
        _isPaused.value = true
        viewModelScope.launch { protocol?.stop() }
        _statusMessage.value = "Пауза (%.1f км/ч сохранено)".format(speedBeforePause)
    }

    fun togglePauseTreadmill() {
        if (_isPaused.value) startTreadmill() else pauseTreadmill()
    }

    fun stopTreadmill() {
        if (!isTreadmillReady()) return

        val ws = workoutManager.state.value
        if (ws.running || ws.paused) {
            stopWorkout()
            return
        }

        _isPaused.value = false
        speedBeforePause = 0.0
        viewModelScope.launch { protocol?.stop() }
        
        // [ФИКС 4] Сохраняем ручную тренировку в журнал
        saveWorkoutLog()
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
                if (!isTreadmillReady()) continue
                if (_targetSpeed.value <= 0.5) continue
                val hr = _heartRate.value
                if (hr <= 0) continue
                val cur = _targetSpeed.value
                val newSpeed = HeartRateController.evaluate(cfg, hr, cur) ?: continue
                setSpeed(newSpeed)
                addLog("HR-авто: пульс=$hr, скорость %.1f -> %.1f".format(cur, newSpeed))
            }
        }
    }

    private fun stopHrAutoLoop() { hrAutoJob?.cancel(); hrAutoJob = null }

    fun startWorkout(program: ProgramData) {
        if (!isTreadmillReady()) {
            _statusMessage.value = "Сначала подключите дорожку"
            return
        }
        resetStats()
        resetHrHistory()
        resetMetrics()
        workoutLogSaved = false
        _isPaused.value = false
        speedBeforePause = 0.0

        isStartingBelt = true
        viewModelScope.launch {
            try {
                protocol?.start()
                if (!waitForBeltRunning(0.9, 30000L)) {
                    _statusMessage.value = "Дорожка не отвечает. Нажмите любую кнопку и запустите снова."
                    return@launch
                }
                workoutManager.start(
                    scope = viewModelScope, program = program,
                    onSetSpeed = { setSpeed(it) },
                    onSetIncline = { setIncline(it) },
                    onFinish = {
                        protocol?.stop()
                        _isPaused.value = false
                        speedBeforePause = 0.0
                    }
                )
            } finally {
                isStartingBelt = false
            }
        }
    }

    fun pauseWorkout() {
        if (!workoutManager.state.value.running) return
        workoutManager.pause()
        if (isTreadmillReady()) {
            viewModelScope.launch { protocol?.stop() }
        }
        _statusMessage.value = "Пауза"
    }

    fun resumeWorkout() {
        if (!workoutManager.state.value.paused) return
        if (!isTreadmillReady()) {
            _statusMessage.value = "Дорожка не подключена — не могу продолжить"
            return
        }
        isStartingBelt = true
        viewModelScope.launch {
            try {
                protocol?.start()
                if (!waitForBeltRunning(0.9, 30000L)) {
                    _statusMessage.value = "Дорожка не отвечает. Нажмите кнопку на дорожке и продолжите снова."
                    return@launch
                }
                workoutManager.resume()
            } finally {
                isStartingBelt = false
            }
        }
    }

    fun togglePauseWorkout() {
        val s = workoutManager.state.value
        when {
            s.paused -> resumeWorkout()
            s.running -> pauseWorkout()
        }
    }

    fun skipWorkoutSegment() {
        if (!workoutManager.state.value.running) return
        workoutManager.skipCurrentSegment()
    }

    fun stopWorkout() {
        saveWorkoutLog()
        workoutManager.stop()
        if (isTreadmillReady()) {
            viewModelScope.launch { protocol?.stop() }
        }
    }

    private fun resetStats() {
        hrSum = 0L; hrCount = 0L; hrMax = 0; speedSum = 0.0; speedCount = 0L
    }

    fun saveWorkoutLog() {
        if (workoutLogSaved) return
        val ws = workoutManager.state.value
        val durationSec = if (ws.totalElapsedSec > 0) ws.totalElapsedSec else _uiElapsedSec.value
        if (durationSec <= 0) return
        workoutLogSaved = true

        val avgHr = if (hrCount > 0) (hrSum / hrCount).toInt() else 0
        val maxHrCopy = hrMax
        val avgSpd = if (speedCount > 0) speedSum / speedCount else 0.0
        val programName = ws.programName.ifBlank { "Ручная тренировка" }

        viewModelScope.launch {
            repository.addLog(
                WorkoutLogEntity(
                    dateMillis = System.currentTimeMillis(),
                    programName = programName,
                    durationSec = durationSec,
                    distanceKm = _uiDistanceKm.value,
                    calories = _uiCalories.value,
                    avgHeartRate = avgHr,
                    maxHeartRate = maxHrCopy,
                    avgSpeedKmh = avgSpd
                )
            )
            addLog("Тренировка сохранена в журнал")
        }
        resetStats()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsStore.update(transform)
    fun setProtocolType(type: ProtocolType) { settingsStore.update { it.copy(protocol = type) } }
    fun setHrMode(mode: HrMode) { settingsStore.update { it.copy(hrMode = mode) } }

    private fun addLog(line: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _logs.value = (_logs.value + "[$ts] $line").takeLast(500)
    }

    fun clearLogs() { _logs.value = emptyList() }

    private fun String.toUuidSafe(): UUID? = try { UUID.fromString(this) } catch (_: Exception) { null }

    override fun onCleared() {
        super.onCleared()
        disconnectTreadmill()
        disconnectHeartRate()
        stopHrAutoLoop()
    }
}
