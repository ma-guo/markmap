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
/**
 * 百度行政区划查询（经 notify.gyzuxing.com 代理），返回 BD09 边界 WKT。
 */
interface BaiduRegionService {
    @GET("/api/baidu/region_search/")
    fun regionSearch(
        @Query("keyword") keyword: String,
        @Query("boundary") boundary: Int,
        @Query("sub_admin") subAdmin: Int
    ): Call<RegionResponse>
}

data class RegionResponse(val data: RegionData?)

data class RegionData(
    val status: Int,
    val message: String?,
    val data_version: String?,
    val result_size: Int?,
    val districts: List<BaiduDistrictDto>?
)

data class BaiduDistrictDto(
    val code: String?,
    val name: String?,
    val level: Int?,
    val polyline: String?,
    val districts: List<BaiduDistrictDto>?
)
