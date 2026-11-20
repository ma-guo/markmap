package com.zuxing.markmap.domain

/**
 * 解析百度行政区边界 WKT（BD09），形如：
 * MULTIPOLYGON (((lng lat, lng lat, ...)), ((...)))
 * POLYGON ((lng lat, ...))
 */
object RegionWkt {

    private val ringRegex = Regex("\\(([^()]+)\\)")

    /** 高德边界：lng,lat;lng,lat|... （| 分隔多环）。 */
    fun parseAmap(polyline: String?): List<List<Pair<Double, Double>>> {
        if (polyline.isNullOrBlank()) return emptyList()
        val rings = ArrayList<List<Pair<Double, Double>>>()
        polyline.split("|").forEach { part ->
            val ring = ArrayList<Pair<Double, Double>>()
            part.split(";").forEach { pt ->
                val p = pt.split(",")
                if (p.size >= 2) {
                    val lng = p[0].trim().toDoubleOrNull()
                    val lat = p[1].trim().toDoubleOrNull()
                    if (lng != null && lat != null) ring.add(lat to lng)
                }
            }
            if (ring.size >= 3) rings.add(ring)
        }
        return rings
    }

    fun parse(wkt: String?): List<List<Pair<Double, Double>>> {
        if (wkt.isNullOrBlank()) return emptyList()
        val rings = ArrayList<List<Pair<Double, Double>>>()
        for (m in ringRegex.findAll(wkt)) {
            val coords = m.groupValues[1].split(",")
            val ring = ArrayList<Pair<Double, Double>>(coords.size)
            for (c in coords) {
                val parts = c.trim().split(Regex("\\s+"))
                if (parts.size < 2) continue
                val lng = parts[0].toDoubleOrNull() ?: continue
                val lat = parts[1].toDoubleOrNull() ?: continue
                ring.add(lat to lng)
            }
            if (ring.size >= 3) rings.add(ring)
        }
        return rings
    }
}
