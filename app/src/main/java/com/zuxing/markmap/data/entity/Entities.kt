package com.zuxing.markmap.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 行程表实体（原组表，产品语义为「行程」）
 */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val isDeleted: Boolean = false,
    val deleteTime: Long? = null,
    val createTime: Long = System.currentTimeMillis(),
    val modifyTime: Long = System.currentTimeMillis(),
    val description: String? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val coverUri: String? = null
)

/**
 * 路段表实体（原线表）
 */
@Entity(
    tableName = "lines",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["groupId"])]
)
data class LineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val groupId: Long,
    val isDeleted: Boolean = false,
    val deleteTime: Long? = null,
    val createTime: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    val transportMode: String = "OTHER",
    val color: Int? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val isReference: Boolean = false
)

/**
 * 地点（轨迹点）表实体
 */
@Entity(
    tableName = "points",
    foreignKeys = [
        ForeignKey(
            entity = LineEntity::class,
            parentColumns = ["id"],
            childColumns = ["lineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["lineId"])]
)
data class PointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val lineId: Long,
    val longitude: Double,
    val latitude: Double,
    val altitude: Double? = null,
    val address: String? = null,
    val description: String? = null,
    val isDeleted: Boolean = false,
    val deleteTime: Long? = null,
    val sortOrder: Int = 0,
    val createTime: Long = System.currentTimeMillis(),
    val modifyTime: Long = System.currentTimeMillis(),
    val speed: Float? = null,
    val accuracy: Float? = null,
    val province: String? = null,
    val city: String? = null,
    val district: String? = null,
    val adCode: String? = null,
    val tags: String? = null,
    val isFavorite: Boolean = false,
    val photoUri: String? = null
)

/**
 * 合并轨迹方案：同一行程下可保存多个合并视图，不修改原始路段。
 * configJson 记录路段顺序、方向、是否显示连接线等。
 */
@Entity(
    tableName = "merged_track_plans",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["groupId"])]
)
data class MergedTrackPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupId: Long,
    val name: String,
    val configJson: String,
    val createTime: Long = System.currentTimeMillis(),
    val modifyTime: Long = System.currentTimeMillis()
)

/**
 * 成就/徽章。code 唯一，unlockedAt 为空表示未解锁。
 */
@Entity(
    tableName = "achievements",
    indices = [Index(value = ["code"], unique = true)]
)
data class AchievementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val code: String,
    val unlockedAt: Long? = null,
    val progress: Int = 0,
    val meta: String? = null
)

/**
 * 足迹点亮（省/市两级）。level: 0=省，1=市；code 为行政区划码或名称。
 */
@Entity(
    tableName = "visited_places",
    indices = [Index(value = ["code", "level"], unique = true)]
)
data class VisitedPlaceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val code: String,
    val level: Int,
    val name: String,
    val firstVisit: Long,
    val lastVisit: Long,
    val visitCount: Int = 1
)
