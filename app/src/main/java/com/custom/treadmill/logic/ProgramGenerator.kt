package com.custom.treadmill.logic

import com.custom.treadmill.data.database.ProgramData
import com.custom.treadmill.data.database.ProgramSegment
import com.custom.treadmill.data.repository.ExperienceLevel
import com.custom.treadmill.data.repository.UserProfile
import kotlin.math.roundToInt

enum class WorkoutGoal(val title: String, val description: String) {
    WEIGHT_LOSS("Похудение", "Жиросжигание, низкий пульс, ходьба в горку"),
    CARDIO("Выносливость", "Крепкое сердце, ровный бег в среднем темпе"),
    SPEED("Скорость", "Интервалы, высокий пульс, развитие скорости")
}

object ProgramGenerator {
    fun generate(profile: UserProfile, goal: WorkoutGoal, durationMin: Int): ProgramData {
        val segments = mutableListOf<ProgramSegment>()
        // Считаем ИМТ: вес / (рост в метрах)^2
        val bmi = profile.weightKg / ((profile.heightCm / 100.0) * (profile.heightCm / 100.0))

        // Базовые скорости в зависимости от ИМТ
        var walkSpeed = if (bmi > 30) 4.0 else 5.0
        var jogSpeed = if (bmi > 30) 6.0 else if (bmi > 25) 7.0 else 8.5
        var runSpeed = if (bmi > 30) 7.5 else if (bmi > 25) 9.0 else 11.0

        // Модификатор сложности
        val levelMult = when(profile.level) {
            ExperienceLevel.BEGINNER -> 0.85
            ExperienceLevel.INTERMEDIATE -> 1.0
            ExperienceLevel.ADVANCED -> 1.15
        }

        walkSpeed = (walkSpeed * levelMult).roundToTenth().coerceIn(3.0, 6.0)
        jogSpeed = (jogSpeed * levelMult).roundToTenth().coerceIn(5.0, 9.0)
        runSpeed = (runSpeed * levelMult).roundToTenth().coerceIn(7.0, 16.0)

        when (goal) {
            WorkoutGoal.WEIGHT_LOSS -> {
                val warmup = minOf(durationMin / 3, 10)
                val cooldown = minOf(durationMin / 3, 5)
                val main = durationMin - warmup - cooldown

                segments.add(ProgramSegment(warmup * 60, walkSpeed, 0.0, "Разминка (Ходьба)"))
                var remaining = main
                var isHill = true
                var blockNum = 1
                while (remaining > 0) {
                    val blockTime = if (remaining >= 3) 3 else remaining
                    if (isHill) {
                        segments.add(ProgramSegment(blockTime * 60, jogSpeed, 6.0, "Горка $blockNum"))
                    } else {
                        segments.add(ProgramSegment(blockTime * 60, walkSpeed, 0.0, "Восстановление $blockNum"))
                    }
                    isHill = !isHill
                    remaining -= blockTime
                    if (!isHill) blockNum++
                }
                segments.add(ProgramSegment(cooldown * 60, walkSpeed, 0.0, "Заминка"))
            }
            WorkoutGoal.CARDIO -> {
                val warmup = minOf(durationMin / 4, 10)
                val cooldown = minOf(durationMin / 4, 5)
                val main = durationMin - warmup - cooldown
                segments.add(ProgramSegment(warmup * 60, walkSpeed, 0.0, "Разминка"))
                segments.add(ProgramSegment(main * 60, jogSpeed, 1.0, "Основной бег (Кардио)"))
                segments.add(ProgramSegment(cooldown * 60, walkSpeed, 0.0, "Заминка"))
            }
            WorkoutGoal.SPEED -> {
                val warmup = minOf(durationMin / 3, 12)
                val cooldown = minOf(durationMin / 4, 6)
                val main = durationMin - warmup - cooldown
                segments.add(ProgramSegment((warmup - 2) * 60, jogSpeed, 0.0, "Разминка (Бег)"))
                segments.add(ProgramSegment(60, runSpeed, 0.0, "Ускорение"))
                segments.add(ProgramSegment(60, walkSpeed, 0.0, "Отдых"))

                var remaining = main
                var intNum = 1
                while (remaining > 0) {
                    val fastTime = minOf(60, remaining)
                    segments.add(ProgramSegment(fastTime, runSpeed, 1.0, "Интервал $intNum"))
                    remaining -= fastTime
                    if (remaining <= 0) break

                    val restTime = minOf(120, remaining)
                    segments.add(ProgramSegment(restTime, jogSpeed, 0.0, "Отдых $intNum"))
                    remaining -= restTime
                    intNum++
                }
                segments.add(ProgramSegment(cooldown * 60, walkSpeed, 0.0, "Заминка"))
            }
        }

        val title = when(goal) {
            WorkoutGoal.WEIGHT_LOSS -> "Жиросжигание $durationMin мин"
            WorkoutGoal.CARDIO -> "Кардио $durationMin мин"
            WorkoutGoal.SPEED -> "Интервалы $durationMin мин"
        }

        return ProgramData(title, "Сгенерировано по профилю: ${profile.name}", segments)
    }

    private fun Double.roundToTenth(): Double = (this * 10).roundToInt() / 10.0
}
