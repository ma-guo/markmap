package com.zuxing.markmap.data.dao

import androidx.room.*
import com.zuxing.markmap.data.entity.GroupEntity
import com.zuxing.markmap.data.entity.LineEntity
import com.zuxing.markmap.data.entity.PointEntity
import kotlinx.coroutines.flow.Flow

/**
 * 组表 DAO
 */
@Dao
interface GroupDao {
    @Query("SELECT * FROM groups WHERE isDeleted = 0 ORDER BY sortOrder ASC, modifyTime DESC")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE isDeleted = 1 ORDER BY deleteTime DESC")
    fun getDeletedGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun getGroupById(id: Long): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: GroupEntity): Long

    @Update
    suspend fun update(group: GroupEntity)

    @Query("UPDATE groups SET isDeleted = 1, deleteTime = :deleteTime WHERE id = :id")
    suspend fun softDelete(id: Long, deleteTime: Long = System.currentTimeMillis())

    @Query("UPDATE groups SET isDeleted = 0, deleteTime = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM groups WHERE isDeleted = 0")
    suspend fun getGroupCount(): Int
}

/**
 * 线表 DAO
 */
@Dao
interface LineDao {
    @Query("SELECT * FROM lines WHERE groupId = :groupId AND isDeleted = 0 ORDER BY sortOrder ASC")
    fun getLinesByGroupId(groupId: Long): Flow<List<LineEntity>>

    @Query("SELECT * FROM lines WHERE isDeleted = 0 ORDER BY sortOrder ASC")
    fun getAllLines(): Flow<List<LineEntity>>

    @Query("SELECT * FROM lines WHERE isDeleted = 1 ORDER BY deleteTime DESC")
    fun getDeletedLines(): Flow<List<LineEntity>>

    @Query("SELECT * FROM lines WHERE id = :id")
    suspend fun getLineById(id: Long): LineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(line: LineEntity): Long

    @Update
    suspend fun update(line: LineEntity)

    @Query("UPDATE lines SET isDeleted = 1, deleteTime = :deleteTime WHERE id = :id")
    suspend fun softDelete(id: Long, deleteTime: Long = System.currentTimeMillis())

    @Query("UPDATE lines SET isDeleted = 0, deleteTime = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM lines WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM lines WHERE groupId = :groupId AND isDeleted = 0")
    suspend fun getLineCountByGroup(groupId: Long): Int
}

/**
 * 点表 DAO
 */
@Dao
interface PointDao {
    @Query("SELECT * FROM points WHERE lineId = :lineId AND isDeleted = 0 ORDER BY sortOrder ASC")
    fun getPointsByLineId(lineId: Long): Flow<List<PointEntity>>

    @Query("SELECT * FROM points WHERE isDeleted = 0 ORDER BY createTime DESC")
    fun getAllPoints(): Flow<List<PointEntity>>

    @Query("SELECT * FROM points WHERE isDeleted = 1 ORDER BY deleteTime DESC")
    fun getDeletedPoints(): Flow<List<PointEntity>>

    @Query("SELECT * FROM points WHERE id = :id")
    suspend fun getPointById(id: Long): PointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: PointEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<PointEntity>)

    @Update
    suspend fun update(point: PointEntity)

    @Query("UPDATE points SET isDeleted = 1, deleteTime = :deleteTime WHERE id = :id")
    suspend fun softDelete(id: Long, deleteTime: Long = System.currentTimeMillis())

    @Query("UPDATE points SET isDeleted = 0, deleteTime = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM points WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM points WHERE lineId = :lineId AND isDeleted = 0")
    suspend fun getPointCountByLine(lineId: Long): Int
}