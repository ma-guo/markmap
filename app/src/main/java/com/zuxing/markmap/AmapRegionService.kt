package com.zuxing.markmap

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 高德行政区划查询（经 notify.gyzuxing.com 代理）。
 */
interface AmapRegionService {
    @GET("/api/amap/region_search/")
    fun regionSearch(
        @Query("keywords") keywords: String,
        @Query("subdistrict") subdistrict: Int,
        @Query("extensions") extensions: String,
        @Query("page") page: Int = 1,
        @Query("offset") offset: Int = 20
    ): Call<AmapRegionResponse>

    @GET("/api/amap/region_search_batch/")
    fun regionSearchBatch(
        @Query("keywords") keywords: List<String>,
        @Query("subdistrict") subdistrict: Int,
        @Query("extensions") extensions: String
    ): Call<AmapRegionResponse>
}

data class AmapRegionResponse(val data: AmapRegionData?)

data class AmapRegionData(
    val status: String?,
    val info: String?,
    val infocode: String?,
    val count: String?,
    val districts: List<AmapDistrictDto>?
)

data class AmapDistrictDto(
    val keyword: String?,
    val citycode: String?,
    val adcode: String?,
    val name: String?,
    val center: String?,
    val level: String?,
    val polyline: String?,
    val districts: List<AmapDistrictDto>?
)
