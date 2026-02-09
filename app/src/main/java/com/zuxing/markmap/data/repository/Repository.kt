package com.zuxing.markmap.data.repository

import com.zuxing.markmap.data.dao.GroupDao
import com.zuxing.markmap.data.dao.LineDao
import com.zuxing.markmap.data.dao.PointDao
import com.zuxing.markmap.data.entity.GroupEntity
import com.zuxing.markmap.data.entity.LineEntity
import com.zuxing.markmap.data.entity.PointEntity
import kotlinx.coroutines.flow.Flow

/**
 * 数据仓储类，封装数据库操作
 */
class Repository(
    private val groupDao: GroupDao,
    private val lineDao: LineDao,
    private val pointDao: PointDao
) {
    // ===== 组操作 =====

    fun getAllGroups(): Flow<List<GroupEntity>> = groupDao.getAllGroups()

    fun getDeletedGroups(): Flow<List<GroupEntity>> = groupDao.getDeletedGroups()

    suspend fun getGroupById(id: Long): GroupEntity? = groupDao.getGroupById(id)

    suspend fun insertGroup(group: GroupEntity): Long = groupDao.insert(group)

    suspend fun updateGroup(group: GroupEntity) = groupDao.update(group)

    suspend fun softDeleteGroup(id: Long) = groupDao.softDelete(id)

    suspend fun restoreGroup(id: Long) = groupDao.restore(id)

    suspend fun deleteGroup(group: GroupEntity) = groupDao.softDelete(group.id)

    suspend fun deleteGroupById(id: Long) = groupDao.deleteById(id)

    suspend fun getGroupCount(): Int = groupDao.getGroupCount()

    // ===== 线操作 =====

    fun getLinesByGroupId(groupId: Long): Flow<List<LineEntity>> =
        lineDao.getLinesByGroupId(groupId)

    fun getAllLines(): Flow<List<LineEntity>> = lineDao.getAllLines()

    fun getDeletedLines(): Flow<List<LineEntity>> = lineDao.getDeletedLines()

    suspend fun getLineById(id: Long): LineEntity? = lineDao.getLineById(id)

    suspend fun insertLine(line: LineEntity): Long = lineDao.insert(line)

    suspend fun updateLine(line: LineEntity) = lineDao.update(line)

    suspend fun softDeleteLine(id: Long) = lineDao.softDelete(id)

    suspend fun restoreLine(id: Long) = lineDao.restore(id)

    suspend fun deleteLine(line: LineEntity) = lineDao.softDelete(line.id)

    suspend fun deleteLineById(id: Long) = lineDao.deleteById(id)

    suspend fun getLineCountByGroup(groupId: Long): Int = lineDao.getLineCountByGroup(groupId)

    // ===== 点操作 =====

    fun getPointsByLineId(lineId: Long): Flow<List<PointEntity>> =
        pointDao.getPointsByLineId(lineId)

    fun getAllPoints(): Flow<List<PointEntity>> = pointDao.getAllPoints()

    fun getDeletedPoints(): Flow<List<PointEntity>> = pointDao.getDeletedPoints()

    suspend fun getPointById(id: Long): PointEntity? = pointDao.getPointById(id)

    suspend fun insertPoint(point: PointEntity): Long = pointDao.insert(point)

    suspend fun insertPoints(points: List<PointEntity>) = pointDao.insertAll(points)

    suspend fun updatePoint(point: PointEntity) = pointDao.update(point)

    suspend fun softDeletePoint(id: Long) = pointDao.softDelete(id)

    suspend fun restorePoint(id: Long) = pointDao.restore(id)

    suspend fun deletePoint(point: PointEntity) = pointDao.softDelete(point.id)

    suspend fun deletePointById(id: Long) = pointDao.deleteById(id)

    suspend fun getPointCountByLine(lineId: Long): Int = pointDao.getPointCountByLine(lineId)
}