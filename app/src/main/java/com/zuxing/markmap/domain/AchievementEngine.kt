package com.zuxing.markmap.domain

import com.zuxing.markmap.data.entity.AchievementEntity
import com.zuxing.markmap.data.repository.Repository
import kotlinx.coroutines.flow.first

/**
 * 本地成就系统：基于历史数据按需评估，解锁时间持久化且不回退。
 */
object AchievementEngine {

    enum class Category(val label: String) {
        GEO("地理"), TRANSPORT("交通"), MILEAGE("里程"), SPECIAL("特殊")
    }

    data class Def(
        val code: String,
        val title: String,
        val desc: String,
        val category: Category,
        val goal: Double = 1.0
    )

    val defs: List<Def> = listOf(
        Def("geo_first_province", "初见山河", "点亮第 1 个省份", Category.GEO),
        Def("geo_5_province", "行者初成", "点亮 5 个省份", Category.GEO, 5.0),
        Def("geo_10_province", "踏遍十省", "点亮 10 个省份", Category.GEO, 10.0),
        Def("geo_all_province", "遍览九州", "点亮全部 34 个省级行政区", Category.GEO, 34.0),
        Def("geo_first_cross", "越界", "首次跨省行程", Category.GEO),
        Def("geo_trip_5_province", "一日千里", "一次行程跨越 5 个省份", Category.GEO, 5.0),
        Def("trans_first_flight", "凌云", "首次飞行", Category.TRANSPORT),
        Def("trans_first_train", "铁道行", "首趟铁路行程", Category.TRANSPORT),
        Def("trans_first_drive", "自驾上路", "首次自驾", Category.TRANSPORT),
        Def("trans_first_ferry", "渡", "首次轮渡", Category.TRANSPORT),
        Def("mile_100", "百里挑一", "累计里程 100 km", Category.MILEAGE, 100.0),
        Def("mile_1000", "千里之行", "累计里程 1,000 km", Category.MILEAGE, 1000.0),
        Def("mile_10000", "万里长空", "累计里程 10,000 km", Category.MILEAGE, 10000.0),
        Def("special_max_alt", "云端之上", "海拔超过 3,000 m", Category.SPECIAL),
        Def("special_night_flight", "夜航", "搭乘夜班航班", Category.SPECIAL),
        Def("special_longest", "最长一程", "单段里程超过 1,500 km", Category.SPECIAL),
        Def("special_timezone", "经纬之间", "轨迹经度跨越 15°（约一个时区）", Category.SPECIAL, 15.0)
    )

    data class Stats(
        val flightCount: Int, val trainCount: Int, val driveCount: Int, val ferryCount: Int,
        val flightKm: Double, val railKm: Double, val totalKm: Double,
        val provinceCount: Int, val cityCount: Int,
        val maxAltitude: Double, val longestSingleKm: Double,
        val lngSpanDeg: Double, val hasNightFlight: Boolean,
        val maxProvincesInTrip: Int, val crossProvinceTrip: Boolean
    )

    suspend fun collectStats(repository: Repository): Stats {
        val lines = repository.getAllLines().first()
        var flightCount = 0; var trainCount = 0; var driveCount = 0; var ferryCount = 0
        var flightKm = 0.0; var railKm = 0.0; var totalKm = 0.0
        var maxAlt = 0.0; var longest = 0.0
        var minLng = Double.MAX_VALUE; var maxLng = -Double.MAX_VALUE
        var nightFlight = false

        data class LineStat(val groupId: Long, val mode: TransportMode, val km: Double, val provinces: Set<String>)
        val lineStats = mutableListOf<LineStat>()

        for (line in lines) {
            val points = repository.getPointsByLineIdOnce(line.id)
            val stat = TrackStats.fromPoints(points)
            val km = stat.distanceMeters / 1000.0
            totalKm += km
            if (km > longest) longest = km
            points.forEach { p ->
                p.latitude.let { }
                if (p.longitude < minLng) minLng = p.longitude
                if (p.longitude > maxLng) maxLng = p.longitude
                p.altitude?.let { if (it > maxAlt) maxAlt = it }
            }
            val mode = TransportMode.from(line.transportMode)
            when (mode) {
                TransportMode.FLIGHT -> { flightCount++; flightKm += km }
                TransportMode.TRAIN -> { trainCount++; railKm += km }
                TransportMode.DRIVE -> driveCount++
                TransportMode.FERRY -> ferryCount++
                else -> {}
            }
            if (mode == TransportMode.FLIGHT) {
                val hour = line.startTime?.let { hourOfDay(it) }
                if (hour != null && (hour >= 22 || hour <= 5)) nightFlight = true
            }
            val provinces = endpointProvinces(points)
            lineStats.add(LineStat(line.groupId, mode, km, provinces))
        }

        // 会话/行程级别：按 group 聚合
        val groupProvinces = lineStats.groupBy { it.groupId }.map { (_, ls) ->
            ls.flatMap { it.provinces }.toSet()
        }
        val maxProvincesInTrip = groupProvinces.maxOfOrNull { it.size } ?: 0
        val crossProvinceTrip = groupProvinces.any { it.size >= 2 }

        val allProvinces = lineStats.flatMap { it.provinces }.toSet()
        val allCities = mutableSetOf<String>()
        for (line in lines) {
            val pts = repository.getPointsByLineIdOnce(line.id)
            endpointPoints(pts).forEach { p ->
                LocationResolver.resolve(p.province, p.city, p.address, p.latitude, p.longitude)
                    .city?.let { allCities.add(it) }
            }
        }

        return Stats(
            flightCount = flightCount, trainCount = trainCount,
            driveCount = driveCount, ferryCount = ferryCount,
            flightKm = flightKm, railKm = railKm, totalKm = totalKm,
            provinceCount = allProvinces.size, cityCount = allCities.size,
            maxAltitude = maxAlt, longestSingleKm = longest,
            lngSpanDeg = if (maxLng > minLng) (maxLng - minLng) else 0.0,
            hasNightFlight = nightFlight,
            maxProvincesInTrip = maxProvincesInTrip,
            crossProvinceTrip = crossProvinceTrip
        )
    }

