package com.custom.treadmill.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProgramEntity::class, WorkoutLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun programDao(): ProgramDao
    abstract fun workoutLogDao(): WorkoutLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Сюда будем добавлять миграции в будущем, например:
        // val MIGRATION_1_2 = object : Migration(1, 2) { ... }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "treadmill.db"
                )
                // ВАЖНО: Мы убрали fallbackToDestructiveMigration()
                // Теперь при изменении схемы без миграции приложение упадет,
                // но НЕ сотрет данные пользователя молча.
                .build().also { INSTANCE = it }
            }
    }
}
