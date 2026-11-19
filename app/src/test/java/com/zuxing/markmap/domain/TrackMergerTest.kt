package com.zuxing.markmap.domain

import com.zuxing.markmap.data.entity.PointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMergerTest {

    private fun point(id: Long, lineId: Long, lat: Double, lng: Double, time: Long = 0L) =
        PointEntity(
            id = id,
            lineId = lineId,
            latitude = lat,
            longitude = lng,
            createTime = time
        )

    private fun segment(id: Long, points: List<PointEntity>) =
        TrackMerger.SegmentInput(
            id = id,
            name = "段$id",
            transportMode = "TRAIN",
            points = points
        )

    @Test
    fun `preserves user order and adds dashed connector distance`() {
        val a = segment(1, listOf(point(1, 1, 30.0, 120.0), point(2, 1, 30.1, 120.0)))
        val b = segment(2, listOf(point(3, 2, 31.0, 121.0), point(4, 2, 31.1, 121.0)))

        val result = TrackMerger.merge(listOf(a, b), includeConnectors = true)

        assertEquals(4, result.points.size)
        assertEquals(listOf(1L, 2L), result.ranges.map { it.segmentId })
        assertEquals(1, result.connectors.size)
        assertTrue(result.connectorDistanceMeters > 0)
        assertEquals(
            result.recordedDistanceMeters + result.connectorDistanceMeters,
            result.totalDistanceMeters,
            0.001
        )
    }

    @Test
    fun `reversed segment swaps endpoints`() {
        val a = segment(1, listOf(point(1, 1, 30.0, 120.0), point(2, 1, 30.1, 120.0)))
            .copy(reversed = true)

        val result = TrackMerger.merge(listOf(a), includeConnectors = false)

        assertEquals(30.1, result.points.first().latitude, 0.0001)
        assertEquals(30.0, result.points.last().latitude, 0.0001)
        assertEquals(1, result.ranges.size)
        assertTrue(result.ranges.first().reversed)
    }

    @Test
    fun `skips segment with fewer than two points`() {
        val a = segment(1, listOf(point(1, 1, 30.0, 120.0)))
        val b = segment(2, listOf(point(2, 2, 31.0, 121.0), point(3, 2, 31.1, 121.0)))

        val result = TrackMerger.merge(listOf(a, b), includeConnectors = true)

        assertEquals(listOf(1L), result.skippedSegmentIds)
        assertEquals(listOf(2L), result.ranges.map { it.segmentId })
        assertEquals(0, result.connectors.size)
    }

    @Test
    fun `dedupes shared endpoint without dirties connector`() {
        val a = segment(1, listOf(point(1, 1, 30.0, 120.0), point(2, 1, 30.1, 120.0)))
        val b = segment(2, listOf(point(3, 2, 30.1, 120.0), point(4, 2, 30.2, 120.0)))

        val result = TrackMerger.merge(listOf(a, b), includeConnectors = true)

        assertEquals(3, result.points.size)
        assertEquals(0, result.connectors.size)
        assertEquals(0.0, result.connectorDistanceMeters, 0.001)
    }

    @Test
    fun `config reorders and reverses segments`() {
        val a = segment(1, listOf(point(1, 1, 30.0, 120.0), point(2, 1, 30.1, 120.0)))
        val b = segment(2, listOf(point(3, 2, 31.0, 121.0), point(4, 2, 31.1, 121.0)))
        val config = MergeConfig(
            segments = listOf(
                MergeConfig.MergeSegmentConfig(segmentId = 2, reversed = true),
                MergeConfig.MergeSegmentConfig(segmentId = 1, reversed = false)
            ),
            includeConnectors = false
        )

        val ordered = config.order(listOf(a, b))

        assertEquals(listOf(2L, 1L), ordered.map { it.id })
        assertTrue(ordered.first().reversed)
        assertFalse(ordered.last().reversed)
    }

    @Test
    fun `config json round trip`() {
        val config = MergeConfig(
            segments = listOf(MergeConfig.MergeSegmentConfig(1, true)),
            includeConnectors = false
        )

        val restored = MergeConfig.fromJson(config.toJson())

        assertEquals(config, restored)
    }

    @Test
    fun `stats computes distance duration and altitude range`() {
        val points = listOf(
            point(1, 1, 30.0, 120.0, time = 1_000).copy(altitude = 10.0),
            point(2, 1, 30.1, 120.0, time = 61_000).copy(altitude = 50.0)
        )

        val stats = TrackStats.fromPoints(points)

        assertEquals(2, stats.pointCount)
        assertEquals(60_000L, stats.durationMillis)
        assertEquals(50.0, stats.maxAltitude!!, 0.01)
        assertEquals(10.0, stats.minAltitude!!, 0.01)
        assertTrue(stats.distanceMeters > 10_000)
    }
}
