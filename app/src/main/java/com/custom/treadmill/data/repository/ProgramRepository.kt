package com.custom.treadmill.data.repository

import com.custom.treadmill.data.database.ProgramData
import com.custom.treadmill.data.database.ProgramDao
import com.custom.treadmill.data.database.ProgramEntity
import com.custom.treadmill.data.database.ProgramSegment
import com.custom.treadmill.data.database.WorkoutLogDao
import com.custom.treadmill.data.database.WorkoutLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.random.Random

class ProgramRepository(
    private val programDao: ProgramDao,
    private val logDao: WorkoutLogDao
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val programs: Flow<List<ProgramEntity>> = programDao.getAll()
    val logs: Flow<List<WorkoutLogEntity>> = logDao.getAll()

    suspend fun save(entity: ProgramEntity): Long =
        if (entity.id == 0L) programDao.insert(entity)
        else {
            programDao.update(entity)
            entity.id
        }

    suspend fun delete(entity: ProgramEntity) = programDao.delete(entity)
    suspend fun addLog(log: WorkoutLogEntity) = logDao.insert(log)

    /** Заполняет журнал 20 демо-тренировками за ~10 недель с падающим пульсом. */
    suspend fun addDemoLogs() {
        logDao.insertAll(buildDemoLogs())
    }

    /** Удаляет все записи из журнала. */
    suspend fun clearLogs() = logDao.deleteAll()

    // ---------- Сериализация ----------

    fun toData(e: ProgramEntity): ProgramData {
        val segs: List<ProgramSegment> = try {
            json.decodeFromString(
                ListSerializer(ProgramSegment.serializer()),
                e.segmentsJson
            )
        } catch (_: Exception) {
            emptyList()
        }
        return ProgramData(e.name, e.description, segs)
    }

    fun toEntity(d: ProgramData, id: Long = 0): ProgramEntity =
        ProgramEntity(
            id,
            d.name,
            d.description,
            json.encodeToString(ListSerializer(ProgramSegment.serializer()), d.segments)
        )

    fun exportJson(d: ProgramData): String =
        json.encodeToString(ProgramData.serializer(), d)

    fun importJson(text: String): ProgramData =
        json.decodeFromString(ProgramData.serializer(), text)

    // ============================================================
    //  Генератор демо-данных
    // ============================================================
    private fun buildDemoLogs(): List<WorkoutLogEntity> {
        val dayMs = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        val count = 20
        val rnd = Random(42)

        val programs = listOf(
            "Интервалы 4×3 мин"   to 1.00,
            "Длительный бег"      to 1.15,
            "Темповый бег"        to 1.05,
            "Восстановительный"   to 0.85,
            "Fartlek 20 мин"      to 1.00,
            "Горки 6×1 мин"       to 0.95
        )

        val logs = ArrayList<WorkoutLogEntity>(count)

        for (i in 0 until count) {
            // i = 0 → самая старая тренировка, i = count-1 → самая новая
            val progress = i.toDouble() / (count - 1)     // 0..1
            val daysAgo = (count - 1 - i) * 4              // каждые ~4 дня
            val date = now - daysAgo.toLong() * dayMs
            val (name, mult) = programs[i % programs.size]

            // Пульс: 155 → 135 средний, 178 → 168 максимальный
            val avgHr = (155 - progress * 20).toInt() + rnd.nextInt(-3, 4)
            val maxHr = (178 - progress * 10).toInt() + rnd.nextInt(-2, 3)

            // Дистанция: 3 → 8 км
            val distanceKm = (3.0 + progress * 5.0 + rnd.nextDouble(-0.4, 0.4)) * mult

            // Длительность: 30 → 55 мин
            val durationSec = (1800 + progress * 1500 + rnd.nextInt(-180, 180)).toInt()
                .coerceAtLeast(900)

            // Калории: 200 → 500
            val calories = (200 + progress * 300 + rnd.nextInt(-30, 30)).toInt()
                .coerceAtLeast(80)

            // Средняя скорость: 8.5 → 11 км/ч
            val avgSpeed = (8.5 + progress * 2.5 + rnd.nextDouble(-0.3, 0.3)) * mult

            logs.add(
                WorkoutLogEntity(
                    dateMillis = date,
                    programName = name,
                    durationSec = durationSec,
                    distanceKm = distanceKm.coerceAtLeast(0.5),
                    calories = calories,
                    avgHeartRate = avgHr,
                    maxHeartRate = maxHr,
                    avgSpeedKmh = avgSpeed.coerceAtLeast(3.0)
                )
            )
        }

        return logs
    }
}
