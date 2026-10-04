package com.custom.treadmill.logic

import com.custom.treadmill.data.repository.AppSettings
import com.custom.treadmill.data.repository.HrMode

object HeartRateController {

    fun evaluate(settings: AppSettings, heartRate: Int, currentSpeedKmh: Double): Double? {
        if (heartRate <= 0) return null

        val (lowerBound, upperBound) = when (settings.hrMode) {
            HrMode.TARGET -> (settings.targetHr - settings.thresholdLow) to
                    (settings.targetHr + settings.thresholdHigh)
            HrMode.ZONE -> settings.zoneMin to settings.zoneMax
        }

        var newSpeed = currentSpeedKmh
        when {
            heartRate > upperBound -> newSpeed -= settings.stepKmh
            heartRate < lowerBound -> newSpeed += settings.stepKmh
            else -> return null
        }

        newSpeed = newSpeed.coerceIn(settings.minSpeedKmh, settings.maxSpeedKmh)
        return if (kotlin.math.abs(newSpeed - currentSpeedKmh) < 0.01) null else newSpeed
    }
}
