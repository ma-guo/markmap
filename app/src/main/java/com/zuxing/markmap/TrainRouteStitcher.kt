package com.zuxing.markmap

import com.baidu.mapapi.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 车次走向拼接器。
 *
 * 12306 的 getTrainMapLine 只接受 trainNo，无法按区间查询。当某趟车的走向为空时，
 * 使用「经停站区间 + 站站余票查询（leftTicket，与 TicketQueryActivity 同一接口）」
 * 找出经过该区间的其他车次，再把它们的整条走向裁剪成区间段拼接起来：
 *
 *   1. 先尝试整段 A-E 的 donor 车次（同类型优先，如 Z 车找 Z/K/T）；
 *   2. 找不到时，在中间的枢纽站 C 处拆分，查 A-C / C-E 的 donor；
 *   3. 同类型车次都没有走向数据时，允许借用高铁/动车的走向，标记为示意段；
 *   4. 仍然找不到的区间退化为经停站之间的直线（approximate = true）。
 *
 * 返回的 RoutePart 按顺序排列，真实走向 approximate=false，示意/兜底 approximate=true。
 */
class TrainRouteStitcher(
    private val trainService: TrainService,
    private val ticketService: TicketService,
    private val httpClient: OkHttpClient,
    private val date: String,
    private val targetTrainCode: String,
    private val targetTrainNo: String,
) {

    data class Anchor(
        val name: String,
        val telecode: String?,
        /** 12306 停站数据里部分车站（如广州白云）没有经纬度，此时只用于拼接查询。 */
        val latLng: LatLng?,
    )

    data class RoutePart(
        val points: List<LatLng>,
        val approximate: Boolean,
    )

    private data class Candidate(
        val trainNo: String,
        val code: String,
    )

    private data class DonorRoute(
        val points: List<LatLng>,
        val approximate: Boolean,
    )

    private val lineCache = HashMap<String, List<LatLng>?>()
    private val odCache = HashMap<String, DonorRoute?>()
    private var ticketQueryBudget = MAX_TICKET_QUERIES
    private var mapLineBudget = MAX_MAP_LINE_FETCHES
    private val startTime = System.currentTimeMillis()

    suspend fun resolve(anchors: List<Anchor>): List<RoutePart> = withContext(Dispatchers.IO) {
        if (anchors.size < 2) return@withContext emptyList()
        // 二分要一直细到相邻站，预算按站点数放宽，避免长交路在中途提前退化成直线
        ticketQueryBudget = maxOf(MAX_TICKET_QUERIES, anchors.size * 2)
        mapLineBudget = maxOf(MAX_MAP_LINE_FETCHES, anchors.size * 2)
        TicketSession.warmUp(httpClient)
        resolveRange(anchors, 0, anchors.lastIndex)
    }

    /**
     * 只解析一个站间区间（如「福泉 -> 贵阳」）。
     *
     * 与 [resolve] 不同，即使只有两个站也会主动查询经过该区间的其他车次，
     * 优先复用它们的真实走向；实在找不到时才用两站直线兜底。
     */
    suspend fun resolveSegment(from: Anchor, to: Anchor): List<RoutePart> =
        withContext(Dispatchers.IO) {
            ticketQueryBudget = maxOf(MAX_TICKET_QUERIES, 24)
            mapLineBudget = maxOf(MAX_MAP_LINE_FETCHES, 24)
            TicketSession.warmUp(httpClient)

            val donor = findDonorRoute(from, to)
            if (donor != null) {
                listOf(RoutePart(donor.points, donor.approximate))
            } else {
                listOf(straightPart(from, to))
            }
        }

    private fun resolveRange(
        anchors: List<Anchor>,
        fromIndex: Int,
        toIndex: Int,
    ): List<RoutePart> {
        if (fromIndex >= toIndex) return emptyList()
        // 已经细到相邻两站，没有中间站可再拆分
        if (toIndex - fromIndex == 1) {
            return listOf(straightPart(anchors[fromIndex], anchors[toIndex]))
        }
        if (isTimeUp() || (ticketQueryBudget <= 0 && mapLineBudget <= 0)) {
            return straightChain(anchors, fromIndex, toIndex)
        }

        // 1. 整段有没有其他车次的真实走向
        findDonorRoute(anchors[fromIndex], anchors[toIndex])?.let {
            return listOf(RoutePart(it.points, it.approximate))
        }

        // 2. 在枢纽/中点尝试拆分，优先复用较长的一侧（如 H->W 已找到，就继续递归 A->H）
        for (mid in splitCandidates(anchors, fromIndex, toIndex)) {
            if (ticketQueryBudget <= 0 && mapLineBudget <= 0) break

            if (mid < toIndex - 1) {
                val suffix = findDonorRoute(anchors[mid], anchors[toIndex])
                if (suffix != null) {
                    return resolveRange(anchors, fromIndex, mid) +
                            RoutePart(suffix.points, suffix.approximate)
                }
            }

            if (mid > fromIndex + 1) {
                val prefix = findDonorRoute(anchors[fromIndex], anchors[mid])
                if (prefix != null) {
                    return listOf(RoutePart(prefix.points, prefix.approximate)) +
                            resolveRange(anchors, mid, toIndex)
                }
            }
        }

        // 3. 一整段都没有真实数据：严格从中点二分，继续递归到相邻站为止，
        //    而不是在这里直接退化成一条长直线。
        val mid = (fromIndex + toIndex) / 2
        return resolveRange(anchors, fromIndex, mid) +
                resolveRange(anchors, mid, toIndex)
    }

    private fun straightPart(from: Anchor, to: Anchor): RoutePart {
        val start = from.latLng
        val end = to.latLng
        if (start == null || end == null) return RoutePart(emptyList(), approximate = true)
        return RoutePart(listOf(start, end), approximate = true)
    }

    /**
     * 逐站直线兜底，保证折线经过每一个经停站，而不是跨站拉一条长直线。
     */
    private fun straightChain(anchors: List<Anchor>, fromIndex: Int, toIndex: Int): List<RoutePart> {
        return (fromIndex until toIndex).map { index ->
            straightPart(anchors[index], anchors[index + 1])
        }
    }

    /**
     * 区间拆分点：中点优先（保证严格二分），再补充评分高的枢纽站。
     */
    private fun splitCandidates(anchors: List<Anchor>, fromIndex: Int, toIndex: Int): List<Int> {
        val mid = (fromIndex + toIndex) / 2
        val hubs = (fromIndex + 1 until toIndex)
            .filter { it != mid && hubScore(anchors[it].name) > 0 }
            .sortedWith(
                compareByDescending<Int> { hubScore(anchors[it].name) }
                    .thenBy { it }
            )
            .take(MAX_SPLIT_CANDIDATES)

        return (listOf(mid) + hubs).distinct()
    }

    /**
     * 查询 A-B 区间是否有其他车次的真实走向，并裁剪出 A-B 段。
     *
     * 先找同类型车次（普速优先普速）；如果都没有走向数据，再退一步借用高铁/动车的
     * 走向，并标记为 approximate，由界面画成灰色虚线。
     */
    private fun findDonorRoute(from: Anchor, to: Anchor): DonorRoute? {
        val fromCode = from.telecode ?: return null
        val toCode = to.telecode ?: return null
        val key = "$fromCode-$toCode"
        if (odCache.containsKey(key)) return odCache[key]
        if (ticketQueryBudget <= 0 || isTimeUp()) return null

        ticketQueryBudget--
        val response = ticketService.queryTicketsAuto(formatTicketDate(date), fromCode, toCode)

        val results = response?.data?.result
        if (response?.status != true || results.isNullOrEmpty()) {
            odCache[key] = null
            return null
        }

        val sameCategory = parseCandidates(results)
        val sameCategoryResult = firstSliced(sameCategory, from, to)
        if (sameCategoryResult != null) {
            val donor = DonorRoute(sameCategoryResult, approximate = false)
            odCache[key] = donor
            return donor
        }

        val tried = sameCategory.map { it.trainNo }.toHashSet()
        val relaxed = parseCandidates(results, allowAnyCategory = true)
            .filter { it.trainNo !in tried }
        val relaxedResult = firstSliced(relaxed, from, to)
        val donor = relaxedResult?.let { DonorRoute(it, approximate = true) }
        odCache[key] = donor
        return donor
    }

    private fun firstSliced(candidates: List<Candidate>, from: Anchor, to: Anchor): List<LatLng>? {
        for (candidate in candidates) {
            val fullLine = fetchTrainLine(candidate.trainNo) ?: continue
            val sliced = sliceRoute(fullLine, from.latLng, to.latLng) ?: continue
            return sliced
        }
        return null
    }

    private fun parseCandidates(
        results: List<String>,
        allowAnyCategory: Boolean = false,
    ): List<Candidate> {
        val seen = HashSet<String>()
        val candidates = mutableListOf<Candidate>()

        for (item in results) {
            val parts = item.split("|")
            if (parts.size < 4) continue

            val trainNo = parts[2]
            val code = parts[3]
            if (trainNo.isBlank() || code.isBlank()) continue
            if (trainNo == targetTrainNo) continue
            if (!seen.add(trainNo)) continue
            if (!allowAnyCategory && !sameTrainCategory(code, targetTrainCode)) continue

            candidates.add(Candidate(trainNo, code))
        }

        val targetLetter = targetTrainCode.firstOrNull()?.uppercaseChar()
        val limit = if (allowAnyCategory) MAX_DONOR_TRIALS * 4 else MAX_DONOR_TRIALS
        val comparator = if (allowAnyCategory) {
            // 借用兜底时优先高铁：普速走向普遍缺失，而 G 车数据覆盖率最高
            compareByDescending<Candidate> { it.code.firstOrNull()?.uppercaseChar() == 'G' }
                .thenBy { it.code }
        } else {
            compareByDescending<Candidate> { it.code.firstOrNull()?.uppercaseChar() == targetLetter }
                .thenBy { it.code }
        }
        return candidates.sortedWith(comparator).take(limit)
    }

    private fun sameTrainCategory(code: String, targetCode: String): Boolean {
        if (targetCode.isBlank()) return true
        val highSpeed = setOf('G', 'D', 'C')
        val codeType = code.firstOrNull()?.uppercaseChar()
        val targetType = targetCode.firstOrNull()?.uppercaseChar()
        return (codeType in highSpeed) == (targetType in highSpeed)
    }

    private fun fetchTrainLine(trainNo: String): List<LatLng>? {
        if (lineCache.containsKey(trainNo)) return lineCache[trainNo]
        if (mapLineBudget <= 0) return null

        mapLineBudget--
        val points = try {
            val response = trainService.getTrainMapLine("v2", trainNo).execute()
            if (response.isSuccessful && response.body()?.status == true) {
                buildPoints(response.body()?.data)
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.e("获取车次走向失败: $trainNo ${e.localizedMessage}", e)
            null
        }

        lineCache[trainNo] = points
        return points
    }

    private fun buildPoints(data: Map<String, TrainLineSegment>?): List<LatLng>? {
        if (data.isNullOrEmpty()) return null

        val points = mutableListOf<LatLng>()
        data.entries.sortedBy { it.value.index }.forEach { (_, segment) ->
            segment.line?.forEach { coordinate ->
                if (coordinate.size >= 2) {
                    val point = Utils.gcj02ToBd09(coordinate[1], coordinate[0])
                    if (points.lastOrNull() != point) {
                        points.add(point)
                    }
                }
            }
        }

        return points.takeIf { it.size >= 2 }
    }

    /**
     * 在 donor 的整条走向上截取离 from/to 最近的两个顶点之间的折线。
     * from/to 缺少坐标时（例如广州白云），取 donor 走向的起点/终点。
     */
    private fun sliceRoute(fullLine: List<LatLng>, from: LatLng?, to: LatLng?): List<LatLng>? {
        if (fullLine.size < 2) return null

        var fromNearestIndex = if (from == null) 0 else -1
        var toNearestIndex = if (to == null) fullLine.lastIndex else -1
        var fromNearestDistance = 0.0
        var toNearestDistance = 0.0

        if (from != null) {
            fromNearestDistance = Double.MAX_VALUE
            fullLine.forEachIndexed { index, point ->
                val distance = Utils.calculateDistance(
                    point.latitude, point.longitude,
                    from.latitude, from.longitude,
                )
                if (distance < fromNearestDistance) {
                    fromNearestDistance = distance
                    fromNearestIndex = index
                }
            }
        }

        if (to != null) {
            toNearestDistance = Double.MAX_VALUE
            fullLine.forEachIndexed { index, point ->
                val distance = Utils.calculateDistance(
                    point.latitude, point.longitude,
                    to.latitude, to.longitude,
                )
                if (distance < toNearestDistance) {
                    toNearestDistance = distance
                    toNearestIndex = index
                }
            }
        }

        if (fromNearestIndex < 0 || toNearestIndex < 0) return null
        if (fromNearestIndex == toNearestIndex) return null
        if (from != null && fromNearestDistance > MAX_STATION_TO_LINE_DISTANCE) return null
        if (to != null && toNearestDistance > MAX_STATION_TO_LINE_DISTANCE) return null

        val sliced = if (fromNearestIndex < toNearestIndex) {
            fullLine.subList(fromNearestIndex, toNearestIndex + 1).toList()
        } else {
            fullLine.subList(toNearestIndex, fromNearestIndex + 1).reversed()
        }
        if (sliced.size < 2) return null

        val straightDistance = if (from != null && to != null) {
            Utils.calculateDistance(
                from.latitude, from.longitude,
                to.latitude, to.longitude,
            )
        } else {
            0.0
        }

        var pathDistance = 0.0
        for (index in 1 until sliced.size) {
            pathDistance += Utils.calculateDistance(
                sliced[index - 1].latitude, sliced[index - 1].longitude,
                sliced[index].latitude, sliced[index].longitude,
            )
        }

        // 防止 donor 线路绕行/环线导致裁出一段明显不合理的路线
        if (straightDistance > 0) {
            if (pathDistance < straightDistance * 0.95) return null
            if (pathDistance > straightDistance * 3.0 + 50_000) return null
        }

        return sliced
    }

    private fun formatTicketDate(raw: String): String {
        return if (raw.length == 8) {
            "${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)}"
        } else {
            raw
        }
    }

    private fun hubScore(stationName: String?): Int {
        val name = stationName?.removeSuffix("站") ?: return 0
        return HUB_SCORES[name] ?: 0
    }

    private fun isTimeUp(): Boolean {
        return System.currentTimeMillis() - startTime > MAX_STITCH_MILLIS
    }

    companion object {
        private const val MAX_DONOR_TRIALS = 3
        private const val MAX_SPLIT_CANDIDATES = 5
        private const val MAX_TICKET_QUERIES = 24
        private const val MAX_MAP_LINE_FETCHES = 24
        private const val MAX_STITCH_MILLIS = 45_000L
        private const val MAX_STATION_TO_LINE_DISTANCE = 50_000.0

        /**
         * 常见枢纽站评分。拼接时优先在这些站拆分，避免在无数据的小站上浪费请求。
         */
        private val HUB_SCORES = mapOf(
            "北京" to 100, "北京西" to 100, "北京南" to 100,
            "上海" to 100, "上海虹桥" to 100, "上海南" to 95,
            "广州" to 100, "广州白云" to 100, "广州南" to 100, "广州东" to 95,
            "深圳" to 95, "深圳北" to 95,
            "郑州" to 98, "西安" to 96, "兰州" to 96, "西宁" to 94,
            "武汉" to 95, "武昌" to 95, "汉口" to 95,
            "重庆" to 95, "重庆西" to 95, "成都" to 95, "成都东" to 95,
            "南京" to 95, "杭州" to 92, "长沙" to 92, "天津" to 92,
            "乌鲁木齐" to 92, "拉萨" to 92, "昆明" to 92,
            "哈尔滨" to 90, "沈阳" to 90, "长春" to 88,
            "济南" to 88, "石家庄" to 88, "太原" to 88, "呼和浩特" to 88,
            "南昌" to 88, "福州" to 88, "合肥" to 88, "贵阳" to 88,
            "南宁" to 88, "海口" to 88, "银川" to 88,
            "格尔木" to 84, "徐州" to 86, "株洲" to 82,
            "洛阳" to 78, "宝鸡" to 78, "天水" to 74, "商丘" to 78,
            "蚌埠" to 78, "襄阳" to 82, "衡阳" to 80, "郴州" to 70,
        )
    }
}

/**
 * 12306 余票查询需要先访问 init 页面拿到会话 Cookie，之后同一 OkHttpClient 复用。
 */
class MemoryCookieJar : CookieJar {

    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val hostCookies = cookieStore.getOrPut(url.host) { mutableListOf() }
        cookies.forEach { newCookie ->
            hostCookies.removeAll { oldCookie ->
                oldCookie.name == newCookie.name && oldCookie.path == newCookie.path
            }
            hostCookies.add(newCookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookieStore[url.host].orEmpty().filter { it.matches(url) }
    }
}

fun createTicketHttpClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .cookieJar(MemoryCookieJar())
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        // 12306 会按 UA/Referer 判断请求来源，缺失时容易 302 到错误页
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
                )
                .header("Referer", "https://kyfw.12306.cn/otn/leftTicket/init")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .build()
            chain.proceed(request)
        }
        .build()
}
