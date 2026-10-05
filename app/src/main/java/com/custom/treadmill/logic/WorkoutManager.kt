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
    val paused: Boolean = false,
    val finished: Boolean = false,
    val programName: String = "",
    val totalSec: Int = 0,
    val totalElapsedSec: Int = 0,
    val currentSegmentIndex: Int = 0,
    val totalSegments: Int = 0,
    val currentSegmentName: String = "",
    val segmentElapsedSec: Int = 0,
    val segmentDurationSec: Int = 0
)

class WorkoutManager {

    private val _state = MutableStateFlow(WorkoutState())
    val state: StateFlow<WorkoutState> = _state.asStateFlow()

    private var job: Job? = null
    private var currentProgram: ProgramData? = null
    private var scopeRef: CoroutineScope? = null
    private var onSetSpeedRef: (suspend (Double) -> Unit)? = null
    private var onSetInclineRef: (suspend (Double) -> Unit)? = null

    /** Запустить программу с нуля. */
    fun start(
        scope: CoroutineScope,
        program: ProgramData,
        onSetSpeed: suspend (Double) -> Unit,
        onSetIncline: suspend (Double) -> Unit = {}
    ) {
        stop()
        currentProgram = program
        scopeRef = scope
        onSetSpeedRef = onSetSpeed
        onSetInclineRef = onSetIncline

        val total = program.segments.sumOf { it.durationSec }
        _state.value = WorkoutState(
            running = true,
            paused = false,
            finished = false,
            programName = program.name,
            totalSec = total,
            totalSegments = program.segments.size,
            currentSegmentIndex = 0,
            segmentElapsedSec = 0,
            totalElapsedSec = 0,
            segmentDurationSec = program.segments.firstOrNull()?.durationSec ?: 0,
            currentSegmentName = program.segments.firstOrNull()?.name.orEmpty()
        )
        launchLoop()
    }

    /** Пауза. Состояние сохраняется, job отменяется. */
    fun pause() {
        if (!_state.value.running || _state.value.paused) return
        job?.cancel()
        job = null
        _state.value = _state.value.copy(paused = true)
    }

    /** Продолжить с того места, где остановились. */
    fun resume() {
        if (!_state.value.paused) return
        _state.value = _state.value.copy(paused = false)
        launchLoop()
    }

    /** Переключить паузу. */
    fun togglePause() {
        val s = _state.value
        when {
            s.paused -> resume()
            s.running -> pause()
        }
    }

    /** Полный сброс. Программа удаляется. */
    fun stop() {
        job?.cancel()
        job = null
        currentProgram = null
        scopeRef = null
        onSetSpeedRef = null
        onSetInclineRef = null
        _state.value = WorkoutState()
    }

    /** Устаревшее имя (совместимость). */
    fun reset() = stop()

    private fun launchLoop() {
        val scope = scopeRef ?: return
        val program = currentProgram ?: return
        // Явные типы suspend-функций — иначе компилятор не выводит
        val onSetSpeed: suspend (Double) -> Unit = onSetSpeedRef ?: return
        val onSetIncline: suspend (Double) -> Unit = onSetInclineRef ?: { _: Double -> }

        job = scope.launch {
            val segments = program.segments

            // Если это resume (мы уже в середине сегмента) — сразу вернём команды
            if (_state.value.segmentElapsedSec > 0 &&
                _state.value.currentSegmentIndex < segments.size
            ) {
                val seg = segments[_state.value.currentSegmentIndex]
                onSetSpeed(seg.speedKmh)
                onSetIncline(seg.inclinePercent)
            }

            var index = _state.value.currentSegmentIndex
            var totalElapsed = _state.value.totalElapsedSec

            while (index < segments.size && isActive) {
                val seg = segments[index]

                // Новый сегмент — отправляем команды
                if (_state.value.segmentElapsedSec == 0) {
                    onSetSpeed(seg.speedKmh)
                    onSetIncline(seg.inclinePercent)
                    _state.value = _state.value.copy(
                        currentSegmentIndex = index,
                        currentSegmentName = seg.name.ifBlank { "Сегмент ${index + 1}" },
                        segmentDurationSec = seg.durationSec,
                        segmentElapsedSec = 0
                    )
                }

                var s = _state.value.segmentElapsedSec
                while (s < seg.durationSec && isActive) {
                    delay(1000L)
                    s++
                    totalElapsed++
                    _state.value = _state.value.copy(
                        segmentElapsedSec = s,
                        totalElapsedSec = totalElapsed
                    )
                }

                if (s >= seg.durationSec) {
                    index++
                    if (index < segments.size) {
                        _state.value = _state.value.copy(
                            currentSegmentIndex = index,
                            segmentElapsedSec = 0
                        )
                    }
                }
            }

            if (isActive) {
                _state.value = _state.value.copy(
                    running = false,
                    paused = false,
                    finished = true
                )
            }
        }
    }
}
