package com.zuxing.markmap

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    ): Call<ResponseBody>

    @GET
    fun queryTicketsAt(
        @Url url: String,
        @Query("leftTicketDTO.train_date") trainDate: String,
        @Query("leftTicketDTO.from_station") fromStation: String,
        @Query("leftTicketDTO.to_station") toStation: String,
        @Query("purpose_codes") purposeCodes: String = "ADULT",
    ): Call<ResponseBody>
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
 * 余票查询日期窗口：今天及更早统一改为明天，超过 10 天统一改为第 10 天。
 * 入参支持 `yyyy-MM-dd` 和 `yyyyMMdd`，统一返回 `yyyy-MM-dd`。
 */
fun normalizeTicketQueryDate(raw: String): String {
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    val parsed = runCatching {
        when (raw.length) {
            8 -> LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE)
            else -> LocalDate.parse(raw, formatter)
        }
    }.getOrNull() ?: return raw

    val today = LocalDate.now()
    val earliest = today.plusDays(1)
    val latest = today.plusDays(10)
    val clamped = when {
        parsed.isBefore(earliest) -> earliest
        parsed.isAfter(latest) -> latest
        else -> parsed
    }
    return clamped.format(formatter)
}

private val ticketGson: Gson = GsonBuilder().setLenient().create()

/**
 * 12306 在会话失效时会 302 到错误页（HTML），旧代码的 lenient Gson 会把 HTML
 * 当成一个字符串，从而抛出 “Expected BEGIN_OBJECT but was STRING”。
 * 这里只接受以 `{` 开头的 JSON 对象，其它内容（HTML/空响应）一律视为无效响应。
 */
private fun parseTicketQuery(body: String?): TicketQueryResponse? {
    val text = body?.trim().orEmpty()
    if (!text.startsWith("{")) return null
    return runCatching {
        val element = JsonParser.parseString(text)
        if (!element.isJsonObject) {
            null
        } else {
            ticketGson.fromJson(element, TicketQueryResponse::class.java)
        }
    }.getOrNull()
}

private fun TicketService.fetchTicketBody(
    url: String,
    trainDate: String,
    fromStation: String,
    toStation: String,
): String? {
    return try {
        queryTicketsAt(url, trainDate, fromStation, toStation).execute().let { response ->
            if (!response.isSuccessful) null else response.body()?.use { it.string() }
        }
    } catch (e: Exception) {
        Logger.e("余票查询失败: $fromStation-$toStation @$url ${e.localizedMessage}", e)
        null
    }
}

/**
 * 带路径自动跟随的站站（出发站 -> 到达站）余票查询。
 * 12306 在旧路径上会返回 `status=false` 和新的 `c_url`，这里据此切换。
 *
 * 另外统一修正查询日期：今天/过去改到明天，超过 10 天改到第 10 天。
 */
fun TicketService.queryTicketsAuto(
    trainDate: String,
    fromStation: String,
    toStation: String,
): TicketQueryResponse? {
    val queryDate = normalizeTicketQueryDate(trainDate)
    queryTicketsPass(queryDate, fromStation, toStation)?.let { return it }

    // 全部路径都拿不到合法 JSON：会话可能已失效，重新预热 Cookie 后再试一轮
    TicketSession.refresh()
    return queryTicketsPass(queryDate, fromStation, toStation)
}

private fun TicketService.queryTicketsPass(
    trainDate: String,
    fromStation: String,
    toStation: String,
): TicketQueryResponse? {
    var last: TicketQueryResponse? = null

    for (path in LeftTicketPath.candidates()) {
        val body = fetchTicketBody(
            LeftTicketPath.BASE + path, trainDate, fromStation, toStation
        ) ?: continue
        val response = parseTicketQuery(body) ?: continue

        last = response
        if (response.status == true) {
            LeftTicketPath.remember(path)
            return response
        }

        // 命中 12306 的路径轮换提示，直接按 c_url 重试一次
        val redirect = response.cUrl
        if (!redirect.isNullOrBlank()) {
            val redirectPath = LeftTicketPath.normalized(redirect)
            if (redirectPath.isEmpty()) continue
            LeftTicketPath.remember(redirectPath)
            val retriedBody = fetchTicketBody(
                LeftTicketPath.BASE + redirectPath,
                trainDate, fromStation, toStation
            ) ?: continue
            val retried = parseTicketQuery(retriedBody)
            if (retried?.status == true) return retried
            last = retried ?: last
        }
    }

    return last
}

/**
 * 12306 余票查询依赖 init 页面下发的会话 Cookie，没有 Cookie 时接口会 302 到错误页。
 * 整个进程只需要预热一次，之后复用同一个带 CookieJar 的 OkHttpClient。
 */
object TicketSession {

    private const val INIT_URL = "https://kyfw.12306.cn/otn/leftTicket/init"

    @Volatile
    private var warmedUp = false

    @Volatile
    private var client: okhttp3.OkHttpClient? = null

    fun warmUp(client: okhttp3.OkHttpClient) {
        this.client = client
        ensureWarmedUp(client)
    }

    /** 会话失效时强制重新访问 init 页面刷新 Cookie。 */
    fun refresh() {
        val current = client ?: return
        synchronized(this) { warmedUp = false }
        ensureWarmedUp(current)
    }

    private fun ensureWarmedUp(client: okhttp3.OkHttpClient) {
        if (warmedUp) return
        synchronized(this) {
            if (warmedUp) return
            val ok = runCatching {
                val request = okhttp3.Request.Builder().url(INIT_URL).build()
                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            }.getOrDefault(false)
            if (ok) warmedUp = true
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
    /** 2026 版接口新增字段：同城车站标记（N/Y）。 */
    val sametlc: String? = null,
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