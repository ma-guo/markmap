package com.zuxing.markmap.domain

import com.zuxing.markmap.data.entity.PointEntity
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 手动合并轨迹。
 *
 * 设计约定（与 PRD MR-1~16 一致）：
 * - 不设任何自动阈值，路段顺序与方向完全由用户决定；
 * - 合并结果是派生视图，不修改原始路段；
 * - 端点为「用户选择的顺序」中的端点，不自动按时间排序；
 * - 相邻路段端点不重合时，可显示虚线连接（可关闭）；
 * - 少于 2 个有效点的路段跳过。
 */
object TrackMerger {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /** 相邻点视为「同一点」的距离阈值，仅用于去重，不参与业务排序。 */
    const val DEDUPE_DISTANCE_METERS = 1.0

    data class SegmentInput(
        val id: Long,
        val name: String,
        val transportMode: String,
        val points: List<PointEntity>,
        val reversed: Boolean = false
    )

    data class MergedPoint(
        val latitude: Double,
        val longitude: Double,
        val segmentId: Long,
        val sourcePointId: Long,
        val time: Long = 0L
    )

    data class SegmentRange(
        val segmentId: Long,
        val name: String,
        val transportMode: String,
        val startIndex: Int,
        val endIndex: Int,
        val reversed: Boolean
    )

    data class Connector(
        val from: MergedPoint,
        val to: MergedPoint,
        val distanceMeters: Double
    )

    data class Result(
        val points: List<MergedPoint>,
        val ranges: List<SegmentRange>,
        val connectors: List<Connector>,
        val recordedDistanceMeters: Double,
        val connectorDistanceMeters: Double,
        val skippedSegmentIds: List<Long>
    ) {
        val totalDistanceMeters: Double
            get() = recordedDistanceMeters + connectorDistanceMeters
    }

    fun merge(segments: List<SegmentInput>, includeConnectors: Boolean): Result {
        val points = mutableListOf<MergedPoint>()
        val ranges = mutableListOf<SegmentRange>()
        val connectors = mutableListOf<Connector>()
        val skipped = mutableListOf<Long>()
        var recordedDistance = 0.0
        var connectorDistance = 0.0

        for (segment in segments) {
            val ordered = if (segment.reversed) segment.points.asReversed() else segment.points
            if (ordered.size < 2) {
                skipped.add(segment.id)
                continue
            }

            val originalSize = points.size
            var startIndex = originalSize
            var segmentDistance = 0.0
            var lastInSegment: MergedPoint? = null
            var addedCount = 0
            var firstPoint = true
            var sharedFirstPoint = false

            for (source in ordered) {
                val candidate = MergedPoint(
                    latitude = source.latitude,
                    longitude = source.longitude,
                    segmentId = segment.id,
                    sourcePointId = source.id,
                    time = source.createTime
                )
                val globalLast = points.lastOrNull()
                if (globalLast != null &&
                    distanceMeters(
                        globalLast.latitude, globalLast.longitude,
                        candidate.latitude, candidate.longitude
                    ) < DEDUPE_DISTANCE_METERS
                ) {
                    if (firstPoint && points.isNotEmpty()) {
                        startIndex = points.size - 1
                        sharedFirstPoint = true
                    }
                    firstPoint = false
                    continue
                }
                firstPoint = false
                if (lastInSegment != null) {
                    segmentDistance += distanceMeters(
                        lastInSegment.latitude, lastInSegment.longitude,
                        candidate.latitude, candidate.longitude
                    )
                }
                points.add(candidate)
                lastInSegment = candidate
                addedCount++
            }

            if (addedCount == 0 || points.size - startIndex < 2) {
                while (points.size > originalSize) {
                    points.removeAt(points.size - 1)
                }
                skipped.add(segment.id)
                continue
            }

            recordedDistance += segmentDistance
            ranges.add(
                SegmentRange(
                    segmentId = segment.id,
                    name = segment.name,
                    transportMode = segment.transportMode,
                    startIndex = startIndex,
                    endIndex = points.size - 1,
                    reversed = segment.reversed
                )
            )

            if (includeConnectors && ranges.size > 1 && !sharedFirstPoint) {
                val from = points[startIndex - 1]
                val to = points[startIndex]
                val distance = distanceMeters(from.latitude, from.longitude, to.latitude, to.longitude)
                if (distance >= DEDUPE_DISTANCE_METERS) {
                    connectors.add(Connector(from, to, distance))
                    connectorDistance += distance
                }
            }
        }

        return Result(
            points = points,
            ranges = ranges,
            connectors = connectors,
            recordedDistanceMeters = recordedDistance,
            connectorDistanceMeters = connectorDistance,
            skippedSegmentIds = skipped
        )
    }

    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }
}
