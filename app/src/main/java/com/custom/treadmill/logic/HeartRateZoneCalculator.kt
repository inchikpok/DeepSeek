package com.custom.treadmill.logic

import com.custom.treadmill.data.repository.AppSettings

object HeartRateZoneCalculator {

    /**
     * Расчет максимального пульса (MaxHR).
     * Формула Tanaka (мужчины): 208 - 0.7 * возраст
     * Формула Gulati (женщины): 206 - 0.88 * возраст
     */
    fun calculateMaxHr(age: Int, isMale: Boolean): Int {
        return if (isMale) {
            (208 - 0.7 * age).toInt()
        } else {
            (206 - 0.88 * age).toInt()
        }
    }

    /**
     * Расчет границ зон по формуле Карвонена (Heart Rate Reserve).
     * Возвращает список из 5 чисел: начало зоны 1 (50%), зоны 2 (60%), зоны 3 (70%), зоны 4 (80%), зоны 5 (90%).
     */
    fun getZoneBounds(maxHr: Int, restingHr: Int): List<Int> {
        val hrr = (maxHr - restingHr).coerceAtLeast(1)
        return listOf(
            (hrr * 0.50 + restingHr).toInt(), // Z1: 50-60%
            (hrr * 0.60 + restingHr).toInt(), // Z2: 60-70%
            (hrr * 0.70 + restingHr).toInt(), // Z3: 70-80%
            (hrr * 0.80 + restingHr).toInt(), // Z4: 80-90%
            (hrr * 0.90 + restingHr).toInt()  // Z5: 90-100%
        )
    }

    fun getEffectiveMaxHr(settings: AppSettings): Int {
        return if (settings.autoMaxHr) {
            calculateMaxHr(settings.age, settings.isMale)
        } else {
            settings.maxHr.coerceAtLeast(settings.restingHr + 20)
        }
    }
}
