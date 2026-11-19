package com.zuxing.markmap.domain

import com.zuxing.markmap.data.entity.PointEntity
import kotlin.math.max
import kotlin.math.min

/**
 * 高度/速度剖面与统计（单段 / 整行程两级通用）。
 */
object ProfileBuilder {

    data class Sample(
        val km: Double,
        val altitude: Double?,
        val speedKmh: Double?,
        val time: Long
    )

    data class Profile(
        val samples: List<Sample>,
        val totalKm: Double,
        val durationMs: Long,
        val avgSpeedKmh: Double,
        val maxSpeedKmh: Double,
        val maxAltitude: Double?,
        val minAltitude: Double?,
        val ascent: Double,
        val descent: Double
    )

    fun build(points: List<PointEntity>): Profile {
        if (points.isEmpty()) {
            return Profile(emptyList(), 0.0, 0L, 0.0, 0.0, null, null, 0.0, 0.0)
        }
        val sorted = points.sortedWith(compareBy({ it.sortOrder }, { it.createTime }))

        val samples = ArrayList<Sample>(sorted.size)
        var cumulative = 0.0
        var ascent = 0.0
        var descent = 0.0
        var maxSpeed = 0.0
        var maxAlt: Double? = null
        var minAlt: Double? = null

        samples.add(
            Sample(0.0, sorted[0].altitude, speedOf(sorted[0], null), sorted[0].createTime)
        )

        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val cur = sorted[i]
            val d = TrackMerger.distanceMeters(prev.latitude, prev.longitude, cur.latitude, cur.longitude)
            cumulative += d

            val altDelta = if (prev.altitude != null && cur.altitude != null) cur.altitude!! - prev.altitude!! else 0.0
            if (altDelta > 0) ascent += altDelta else descent += -altDelta

            cur.altitude?.let {
                maxAlt = if (maxAlt == null) it else max(maxAlt!!, it)
                minAlt = if (minAlt == null) it else min(minAlt!!, it)
            }
            prev.altitude?.let {
                maxAlt = if (maxAlt == null) it else max(maxAlt!!, it)
                minAlt = if (minAlt == null) it else min(minAlt!!, it)
            }

            val speed = speedOf(cur, prev)
            speed?.let { maxSpeed = max(maxSpeed, it) }
            samples.add(Sample(cumulative / 1000.0, cur.altitude, speed, cur.createTime))
        }

        val times = sorted.map { it.createTime }
        val duration = (times.maxOrNull() ?: 0L) - (times.minOrNull() ?: 0L)
        val avg = if (duration > 0) (cumulative / 1000.0) / (duration / 3_600_000.0) else 0.0

        return Profile(
            samples = samples,
            totalKm = cumulative / 1000.0,
            durationMs = duration,
            avgSpeedKmh = avg,
            maxSpeedKmh = maxSpeed,
            maxAltitude = maxAlt,
            minAltitude = minAlt,
            ascent = ascent,
            descent = descent
        )
    }

    /** 速度 km/h：优先用记录速度(m/s)，否则按相邻点距离/时间推算。 */
    private fun speedOf(cur: PointEntity, prev: PointEntity?): Double? {
        cur.speed?.let { s -> if (s > 0f) return s * 3.6 }
        if (prev == null) return null
        val dt = cur.createTime - prev.createTime
        if (dt <= 0) return null
        val d = TrackMerger.distanceMeters(prev.latitude, prev.longitude, cur.latitude, cur.longitude)
        val v = (d / (dt / 1000.0)) * 3.6
        return if (v.isFinite() && v >= 0) v else null
    }
}
