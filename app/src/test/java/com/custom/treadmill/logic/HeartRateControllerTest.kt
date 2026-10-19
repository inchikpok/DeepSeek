package com.custom.treadmill.logic

import com.custom.treadmill.data.repository.AppSettings
import com.custom.treadmill.data.repository.HrMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateControllerTest {

    @Test
    fun `пульс выше верхней границы снижает скорость`() {
        val settings = AppSettings(hrMode = HrMode.TARGET, targetHr = 130, thresholdHigh = 5, stepKmh = 0.5)
        val newSpeed = HeartRateController.evaluate(settings, heartRate = 140, currentSpeedKmh = 5.0)
        assertEquals(4.5, newSpeed ?: 0.0, 0.01)
    }

    @Test
    fun `пульс ниже нижней границы повышает скорость`() {
        val settings = AppSettings(hrMode = HrMode.TARGET, targetHr = 130, thresholdLow = 5, stepKmh = 0.5)
        val newSpeed = HeartRateController.evaluate(settings, heartRate = 120, currentSpeedKmh = 5.0)
        assertEquals(5.5, newSpeed ?: 0.0, 0.01)
    }

    @Test
    fun `пульс в зоне не меняет скорость`() {
        val settings = AppSettings(hrMode = HrMode.TARGET, targetHr = 130, thresholdHigh = 5, thresholdLow = 5)
        val newSpeed = HeartRateController.evaluate(settings, heartRate = 130, currentSpeedKmh = 5.0)
        assertNull(newSpeed)
    }
}
