package com.custom.treadmill.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgramDao {
    @Query("SELECT * FROM programs ORDER BY name ASC")
    fun getAll(): Flow<List<ProgramEntity>>

    @Query("SELECT * FROM programs WHERE id = :id")
    suspend fun getById(id: Long): ProgramEntity?

    @Insert
    suspend fun insert(p: ProgramEntity): Long

    @Update
    suspend fun update(p: ProgramEntity)

    @Delete
    suspend fun delete(p: ProgramEntity)
}

@Dao
interface WorkoutLogDao {
    @Query("SELECT * FROM workout_logs ORDER BY dateMillis DESC")
    fun getAll(): Flow<List<WorkoutLogEntity>>

    @Insert
    suspend fun insert(l: WorkoutLogEntity)
}
