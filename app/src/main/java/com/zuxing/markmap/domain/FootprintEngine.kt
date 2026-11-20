package com.zuxing.markmap.domain

import com.zuxing.markmap.data.entity.PointEntity
import com.zuxing.markmap.data.entity.VisitedPlaceEntity
import com.zuxing.markmap.data.repository.Repository
import kotlinx.coroutines.flow.first

/**
 * 足迹点亮引擎：仅点亮路段「端点城市」，同一城市多次只记一次，但累计次数与最近时间。
 * 结果持久化到 visited_places，可由历史点数据随时重建。
 */
object FootprintEngine {

    data class Coverage(
        val provinces: List<VisitedPlaceEntity>,
        val cities: List<VisitedPlaceEntity>
    ) {
        val provinceCount: Int get() = provinces.size
        val cityCount: Int get() = cities.size
    }

    /** 从历史数据重建足迹点亮（幂等）。返回最新的点亮列表。 */
    suspend fun rebuild(repository: Repository): List<VisitedPlaceEntity> {
        val accumulator = LinkedHashMap<String, VisitedPlaceEntity>()

        fun visit(code: String, level: Int, name: String, time: Long) {
            val key = "$level#$code"
            val existing = accumulator[key]
            if (existing == null) {
                accumulator[key] = VisitedPlaceEntity(
                    code = code, level = level, name = name,
                    firstVisit = time, lastVisit = time, visitCount = 1
                )
            } else {
                accumulator[key] = existing.copy(
                    firstVisit = minOf(existing.firstVisit, time),
                    lastVisit = maxOf(existing.lastVisit, time),
                    visitCount = existing.visitCount + 1
                )
            }
        }

        val lines = repository.getAllLines().first()
        for (line in lines) {
            val points = repository.getPointsByLineIdOnce(line.id)
            if (points.isEmpty()) continue
            val endpoints = endpointPoints(points)
            for (point in endpoints) {
                val place = LocationResolver.resolve(
                    point.province, point.city, point.address,
                    point.latitude, point.longitude
                )
                place.province?.let { visit(it, 0, it, point.createTime) }
                place.city?.let { city -> visit(city, 1, city, point.createTime) }
                point.district?.takeIf { it.isNotBlank() }?.let { d ->
                    visit(d.trim(), 2, d.trim(), point.createTime)
                }
            }
        }

        repository.clearVisitedPlaces()
        accumulator.values.forEach { repository.upsertVisitedPlace(it) }
        return accumulator.values.toList()
    }

    /** 端点：起点与终点（不足两点时取存在的点，去重）。 */
    private fun endpointPoints(points: List<PointEntity>): List<PointEntity> {
        val sorted = points.sortedBy { it.sortOrder }
        val first = sorted.first()
        val last = sorted.last()
        return if (first.id == last.id) listOf(first) else listOf(first, last)
    }

    fun coverage(places: List<VisitedPlaceEntity>): Coverage {
        val provinces = places.filter { it.level == 0 }
        val cities = places.filter { it.level == 1 }
        return Coverage(provinces, cities)
    }
}
