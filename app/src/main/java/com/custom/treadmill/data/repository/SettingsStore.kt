package com.custom.treadmill.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ProtocolType { FTMS, FITSHOW }
enum class HrMode { TARGET, ZONE }

@Serializable
data class AppSettings(
    val protocol: ProtocolType = ProtocolType.FTMS,
    val manualWriteUuid: String = "",
    val manualNotifyUuid: String = "",
    val minSpeedKmh: Double = 1.0,
    val maxSpeedKmh: Double = 12.0,
    val hrEnabled: Boolean = false,
    val hrMode: HrMode = HrMode.TARGET,
    val targetHr: Int = 130,
    val zoneMin: Int = 120,
    val zoneMax: Int = 140,
    val thresholdHigh: Int = 5,
    val thresholdLow: Int = 5,
    val intervalSec: Int = 30,
    val stepKmh: Double = 0.5
)

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("treadmill_settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings = try {
        prefs.getString(KEY, null)?.let { json.decodeFromString(it) } ?: AppSettings()
    } catch (_: Exception) {
        AppSettings()
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value)
        _settings.value = s
        prefs.edit().putString(KEY, json.encodeToString(s)).apply()
    }

    companion object { private const val KEY = "settings_json" }
}
