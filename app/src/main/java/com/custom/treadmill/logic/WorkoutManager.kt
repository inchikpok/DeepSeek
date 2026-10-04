package com.custom.treadmill.logic

import com.custom.treadmill.data.database.ProgramData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class WorkoutState(
    val running: Boolean = false,
    val finished: Boolean = false,
    val programName: String = "",
    val totalSec: Int = 0,
    val totalElapsedSec: Int = 0,
    val currentSegmentIndex: Int = 0,
    val currentSegmentName: String = "",
    val segmentElapsedSec: Int = 0,
    val segmentDurationSec: Int = 0
)

class WorkoutManager {

    private val _state = MutableStateFlow(WorkoutState())
    val state: StateFlow<WorkoutState> = _state.asStateFlow()

    private var job: Job? = null

    fun start(
        scope: CoroutineScope,
        program: ProgramData,
        onSetSpeed: suspend (Double) -> Unit,
        onSetIncline: suspend (Double) -> Unit = {}
    ) {
        stop()
        job = scope.launch {
            val segments = program.segments
            val total = segments.sumOf { it.durationSec }
            _state.value = WorkoutState(
                running = true,
                programName = program.name,
                totalSec = total
            )
            var elapsed = 0
            for ((index, seg) in segments.withIndex()) {
                onSetSpeed(seg.speedKmh)
                onSetIncline(seg.inclinePercent)
                _state.value = _state.value.copy(
                    currentSegmentIndex = index,
                    currentSegmentName = seg.name.ifBlank { "Сегмент ${index + 1}" },
                    segmentDurationSec = seg.durationSec,
                    segmentElapsedSec = 0
                )
                var s = 0
                while (s < seg.durationSec && isActive) {
                    delay(1000L)
                    s++
                    elapsed++
                    _state.value = _state.value.copy(
                        segmentElapsedSec = s,
                        totalElapsedSec = elapsed
                    )
                }
            }
            _state.value = _state.value.copy(running = false, finished = true)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = _state.value.copy(running = false)
    }

    fun reset() {
        stop()
        _state.value = WorkoutState()
    }
}
