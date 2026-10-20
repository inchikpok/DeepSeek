package com.custom.treadmill

import android.app.Application
import com.custom.treadmill.ble.BleManager
import com.custom.treadmill.ble.TreadmillSession
import com.custom.treadmill.data.database.AppDatabase
import com.custom.treadmill.data.repository.ProgramRepository
import com.custom.treadmill.data.repository.SettingsStore
import com.custom.treadmill.logic.TtsManager

class TreadmillApp : Application() {

    lateinit var bleManager: BleManager
        private set
    lateinit var database: AppDatabase
        private set
    lateinit var programRepository: ProgramRepository
        private set
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var ttsManager: TtsManager
        private set
    lateinit var session: TreadmillSession
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        bleManager = BleManager(this)
        database = AppDatabase.get(this)
        programRepository = ProgramRepository(database.programDao(), database.workoutLogDao())
        settingsStore = SettingsStore(this)
        ttsManager = TtsManager(this)
        session = TreadmillSession(this)
    }

    companion object {
        lateinit var instance: TreadmillApp
            private set
    }
}
