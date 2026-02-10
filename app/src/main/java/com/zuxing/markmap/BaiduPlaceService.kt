package com.zuxing.markmap

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * [全球逆地理编码](https://lbs.baidu.com/faq/api?title=webapi/guide/webservice-geocoding-abroad-base)
 *
 * 全球逆地理编码服务提供将坐标点（经纬度）转换为对应位置信息（如所在行政区划，周边地标点分布）功能。
 */
interface BaiduPlaceService {
    @GET("/api/baidu/reverse_geocoding/")
    fun reverseGeocoding(
        @Query("latitude") latitude: String,
        @Query("longitude") longitude: String,
    ): Call<ReverseGeocodingResponse>
}

data class ReverseGeocodingResponse(
    val result: Int,
    val message: String?,
    val data: GeocodingResult?
)

data class GeocodingResult(
    val address: String?,
    val description: String?,
)