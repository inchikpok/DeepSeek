package com.custom.treadmill.data.repository

import com.custom.treadmill.data.database.ProgramData
import com.custom.treadmill.data.database.ProgramDao
import com.custom.treadmill.data.database.ProgramEntity
import com.custom.treadmill.data.database.ProgramSegment
import com.custom.treadmill.data.database.WorkoutLogDao
import com.custom.treadmill.data.database.WorkoutLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

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

    fun toData(e: ProgramEntity): ProgramData {
        val segs: List<ProgramSegment> = try {
            json.decodeFromString(e.segmentsJson)
        } catch (_: Exception) {
            emptyList()
        }
        return ProgramData(e.name, e.description, segs)
    }

    fun toEntity(d: ProgramData, id: Long = 0): ProgramEntity =
        ProgramEntity(id, d.name, d.description, json.encodeToString(d.segments))

    fun exportJson(d: ProgramData): String = json.encodeToString(d)

    fun importJson(text: String): ProgramData = json.decodeFromString(text)
}
