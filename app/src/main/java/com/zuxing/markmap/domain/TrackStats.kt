package com.zuxing.markmap.domain

import com.zuxing.markmap.data.entity.PointEntity

/**
 * 轨迹统计。纯计算，便于单元测试。
 */
object TrackStats {

    data class Stats(
        val distanceMeters: Double,
        val durationMillis: Long,
        val pointCount: Int,
        val maxAltitude: Double?,
        val minAltitude: Double?,
        val startTime: Long?,
        val endTime: Long?
    )

    fun fromPoints(points: List<PointEntity>): Stats {
        if (points.isEmpty()) {
            return Stats(0.0, 0L, 0, null, null, null, null)
        }
        var distance = 0.0
        for (i in 1 until points.size) {
            distance += TrackMerger.distanceMeters(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude
            )
        }
        val times = points.map { it.createTime }
        val start = times.minOrNull()
        val end = times.maxOrNull()
        val duration = if (start != null && end != null && end > start) end - start else 0L
        val altitudes = points.mapNotNull { it.altitude }
        return Stats(
            distanceMeters = distance,
            durationMillis = duration,
            pointCount = points.size,
            maxAltitude = altitudes.maxOrNull(),
            minAltitude = altitudes.minOrNull(),
            startTime = start,
            endTime = end
        )
    }
}
