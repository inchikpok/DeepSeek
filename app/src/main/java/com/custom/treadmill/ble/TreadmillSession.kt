package com.custom.treadmill.ble

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.custom.treadmill.TreadmillApp
import com.custom.treadmill.data.database.ProgramData
import com.custom.treadmill.data.database.WorkoutLogEntity
import com.custom.treadmill.logic.HeartRateController
import com.custom.treadmill.logic.WorkoutManager
import com.custom.treadmill.service.TreadmillService
import com.custom.treadmill.ui.components.HrPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class TreadmillSession(private val appContext: Context) {

    private val ble = (appContext as TreadmillApp).bleManager
    private val settingsStore = (appContext as TreadmillApp).settingsStore
    private val repository = (appContext as TreadmillApp).programRepository

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var treadmillConn: BleConnection? = null
    private var hrConn: BleConnection? = null
    private var protocol: FTMSProtocol? = null
    private val hrService = HeartRateService()

    private var treadmillStateJob: Job? = null
    private var treadmillNotifyJob: Job? = null
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

    private val _heartRate = MutableStateFlow(0)
    val heartRate: StateFlow<Int> = _heartRate.asStateFlow()

    private val _hrHistory = MutableStateFlow<List<HrPoint>>(emptyList())
    val hrHistory: StateFlow<List<HrPoint>> = _hrHistory.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _uiElapsedSec = MutableStateFlow(0)
    val uiElapsedSec: StateFlow<Int> = _uiElapsedSec.asStateFlow()

    private val _uiDistanceKm = MutableStateFlow(0.0)
    val uiDistanceKm: StateFlow<Double> = _uiDistanceKm.asStateFlow()

    private val _uiCalories = MutableStateFlow(0)
    val uiCalories: StateFlow<Int> = _uiCalories.asStateFlow()

    private var lastSpeedCmdTime = 0L
    private var lastInclineCmdTime = 0L
    private var isStartingBelt = false
    private var speedBeforePause: Double = 0.0

    private var totalCaloriesDouble = 0.0

    private val workoutManager = WorkoutManager()
    val workoutState = workoutManager.state

    private var lastConnectedMac: String? = null
    private var isAutoReconnecting = false
    private var isManualDisconnect = false

    private var hrSum = 0L; private var hrCount = 0L; private var hrMax = 0
    private var speedSum = 0.0; private var speedCount = 0L
    private var workoutLogSaved = false

    init {
        scope.launch {
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
        scope.launch {
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
        startMetricsLoop()
    }

    private fun startMetricsLoop() {
        scope.launch {
            while (isActive) {
                delay(1000L)
                if (_treadmillState.value != BleConnectionState.READY) continue
                val d = _treadmillData.value
                if (d.speedKmh < 0.3) continue

                val weight = settingsStore.settings.value.weightKg
                val met = if (d.speedKmh < 6.0) 1.0 + (d.speedKmh * 0.5) + (d.inclinePercent * 0.1)
                else 1.0 + (d.speedKmh * 0.9) + (d.inclinePercent * 0.15)
                val kcalPerMin = (met * 3.5 * weight) / 200.0
                
                _uiElapsedSec.value += 1
                _uiDistanceKm.value += d.speedKmh / 3600.0
                totalCaloriesDouble += kcalPerMin / 60.0
                _uiCalories.value = totalCaloriesDouble.toInt()
            }
        }
    }

    fun connectTreadmill(device: BluetoothDevice) {
        lastConnectedMac = device.address
        isManualDisconnect = false
        disconnectTreadmillInternal()
        
        _treadmillName.value = try { device.name } catch (_: SecurityException) { null } ?: "Дорожка"
        
        // Запускаем Foreground Service
        val intent = Intent(appContext, TreadmillService::class.java)
        ContextCompat.startForegroundService(appContext, intent)

        val conn = BleConnection(appContext, "TR")
        treadmillConn = conn
        
        treadmillStateJob = scope.launch {
            conn.state.collect { state ->
                _treadmillState.value = state
                if (state == BleConnectionState.READY) onTreadmillReady(conn)
                if (state == BleConnectionState.DISCONNECTED) {
                    if (!isManualDisconnect && lastConnectedMac != null) {
                        triggerAutoReconnect()
                    } else {
                        stopService()
                    }
                }
            }
        }
        treadmillNotifyJob = scope.launch { conn.notifications.collect { protocol?.onNotification(it) } }
        conn.connect(device)
    }

    private fun triggerAutoReconnect() {
        if (isAutoReconnecting) return
        val mac = lastConnectedMac ?: return
        val adapter = ble.adapter ?: return
        isAutoReconnecting = true
        _statusMessage.value = "Связь потеряна. Переподключение..."

        scope.launch {
            for (i in 1..10) {
                if (_treadmillState.value == BleConnectionState.READY) break
                val device = try { adapter.getRemoteDevice(mac) } catch (_: Exception) { null }
                if (device != null) {
                    _treadmillState.value = BleConnectionState.CONNECTING
                    val conn = BleConnection( appContext, "TR")
                    treadmillConn = conn
                    treadmillStateJob = scope.launch {
                        conn.state.collect { state ->
                            _treadmillState.value = state
                            if (state == BleConnectionState.READY) onTreadmillReady(conn)
                        }
                    }
                    treadmillNotifyJob = scope.launch { conn.notifications.collect { protocol?.onNotification(it) } }
                    conn.connect(device)
                }
                delay(3000)
            }
            isAutoReconnecting = false
            if (_treadmillState.value != BleConnectionState.READY) {
                _statusMessage.value = "Не удалось переподключиться. Нажмите кнопку на дорожке."
                stopService()
            } else {
                _statusMessage.value = "Связь восстановлена!"
            }
        }
    }

    private fun onTreadmillReady(conn: BleConnection) {
        scope.launch {
            val p = FTMSProtocol().apply { sendStartCommand = true }
            protocol = p
            scope.launch { p.data.collect { _treadmillData.value = it } }
            p.initialize(conn)
            _statusMessage.value = "Дорожка готова"
            _targetSpeed.value = _treadmillData.value.speedKmh
            _targetIncline.value = _treadmillData.value.inclinePercent
        }
    }

    fun disconnectTreadmill() {
        isManualDisconnect = true
        lastConnectedMac = null
        disconnectTreadmillInternal()
        stopService()
    }

    private fun disconnectTreadmillInternal() {
        treadmillStateJob?.cancel()
        treadmillNotifyJob?.cancel()
        treadmillConn?.disconnect()
        treadmillConn = null
        protocol = null
        _treadmillState.value = BleConnectionState.DISCONNECTED
        _treadmillData.value = TreadmillData()
        _targetSpeed.value = 0.0
        _targetIncline.value = 0.0
        _treadmillName.value = null
        _isPaused.value = false
        speedBeforePause = 0.0
    }

    private fun stopService() {
        val intent = Intent(appContext, TreadmillService::class.java)
        appContext.stopService(intent)
    }

    // ... (Остальные методы: connectHeartRate, disconnectHeartRate, setSpeed, setIncline, startTreadmill, pauseTreadmill, stopTreadmill, startWorkout и т.д.)
    // Я скопирую их из MainViewModel сюда без изменений.
}
