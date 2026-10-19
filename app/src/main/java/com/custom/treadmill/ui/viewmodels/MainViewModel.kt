package com.custom.treadmill.ui.viewmodels

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import com.custom.treadmill.TreadmillApp
import com.custom.treadmill.data.database.ProgramData
import com.custom.treadmill.data.repository.AppSettings
import com.custom.treadmill.data.repository.HrMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ScanMode { NONE, TREADMILL, HEART_RATE }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val session = TreadmillApp.instance.session
    private val settingsStore = TreadmillApp.instance.settingsStore

    val settings = settingsStore.settings

    val treadmillState = session.treadmillState
    val treadmillName = session.treadmillName
    val hrName = session.hrName
    val hrState = session.hrState
    val treadmillData = session.treadmillData
    val targetSpeed = session.targetSpeed
    val targetIncline = session.targetIncline
    val heartRate = session.heartRate
    val hrHistory = session.hrHistory
    val statusMessage = session.statusMessage
    val isPaused = session.isPaused
    val uiElapsedSec = session.uiElapsedSec
    val uiDistanceKm = session.uiDistanceKm
    val uiCalories = session.uiCalories
    val logs = session.logs
    val workoutState = session.workoutState

    val scanResults = TreadmillApp.instance.bleManager.scanResults
    val scanLog = TreadmillApp.instance.bleManager.scanLog

    // Восстановили ScanMode
    private val _scanMode = MutableStateFlow(ScanMode.NONE)
    val scanMode: StateFlow<ScanMode> = _scanMode.asStateFlow()

    fun showMessage(msg: String) = session.showMessage(msg)
    fun clearStatusMessage() = session.clearStatusMessage()

    fun connectTreadmill(device: BluetoothDevice) = session.connectTreadmill(device)
    fun disconnectTreadmill() = session.disconnectTreadmill()
    fun connectHeartRate(device: BluetoothDevice) = session.connectHeartRate(device)
    fun disconnectHeartRate() = session.disconnectHeartRate()

    fun setSpeed(kmh: Double) = session.setSpeed(kmh)
    fun setIncline(percent: Double) = session.setIncline(percent)

    fun startTreadmill() = session.startTreadmill()
    fun pauseTreadmill() = session.pauseTreadmill()
    fun togglePauseTreadmill() = session.togglePauseTreadmill()
    fun stopTreadmill() = session.stopTreadmill()

    fun startWorkout(program: ProgramData) = session.startWorkout(program)
    fun pauseWorkout() = session.pauseWorkout()
    fun resumeWorkout() = session.resumeWorkout()
    fun togglePauseWorkout() = session.togglePauseWorkout()
    fun stopWorkout() = session.stopWorkout()
    fun skipWorkoutSegment() = session.skipWorkoutSegment()
    
    // Восстановили saveWorkoutLog
    fun saveWorkoutLog() = session.saveWorkoutLog()

    fun sendRawHex(hex: String): Boolean = session.sendRawHex(hex)
    
    fun setAutoHrEnabled(enabled: Boolean) = session.setAutoHrEnabled(enabled)
    
    fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsStore.update(transform)
    fun setHrMode(mode: HrMode) = settingsStore.update { it.copy(hrMode = mode) }
    
    // Восстановили управление сканированием
    fun startScanTreadmill() { _scanMode.value = ScanMode.TREADMILL; session.startScanTreadmill() }
    fun startScanHeartRate() { _scanMode.value = ScanMode.HEART_RATE; session.startScanHeartRate() }
    fun stopScan() { _scanMode.value = ScanMode.NONE; session.stopScan() }
    
    fun clearLogs() = session.clearLogs()
}
