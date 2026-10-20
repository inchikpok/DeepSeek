package com.custom.treadmill.ui.viewmodels

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import com.custom.treadmill.DebugLog
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

    private val _scanMode = MutableStateFlow(ScanMode.NONE)
    val scanMode: StateFlow<ScanMode> = _scanMode.asStateFlow()

    fun showMessage(msg: String) = session.showMessage(msg)
    fun clearStatusMessage() = session.clearStatusMessage()

    fun connectTreadmill(device: BluetoothDevice) {
        DebugLog.d("VM", "connectTreadmill(${device.address})")
        _scanMode.value = ScanMode.NONE
        session.connectTreadmill(device)
    }

    fun connectHeartRate(device: BluetoothDevice) {
        DebugLog.d("VM", "connectHeartRate(${device.address})")
        _scanMode.value = ScanMode.NONE
        session.connectHeartRate(device)
    }

    fun disconnectTreadmill() {
        DebugLog.d("VM", "disconnectTreadmill()")
        session.disconnectTreadmill()
    }
    fun disconnectHeartRate() {
        DebugLog.d("VM", "disconnectHeartRate()")
        session.disconnectHeartRate()
    }

    fun setSpeed(kmh: Double) {
        DebugLog.d("VM", "setSpeed(${kmh})")
        session.setSpeed(kmh)
    }
    fun setIncline(percent: Double) {
        DebugLog.d("VM", "setIncline(${percent})")
        session.setIncline(percent)
    }

    fun startTreadmill() {
        DebugLog.d("VM", "startTreadmill()")
        session.startTreadmill()
    }
    fun pauseTreadmill() {
        DebugLog.d("VM", "pauseTreadmill()")
        session.pauseTreadmill()
    }
    fun togglePauseTreadmill() {
        DebugLog.d("VM", "togglePauseTreadmill()")
        session.togglePauseTreadmill()
    }
    fun stopTreadmill() {
        DebugLog.d("VM", "stopTreadmill()")
        session.stopTreadmill()
    }

    fun startWorkout(program: ProgramData) {
        DebugLog.d("VM", "startWorkout(${program.name})")
        session.startWorkout(program)
    }
    fun pauseWorkout() {
        DebugLog.d("VM", "pauseWorkout()")
        session.pauseWorkout()
    }
    fun resumeWorkout() {
        DebugLog.d("VM", "resumeWorkout()")
        session.resumeWorkout()
    }
    fun togglePauseWorkout() {
        DebugLog.d("VM", "togglePauseWorkout()")
        session.togglePauseWorkout()
    }
    fun stopWorkout() {
        DebugLog.d("VM", "stopWorkout()")
        session.stopWorkout()
    }
    fun skipWorkoutSegment() {
        DebugLog.d("VM", "skipWorkoutSegment()")
        session.skipWorkoutSegment()
    }
    fun saveWorkoutLog() {
        DebugLog.d("VM", "saveWorkoutLog()")
        session.saveWorkoutLog()
    }

    fun sendRawHex(hex: String): Boolean {
        DebugLog.d("VM", "sendRawHex($hex)")
        return session.sendRawHex(hex)
    }
    fun setAutoHrEnabled(enabled: Boolean) {
        DebugLog.d("VM", "setAutoHrEnabled($enabled)")
        session.setAutoHrEnabled(enabled)
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        DebugLog.d("VM", "updateSettings()")
        settingsStore.update(transform)
    }
    fun setHrMode(mode: HrMode) {
        DebugLog.d("VM", "setHrMode($mode)")
        settingsStore.update { it.copy(hrMode = mode) }
    }

    fun startScanTreadmill() {
        DebugLog.d("VM", "startScanTreadmill()")
        _scanMode.value = ScanMode.TREADMILL
        session.startScanTreadmill()
    }
    fun startScanHeartRate() {
        DebugLog.d("VM", "startScanHeartRate()")
        _scanMode.value = ScanMode.HEART_RATE
        session.startScanHeartRate()
    }
    fun stopScan() {
        DebugLog.d("VM", "stopScan()")
        _scanMode.value = ScanMode.NONE
        session.stopScan()
    }

    fun clearLogs() {
        DebugLog.d("VM", "clearLogs()")
        session.clearLogs()
    }
}
