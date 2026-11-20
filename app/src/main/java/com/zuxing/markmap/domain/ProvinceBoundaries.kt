package com.zuxing.markmap.domain

import android.content.Context
import org.json.JSONObject

/**
 * 内置简化省界：assets/china_provinces.json（GeoJSON, GCJ-02）。
 * 返回经纬度对（lat, lng，GCJ-02），由调用方转成对应地图 SDK 的坐标类型。
 */
object ProvinceBoundaries {

    /** rings: 每个环为 (lat, lng) 列表。 */
    data class Boundary(val name: String, val rings: List<List<Pair<Double, Double>>>)

    @Volatile
    private var cache: Map<String, Boundary>? = null

    fun load(context: Context): Map<String, Boundary> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val result = LinkedHashMap<String, Boundary>()
            try {
                val text = context.assets.open("china_provinces.json")
                    .bufferedReader().use { it.readText() }
                val root = JSONObject(text)
                val features = root.getJSONArray("features")
                for (i in 0 until features.length()) {
                    val feature = features.getJSONObject(i)
                    val fullName = feature.getJSONObject("properties").optString("name")
                    if (fullName.isBlank()) continue
                    val geometry = feature.getJSONObject("geometry")
                    val type = geometry.optString("type")
                    val coords = geometry.getJSONArray("coordinates")
                    val rings = ArrayList<List<Pair<Double, Double>>>()
                    when (type) {
                        "Polygon" -> rings.addAll(parsePolygon(coords))
                        "MultiPolygon" -> {
                            for (p in 0 until coords.length()) {
                                rings.addAll(parsePolygon(coords.getJSONArray(p)))
                            }
                        }
                    }
                    if (rings.isNotEmpty()) {
                        result[shortName(fullName)] = Boundary(fullName, rings)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            cache = result
            return result
        }
    }

    private fun parsePolygon(polygon: org.json.JSONArray): List<List<Pair<Double, Double>>> {
        val rings = ArrayList<List<Pair<Double, Double>>>()
        for (r in 0 until polygon.length()) {
            val ringArr = polygon.getJSONArray(r)
            val ring = ArrayList<Pair<Double, Double>>(ringArr.length())
            for (p in 0 until ringArr.length()) {
                val pt = ringArr.getJSONArray(p)
                val lng = pt.getDouble(0)
                val lat = pt.getDouble(1)
                ring.add(lat to lng)
            }
            if (ring.size >= 3) rings.add(ring)
        }
        return rings
    }

    private fun shortName(fullName: String): String =
        ChinaRegions.fullProvinceName.entries.firstOrNull { it.value == fullName }?.key ?: fullName
}