    suspend fun evaluate(repository: Repository): List<AchievementEntity> {
        val stats = collectStats(repository)
        val existing = repository.getAchievementsOnce().associateBy { it.code }
        val now = System.currentTimeMillis()
        val result = mutableListOf<AchievementEntity>()

        defs.forEach { def ->
            val (value, unlocked) = measure(def, stats)
            val prev = existing[def.code]
            val unlockedAt = when {
                prev?.unlockedAt != null -> prev.unlockedAt
                unlocked -> now
                else -> null
            }
            val progress = ((value / def.goal) * 100).coerceIn(0.0, 100.0).toInt()
            val entity = AchievementEntity(
                id = prev?.id ?: 0,
                code = def.code,
                unlockedAt = unlockedAt,
                progress = progress,
                meta = null
            )
            repository.upsertAchievement(entity)
            result.add(entity)
        }
        return result
    }

    private fun measure(def: Def, s: Stats): Pair<Double, Boolean> = when (def.code) {
        "geo_first_province" -> s.provinceCount.toDouble() to (s.provinceCount >= 1)
        "geo_5_province" -> s.provinceCount.toDouble() to (s.provinceCount >= 5)
        "geo_10_province" -> s.provinceCount.toDouble() to (s.provinceCount >= 10)
        "geo_all_province" -> s.provinceCount.toDouble() to (s.provinceCount >= 34)
        "geo_first_cross" -> (if (s.crossProvinceTrip) 1.0 else 0.0) to s.crossProvinceTrip
        "geo_trip_5_province" -> s.maxProvincesInTrip.toDouble() to (s.maxProvincesInTrip >= 5)
        "trans_first_flight" -> s.flightCount.toDouble() to (s.flightCount >= 1)
        "trans_first_train" -> s.trainCount.toDouble() to (s.trainCount >= 1)
        "trans_first_drive" -> s.driveCount.toDouble() to (s.driveCount >= 1)
        "trans_first_ferry" -> s.ferryCount.toDouble() to (s.ferryCount >= 1)
        "mile_100" -> s.totalKm to (s.totalKm >= 100)
        "mile_1000" -> s.totalKm to (s.totalKm >= 1000)
        "mile_10000" -> s.totalKm to (s.totalKm >= 10000)
        "special_max_alt" -> s.maxAltitude to (s.maxAltitude >= 3000)
        "special_night_flight" -> (if (s.hasNightFlight) 1.0 else 0.0) to s.hasNightFlight
        "special_longest" -> s.longestSingleKm to (s.longestSingleKm >= 1500)
        "special_timezone" -> s.lngSpanDeg to (s.lngSpanDeg >= 15)
        else -> 0.0 to false
    }

    private fun endpointProvinces(points: List<com.zuxing.markmap.data.entity.PointEntity>): Set<String> {
        val eps = endpointPoints(points)
        return eps.mapNotNull { p ->
            LocationResolver.resolve(p.province, p.city, p.address, p.latitude, p.longitude).province
        }.toSet()
    }

    private fun endpointPoints(points: List<com.zuxing.markmap.data.entity.PointEntity>)
        : List<com.zuxing.markmap.data.entity.PointEntity> {
        if (points.isEmpty()) return emptyList()
        val sorted = points.sortedBy { it.sortOrder }
        val first = sorted.first(); val last = sorted.last()
        return if (first.id == last.id) listOf(first) else listOf(first, last)
    }

    private fun hourOfDay(ms: Long): Int {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = ms
        return cal.get(java.util.Calendar.HOUR_OF_DAY)
    }
}
