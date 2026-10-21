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
    /**
     * @param intensityPercent от 70 до 130. 100 = базовый уровень профиля.
     */
    fun generate(
        profile: UserProfile,
        goal: WorkoutGoal,
        durationMin: Int,
        intensityPercent: Int = 100
    ): ProgramData {
        val segments = mutableListOf<ProgramSegment>()
        val bmi = profile.weightKg / ((profile.heightCm / 100.0) * (profile.heightCm / 100.0))

        // 1. Штраф за высокий вес (защита суставов и аэробная способность)
        val bmiMult = when {
            bmi > 35 -> 0.65
            bmi > 30 -> 0.75
            bmi > 25 -> 0.88
            else -> 1.0
        }

        // 2. База по уровню (значительная разница!)
        val levelJog = when (profile.level) {
            ExperienceLevel.BEGINNER -> 6.0
            ExperienceLevel.INTERMEDIATE -> 8.5
            ExperienceLevel.ADVANCED -> 11.0
        }
        val levelRun = when (profile.level) {
            ExperienceLevel.BEGINNER -> 8.0
            ExperienceLevel.INTERMEDIATE -> 12.0
            ExperienceLevel.ADVANCED -> 15.0
        }
        val levelIncline = when (profile.level) {
            ExperienceLevel.BEGINNER -> 3.0
            ExperienceLevel.INTERMEDIATE -> 6.0
            ExperienceLevel.ADVANCED -> 10.0
        }

        // 3. Множитель от ползунка (0.7 … 1.3)
        val userMult = intensityPercent / 100.0

        val walkSpeed = (4.5 * bmiMult * userMult).coerceIn(3.0, 6.5).roundToTenth()
        val jogSpeed = (levelJog * bmiMult * userMult).coerceIn(4.5, 16.0).roundToTenth()
        val runSpeed = (levelRun * bmiMult * userMult).coerceIn(6.0, 20.0).roundToTenth()
        val maxIncline = (levelIncline * userMult).coerceIn(1.0, 15.0)

        when (goal) {
            WorkoutGoal.WEIGHT_LOSS -> {
                val warmup = minOf(durationMin / 4, 8)
                val cooldown = minOf(durationMin / 5, 5)
                val main = durationMin - warmup - cooldown

                segments.add(ProgramSegment(warmup * 60, walkSpeed, 0.0, "Разминка (Ходьба)"))

                // Интервалы: чем выше уровень, тем длиннее рабочая фаза
                val fastBlockMin = when (profile.level) {
                    ExperienceLevel.BEGINNER -> 2
                    ExperienceLevel.INTERMEDIATE -> 3
                    ExperienceLevel.ADVANCED -> 4
                }
                val slowBlockMin = when (profile.level) {
                    ExperienceLevel.BEGINNER -> 2
                    ExperienceLevel.INTERMEDIATE -> 2
                    ExperienceLevel.ADVANCED -> 1
                }

                var remaining = main
                var block = 1
                while (remaining > 0) {
                    val fast = minOf(fastBlockMin, remaining)
                    if (fast > 0) {
                        segments.add(ProgramSegment(fast * 60, jogSpeed, maxIncline * 0.7, "Ходьба в горку $block"))
                        remaining -= fast
                    }
                    if (remaining <= 0) break
                    val slow = minOf(slowBlockMin, remaining)
                    segments.add(ProgramSegment(slow * 60, walkSpeed, 0.0, "Восстановление $block"))
                    remaining -= slow
                    block++
                }
                segments.add(ProgramSegment(cooldown * 60, walkSpeed, 0.0, "Заминка"))
            }

            WorkoutGoal.CARDIO -> {
                val warmupWalk = minOf(durationMin / 6, 5)
                val warmupJog = minOf(durationMin / 6, 5)
                val cooldown = minOf(durationMin / 5, 5)
                val main = durationMin - warmupWalk - warmupJog - cooldown

                segments.add(ProgramSegment(warmupWalk * 60, walkSpeed, 0.0, "Разминка (Ходьба)"))
                segments.add(ProgramSegment(warmupJog * 60, jogSpeed, 0.0, "Разминка (Бег)"))
                if (main > 0) {
                    segments.add(ProgramSegment(main * 60, jogSpeed, maxIncline * 0.3, "Основной бег (Кардио)"))
                }
                segments.add(ProgramSegment(cooldown * 60, walkSpeed, 0.0, "Заминка"))
            }

            WorkoutGoal.SPEED -> {
                val warmup = minOf(durationMin / 4, 12)
                val cooldown = minOf(durationMin / 5, 6)
                val main = durationMin - warmup - cooldown

                segments.add(ProgramSegment((warmup - 2) * 60, jogSpeed, 0.0, "Разминка (Бег)"))
                segments.add(ProgramSegment(60, runSpeed, 0.0, "Ускорение"))
                segments.add(ProgramSegment(60, walkSpeed, 0.0, "Отдых"))

                // Интервалы: чем выше уровень, тем длиннее быстрый отрезок и короче отдых
                val fastSec = when (profile.level) {
                    ExperienceLevel.BEGINNER -> 30
                    ExperienceLevel.INTERMEDIATE -> 45
                    ExperienceLevel.ADVANCED -> 60
                }
                val slowSec = when (profile.level) {
                    ExperienceLevel.BEGINNER -> 90
                    ExperienceLevel.INTERMEDIATE -> 60
                    ExperienceLevel.ADVANCED -> 45
                }

                var remaining = main
                var intNum = 1
                while (remaining > 0) {
                    val fast = minOf(fastSec, remaining)
                    segments.add(ProgramSegment(fast, runSpeed, maxIncline * 0.2, "Интервал $intNum"))
                    remaining -= fast
                    if (remaining <= 0) break
                    val slow = minOf(slowSec, remaining)
                    segments.add(ProgramSegment(slow, (jogSpeed * 0.9).roundToTenth(), 0.0, "Отдых $intNum"))
                    remaining -= slow
                    intNum++
                }
                segments.add(ProgramSegment(cooldown * 60, walkSpeed, 0.0, "Заминка"))
            }
        }

        val levelStr = when (profile.level) {
            ExperienceLevel.BEGINNER -> "новичок"
            ExperienceLevel.INTERMEDIATE -> "средний"
            ExperienceLevel.ADVANCED -> "профи"
        }
        val title = when (goal) {
            WorkoutGoal.WEIGHT_LOSS -> "Жиросжигание ${durationMin}м"
            WorkoutGoal.CARDIO -> "Кардио ${durationMin}м"
            WorkoutGoal.SPEED -> "Интервалы ${durationMin}м"
        }

        return ProgramData(
            name = title,
            description = "${goal.title} · Уровень: $levelStr · Интенсивность: $intensityPercent%",
            segments = segments
        )
    }

    private fun Double.roundToTenth(): Double = (this * 10).roundToInt() / 10.0
}
