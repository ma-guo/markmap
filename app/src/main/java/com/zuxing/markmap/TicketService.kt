package com.zuxing.markmap

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface TicketService {

    /**
     * 余票查询路径会随 12306 改版轮换（queryA/queryB/.../queryG）。
     * 这里固定入口只作兜底，实际调用走 [queryTicketsAuto]，会自动跟随 c_url。
     */
    @GET("/otn/leftTicket/queryG")
    fun queryTickets(
        @Query("leftTicketDTO.train_date") trainDate: String,
        @Query("leftTicketDTO.from_station") fromStation: String,
        @Query("leftTicketDTO.to_station") toStation: String,
        @Query("purpose_codes") purposeCodes: String = "ADULT",
    ): Call<TicketQueryResponse>

    @GET
    fun queryTicketsAt(
        @Url url: String,
        @Query("leftTicketDTO.train_date") trainDate: String,
        @Query("leftTicketDTO.from_station") fromStation: String,
        @Query("leftTicketDTO.to_station") toStation: String,
        @Query("purpose_codes") purposeCodes: String = "ADULT",
    ): Call<TicketQueryResponse>
}

/**
 * 记住可用的余票查询路径，避免每次查询都要先试错一轮。
 */
object LeftTicketPath {

    const val BASE = "https://kyfw.12306.cn/otn/"

    private const val DEFAULT = "leftTicket/queryG"

    @Volatile
    private var current: String = DEFAULT

    fun candidates(): List<String> {
        val fallback = listOf(
            DEFAULT,
            "leftTicket/queryB",
            "leftTicket/queryZ",
            "leftTicket/queryO",
            "leftTicket/queryA",
            "leftTicket/queryC",
            "leftTicket/queryD",
            "leftTicket/queryE",
        )
        return (listOf(current) + fallback).distinct()
    }

    fun remember(raw: String) {
        val path = normalized(raw)
        if (path.isNotEmpty()) current = path
    }

    /** 把 `leftTicket/queryG`、`otn/leftTicket/queryG`、完整 URL 统一成 `leftTicket/queryG`。 */
    fun normalized(raw: String): String {
        var path = raw.trim()
        if (path.isEmpty()) return ""
        path = path.removePrefix("https://kyfw.12306.cn/").removePrefix("/")
        path = path.removePrefix("otn/")
        return if (path.startsWith("leftTicket/")) path else ""
    }
}

/**
 * 带路径自动跟随的站站（出发站 -> 到达站）余票查询。
 * 12306 在旧路径上会返回 `status=false` 和新的 `c_url`，这里据此切换。
 */
fun TicketService.queryTicketsAuto(
    trainDate: String,
    fromStation: String,
    toStation: String,
): TicketQueryResponse? {
    var last: TicketQueryResponse? = null

    for (path in LeftTicketPath.candidates()) {
        val response = try {
            queryTicketsAt(
                LeftTicketPath.BASE + path, trainDate, fromStation, toStation
            ).execute().takeIf { it.isSuccessful }?.body()
        } catch (e: Exception) {
            Logger.e("余票查询失败: $fromStation-$toStation @$path ${e.localizedMessage}", e)
            null
        } ?: continue

        last = response
        if (response.status == true) {
            LeftTicketPath.remember(path)
            return response
        }

        // 命中 12306 的路径轮换提示，直接按 c_url 重试一次
        val redirect = response.cUrl
        if (!redirect.isNullOrBlank()) {
            LeftTicketPath.remember(redirect)
            val retried = try {
                queryTicketsAt(
                    LeftTicketPath.BASE + LeftTicketPath.normalized(redirect),
                    trainDate, fromStation, toStation
                ).execute().takeIf { it.isSuccessful }?.body()
            } catch (e: Exception) {
                null
            }
            if (retried?.status == true) return retried
        }
    }

    return last
}

/**
 * 12306 余票查询依赖 init 页面下发的会话 Cookie，没有 Cookie 时接口会返回空响应。
 * 整个进程只需要预热一次，之后复用同一个带 CookieJar 的 OkHttpClient。
 */
object TicketSession {

    @Volatile
    private var warmedUp = false

    fun warmUp(client: okhttp3.OkHttpClient) {
        if (warmedUp) return
        synchronized(this) {
            if (warmedUp) return
            runCatching {
                val request = okhttp3.Request.Builder()
                    .url("https://kyfw.12306.cn/otn/leftTicket/init")
                    .build()
                client.newCall(request).execute().use { it.body?.string() }
                warmedUp = true
            }
        }
    }
}

data class TicketQueryResponse(
    val httpstatus: Int = 0,
    val status: Boolean = false,
    val messages: String? = null,
    @com.google.gson.annotations.SerializedName("c_url") val cUrl: String? = null,
    val data: TicketQueryData? = null
)

data class TicketQueryData(
    val result: List<String>? = null,
    val flag: String? = null,
    val level: String? = null,
    val map: Map<String, String>? = null
)

data class TicketInfo(
    val trainNo: String,
    val stationTrainCode: String,
    val fromStation: String,
    val toStation: String,
    val fromTelecode: String,
    val toTelecode: String,
    val startTime: String,
    val arriveTime: String,
    val duration: String,
    val canBuy: Boolean,
    val seats: List<String>,
    val prices: Map<String, Int>,
    val date: String
)