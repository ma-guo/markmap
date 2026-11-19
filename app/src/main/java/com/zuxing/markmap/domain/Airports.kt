package com.zuxing.markmap.domain

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Airport(val code: String, val city: String, val name: String, val lat: Double, val lng: Double) {
    val label: String get() = "$code $city"
}

object Airports {
    val all: List<Airport> = listOf(
        Airport("PEK", "北京", "首都国际机场", 40.0801, 116.5846),
        Airport("PKX", "北京", "大兴国际机场", 39.5098, 116.4105),
        Airport("SHA", "上海", "虹桥国际机场", 31.1979, 121.3363),
        Airport("PVG", "上海", "浦东国际机场", 31.1434, 121.8052),
        Airport("CAN", "广州", "白云国际机场", 23.3924, 113.2988),
        Airport("SZX", "深圳", "宝安国际机场", 22.6393, 113.8107),
        Airport("CTU", "成都", "双流国际机场", 30.5785, 103.9471),
        Airport("TFU", "成都", "天府国际机场", 30.3125, 104.4415),
        Airport("CKG", "重庆", "江北国际机场", 29.7192, 106.6417),
        Airport("HGH", "杭州", "萧山国际机场", 30.2295, 120.4344),
        Airport("NKG", "南京", "禄口国际机场", 31.7420, 118.8622),
        Airport("WUH", "武汉", "天河国际机场", 30.7838, 114.2081),
        Airport("XIY", "西安", "咸阳国际机场", 34.4471, 108.7516),
        Airport("KMG", "昆明", "长水国际机场", 25.1019, 102.9292),
        Airport("XMN", "厦门", "高崎国际机场", 24.5440, 118.1277),
        Airport("CSX", "长沙", "黄花国际机场", 28.1892, 113.2196),
        Airport("TAO", "青岛", "胶东国际机场", 36.3611, 120.0861),
        Airport("CGO", "郑州", "新郑国际机场", 34.5197, 113.8408),
        Airport("TSN", "天津", "滨海国际机场", 39.1244, 117.3462),
        Airport("HRB", "哈尔滨", "太平国际机场", 45.6234, 126.2500),
        Airport("SHE", "沈阳", "桃仙国际机场", 41.6398, 123.4832),
        Airport("DLC", "大连", "周水子国际机场", 38.9657, 121.5386),
        Airport("FOC", "福州", "长乐国际机场", 25.9351, 119.6633),
        Airport("HET", "呼和浩特", "白塔国际机场", 40.8514, 111.8241),
        Airport("TYN", "太原", "武宿国际机场", 37.7469, 112.6284),
        Airport("URC", "乌鲁木齐", "地窝堡国际机场", 43.9071, 87.4742),
        Airport("LXA", "拉萨", "贡嘎国际机场", 29.2978, 90.9119),
        Airport("LHW", "兰州", "中川国际机场", 36.5152, 103.6203),
        Airport("XNN", "西宁", "曹家堡国际机场", 36.5275, 102.0430),
        Airport("KWE", "贵阳", "龙洞堡国际机场", 26.5385, 106.8007),
        Airport("NNG", "南宁", "吴圩国际机场", 22.6083, 108.1722),
        Airport("HAK", "海口", "美兰国际机场", 19.9349, 110.4589),
        Airport("SYX", "三亚", "凤凰国际机场", 18.3029, 109.4123),
        Airport("SYX2", "桂林", "两江国际机场", 25.2181, 110.0392),
        Airport("HKG", "香港", "香港国际机场", 22.3080, 113.9185),
        Airport("MFM", "澳门", "澳门国际机场", 22.1496, 113.5915),
        Airport("TPE", "台北", "桃园国际机场", 25.0777, 121.2328),
        Airport("NRT", "东京", "成田国际机场", 35.7720, 140.3929),
        Airport("ICN", "首尔", "仁川国际机场", 37.4602, 126.4407),
        Airport("SIN", "新加坡", "樟宜机场", 1.3644, 103.9915),
        Airport("BKK", "曼谷", "素万那普机场", 13.6900, 100.7501)
    )

    fun byCode(code: String): Airport? = all.firstOrNull { it.code.equals(code, true) }

    fun search(q: String): List<Airport> {
        if (q.isBlank()) return all
        val key = q.trim().lowercase()
        return all.filter {
            it.code.lowercase().contains(key) ||
                it.city.contains(key) || it.name.contains(key)
        }
    }
}

object GreatCircle {
    /** 沿大圆插值 n 个点，返回 (lat,lng) 列表。 */
    fun interpolate(a: Airport, b: Airport, n: Int = 64): List<Pair<Double, Double>> {
        if (n < 2) return listOf(a.lat to a.lng, b.lat to b.lng)
        val lat1 = Math.toRadians(a.lat); val lng1 = Math.toRadians(a.lng)
        val lat2 = Math.toRadians(b.lat); val lng2 = Math.toRadians(b.lng)
        val d = 2 * asin(
            sqrt(
                sin((lat2 - lat1) / 2).let { it * it } +
                    cos(lat1) * cos(lat2) * sin((lng2 - lng1) / 2).let { it * it }
            )
        )
        if (d == 0.0) return listOf(a.lat to a.lng, b.lat to b.lng)
        val out = ArrayList<Pair<Double, Double>>(n)
        for (i in 0 until n) {
            val f = i.toDouble() / (n - 1)
            val A = sin((1 - f) * d) / sin(d)
            val B = sin(f * d) / sin(d)
            val x = A * cos(lat1) * cos(lng1) + B * cos(lat2) * cos(lng2)
            val y = A * cos(lat1) * sin(lng1) + B * cos(lat2) * sin(lng2)
            val z = A * sin(lat1) + B * sin(lat2)
            val lat = atan2(z, sqrt(x * x + y * y))
            val lng = atan2(y, x)
            out.add(Math.toDegrees(lat) to Math.toDegrees(lng))
        }
        return out
    }
}
