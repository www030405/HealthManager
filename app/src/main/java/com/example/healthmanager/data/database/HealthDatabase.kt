package com.example.healthmanager.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.healthmanager.data.dao.DietDao
import com.example.healthmanager.data.dao.ExerciseDao
import com.example.healthmanager.data.dao.HealthScoreHistoryDao
import com.example.healthmanager.data.dao.SleepDao
import com.example.healthmanager.data.dao.UserDao
import com.example.healthmanager.data.entity.DietRecord
import com.example.healthmanager.data.entity.ExerciseRecord
import com.example.healthmanager.data.entity.HealthScoreHistory
import com.example.healthmanager.data.entity.SleepRecord
import com.example.healthmanager.data.entity.User

/**
 * Room 数据库主类
 * 单例模式确保全局只有一个数据库实例
 */
@Database(
    entities = [User::class, ExerciseRecord::class, DietRecord::class, SleepRecord::class, HealthScoreHistory::class],
    version = 2,
    exportSchema = false
)
abstract class HealthDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun dietDao(): DietDao
    abstract fun sleepDao(): SleepDao
    abstract fun healthScoreHistoryDao(): HealthScoreHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: HealthDatabase? = null

        fun getDatabase(context: Context): HealthDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HealthDatabase::class.java,
                    "health_manager_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
