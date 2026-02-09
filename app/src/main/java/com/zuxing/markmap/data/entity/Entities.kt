package com.zuxing.markmap.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 组表实体
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
    val modifyTime: Long = System.currentTimeMillis()
)

/**
 * 线表实体
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
    val sortOrder: Int = 0
)

/**
 * 点表实体
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
    val modifyTime: Long = System.currentTimeMillis()
)