package com.custom.treadmill.logic

import com.custom.treadmill.data.repository.AppSettings

object HeartRateZoneCalculator {

    fun calculateMaxHr(age: Int, isMale: Boolean): Int {
        return if (isMale) {
            (208 - 0.7 * age).toInt()
        } else {
            (206 - 0.88 * age).toInt()
        }
    }

    fun getZoneBounds(maxHr: Int, restingHr: Int): List<Int> {
        val hrr = (maxHr - restingHr).coerceAtLeast(1)
        return listOf(
            (hrr * 0.50 + restingHr).toInt(),
            (hrr * 0.60 + restingHr).toInt(),
            (hrr * 0.70 + restingHr).toInt(),
            (hrr * 0.80 + restingHr).toInt(),
            (hrr * 0.90 + restingHr).toInt()
        )
    }

    fun getEffectiveMaxHr(settings: AppSettings): Int {
        val p = settings.activeProfile
        return if (settings.autoMaxHr) {
            calculateMaxHr(p.age, p.isMale)
        } else {
            settings.maxHr.coerceAtLeast(p.restingHr + 20)
        }
    }
}
