package com.zuxing.markmap.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.zuxing.markmap.data.dao.GroupDao
import com.zuxing.markmap.data.dao.LineDao
import com.zuxing.markmap.data.dao.PointDao
import com.zuxing.markmap.data.entity.GroupEntity
import com.zuxing.markmap.data.entity.LineEntity
import com.zuxing.markmap.data.entity.PointEntity

/**
 * Room 数据库
 */
@Database(
    entities = [
        GroupEntity::class,
        LineEntity::class,
        PointEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun groupDao(): GroupDao
    abstract fun lineDao(): LineDao
    abstract fun pointDao(): PointDao

    companion object {
        private const val DATABASE_NAME = "markmap_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}