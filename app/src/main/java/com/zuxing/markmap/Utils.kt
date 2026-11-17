package com.zuxing.markmap

import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.utils.CoordinateConverter
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Utils {
    /**
     * GCJ-02（12306/高德坐标）转百度 BD-09
     */
    fun gcj02ToBd09(lat: Double, lng: Double): LatLng {
        val source = LatLng(lat, lng)
        val converter = CoordinateConverter()
        converter.from(CoordinateConverter.CoordType.COMMON)
        converter.coord(source)
        return converter.convert()
    }

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}