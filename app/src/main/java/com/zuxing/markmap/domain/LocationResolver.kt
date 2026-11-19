package com.zuxing.markmap.domain

import kotlin.math.cos
import kotlin.math.sqrt

/**
 * 城市/省份识别（A+B+C+E 组合）：
 * A 后端结构化（未接入，字段预留）→ B BDLocation 字段 → C 地址文本解析 → E 最近城市质心兜底。
 */
object LocationResolver {

    data class Place(val province: String?, val city: String?)

    private const val NEAREST_CITY_MAX_METERS = 80_000.0

    fun normProvince(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()
        return ChinaRegions.provinces.firstOrNull { s.startsWith(it) || s.contains(it) }
    }

    fun normCity(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()
        return ChinaRegions.cities.firstOrNull { s.startsWith(it.name) || s.contains(it.name) }?.name
    }

    fun provinceFromText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return ChinaRegions.provinces.firstOrNull { text.contains(it) }
    }

    fun cityFromText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return ChinaRegions.cities.firstOrNull { text.contains(it.name) }?.name
    }

    fun nearestCity(lat: Double, lng: Double): CityInfo? {
        val nearest = ChinaRegions.cities.minByOrNull { distance(it.lat, it.lng, lat, lng) } ?: return null
        return if (distance(nearest.lat, nearest.lng, lat, lng) <= NEAREST_CITY_MAX_METERS) nearest else null
    }

    fun resolve(
        provinceField: String?,
        cityField: String?,
        address: String?,
        lat: Double,
        lng: Double
    ): Place {
        var province = normProvince(provinceField)
        var city = normCity(cityField)

        if (province == null) province = provinceFromText(address)
        if (city == null) city = cityFromText(address)

        if (city == null) {
            nearestCity(lat, lng)?.let { city = it.name }
        }
        if (province == null && city != null) province = ChinaRegions.provinceOfCity[city]

        return Place(province, city)
    }

    private fun distance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        return r * 2 * kotlin.math.atan2(sqrt(a), sqrt(1 - a))
    }
}
