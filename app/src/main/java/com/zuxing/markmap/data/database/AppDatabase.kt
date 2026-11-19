package com.zuxing.markmap.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zuxing.markmap.data.dao.AchievementDao
import com.zuxing.markmap.data.dao.GroupDao
import com.zuxing.markmap.data.dao.LineDao
import com.zuxing.markmap.data.dao.MergedTrackPlanDao
import com.zuxing.markmap.data.dao.PointDao
import com.zuxing.markmap.data.dao.VisitedPlaceDao
import com.zuxing.markmap.data.entity.AchievementEntity
import com.zuxing.markmap.data.entity.GroupEntity
import com.zuxing.markmap.data.entity.LineEntity
import com.zuxing.markmap.data.entity.MergedTrackPlanEntity
import com.zuxing.markmap.data.entity.PointEntity
import com.zuxing.markmap.data.entity.VisitedPlaceEntity

/**
 * Room 数据库
 */
@Database(
    entities = [
        GroupEntity::class,
        LineEntity::class,
        PointEntity::class,
        MergedTrackPlanEntity::class,
        AchievementEntity::class,
        VisitedPlaceEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun groupDao(): GroupDao
    abstract fun lineDao(): LineDao
    abstract fun pointDao(): PointDao
    abstract fun mergedTrackPlanDao(): MergedTrackPlanDao
    abstract fun achievementDao(): AchievementDao
    abstract fun visitedPlaceDao(): VisitedPlaceDao

    companion object {
        private const val DATABASE_NAME = "markmap_database"

        /**
         * v1 -> v2：
         * 行程补充描述/起止日期/封面；路段补充交通方式/颜色/起止时间；
         * 地点补充速度/精度/省市信息；新增合并轨迹方案表。
         * 全部为增量变更，不破坏用户已有数据。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE groups ADD COLUMN description TEXT")
                db.execSQL("ALTER TABLE groups ADD COLUMN startDate INTEGER")
                db.execSQL("ALTER TABLE groups ADD COLUMN endDate INTEGER")
                db.execSQL("ALTER TABLE groups ADD COLUMN coverUri TEXT")

                db.execSQL("ALTER TABLE lines ADD COLUMN transportMode TEXT NOT NULL DEFAULT 'OTHER'")
                db.execSQL("ALTER TABLE lines ADD COLUMN color INTEGER")
                db.execSQL("ALTER TABLE lines ADD COLUMN startTime INTEGER")
                db.execSQL("ALTER TABLE lines ADD COLUMN endTime INTEGER")

                db.execSQL("ALTER TABLE points ADD COLUMN speed REAL")
                db.execSQL("ALTER TABLE points ADD COLUMN accuracy REAL")
                db.execSQL("ALTER TABLE points ADD COLUMN province TEXT")
                db.execSQL("ALTER TABLE points ADD COLUMN city TEXT")
                db.execSQL("ALTER TABLE points ADD COLUMN district TEXT")
                db.execSQL("ALTER TABLE points ADD COLUMN adCode TEXT")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS merged_track_plans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        groupId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        configJson TEXT NOT NULL,
                        createTime INTEGER NOT NULL,
                        modifyTime INTEGER NOT NULL,
                        FOREIGN KEY(groupId) REFERENCES groups(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_merged_track_plans_groupId " +
                        "ON merged_track_plans(groupId)"
                )
            }
        }

        /** v2 -> v3：新增成就表与足迹点亮表。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS achievements (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        code TEXT NOT NULL,
                        unlockedAt INTEGER,
                        progress INTEGER NOT NULL,
                        meta TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_achievements_code ON achievements(code)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS visited_places (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        code TEXT NOT NULL,
                        level INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        firstVisit INTEGER NOT NULL,
                        lastVisit INTEGER NOT NULL,
                        visitCount INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_visited_places_code_level ON visited_places(code, level)")
            }
        }

        /** v3 -> v4：路段新增参考轨迹标记；地点新增标签/收藏/照片。 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lines ADD COLUMN isReference INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE points ADD COLUMN tags TEXT")
                db.execSQL("ALTER TABLE points ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE points ADD COLUMN photoUri TEXT")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
