package com.zuxing.markmap

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.CircleOptions
import com.amap.api.maps.model.LatLng as AMapLatLng
import com.amap.api.maps.model.Polygon
import com.amap.api.maps.model.PolygonOptions
import com.zuxing.markmap.data.entity.VisitedPlaceEntity
import com.zuxing.markmap.databinding.ActivityFootprintBinding
import com.zuxing.markmap.databinding.ItemFootprintBinding
import com.zuxing.markmap.domain.ChinaRegions
import com.zuxing.markmap.domain.FootprintEngine
import com.zuxing.markmap.domain.ProvinceBoundaries
import com.zuxing.markmap.domain.RegionCache
import com.zuxing.markmap.domain.RegionWkt
import com.zuxing.markmap.domain.ReportGenerator
import com.zuxing.markmap.widget.PlaceSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.IdentityHashMap
import java.util.Locale

class FootprintActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFootprintBinding
    private lateinit var app: MarkMapApplication
    private lateinit var mapView: MapView
    private var bitmap: Bitmap? = null

    private var mapReady = false
    private var showingMap = false
    private var level = 0 // 0=省 1=市
    private var stressCount = 0

    private val provinceInfo = LinkedHashMap<String, VisitedPlaceEntity>()
    private val cityInfo = LinkedHashMap<String, VisitedPlaceEntity>()
    private class DrawnRegion(val info: VisitedPlaceEntity, val rings: List<List<AMapLatLng>>)
    private val drawnRegions = mutableListOf<DrawnRegion>()

    private val regionService: AmapRegionService by lazy {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        retrofit2.Retrofit.Builder()
            .baseUrl(BaiduConfig.baseUrl)
            .client(client)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(AmapRegionService::class.java)
    }
    private var citySearchList: List<String> = emptyList()
    private var citySearchIndex = 0
    private var cityDrawn = 0
    private var cityLoading = false
    private var cameraListenerSet = false
    private val cityGeos = LinkedHashMap<String, CityGeo>()

    private class CityGeo(
        val info: VisitedPlaceEntity,
        val rings: List<List<AMapLatLng>>,
        val minLat: Double, val maxLat: Double, val minLng: Double, val maxLng: Double
    ) {
        fun intersects(minLa: Double, maxLa: Double, minLo: Double, maxLo: Double): Boolean =
            !(maxLat < minLa || minLat > maxLa || maxLng < minLo || minLng > maxLo)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFootprintBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as MarkMapApplication
        mapView = binding.amapView
        mapView.onCreate(savedInstanceState)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rvProvinces.layoutManager = GridLayoutManager(this, 4)
        binding.rvCities.layoutManager = GridLayoutManager(this, 4)

        binding.tvList.setOnClickListener { switchMode(false) }
        binding.tvMap.setOnClickListener { switchMode(true) }
        binding.tvProvinceLevel.setOnClickListener { selectLevel(0) }
        binding.tvCityLevel.setOnClickListener { selectLevel(1) }

        mapReady = app.amapReady
        if (!mapReady) binding.tvMap.alpha = 0.4f

        stressCount = intent.getIntExtra("stress", 0)
        if (intent.getBooleanExtra("prefetch", false)) {
            runPrefetch()
        } else {
            load()
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_footprint, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_share -> { share(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun load() {
        lifecycleScope.launch {
            val places = withContext(Dispatchers.IO) { FootprintEngine.rebuild(app.repository) }
            val cover = FootprintEngine.coverage(places)
            provinceInfo.clear(); provinceInfo.putAll(cover.provinces.associateBy { it.name })
            cityInfo.clear(); cityInfo.putAll(cover.cities.associateBy { it.name })

            val totals = withContext(Dispatchers.IO) { regionCounts() }
            val totalProv = totals.first; val totalCity = totals.second
            val coverageText = "已点亮 ${cover.provinceCount}/$totalProv 省 · ${cover.cityCount}/$totalCity 市"
            binding.tvCoverage.text = coverageText
            binding.tvMapCoverage.text = coverageText
            binding.pbProvince.progress = cover.provinceCount * 100 / totalProv.coerceAtLeast(1)
            binding.pbCity.progress = cover.cityCount * 100 / totalCity.coerceAtLeast(1)

            // 省份：展示全部（未点亮为灰态），新用户不会空白
            binding.tvProvSection.text = "省份（已点亮 ${cover.provinceCount}/$totalProv）"
            binding.rvProvinces.adapter = ChipAdapter(ChinaRegions.provinces, provinceInfo.keys)

            // 城市：只列已点亮；为空时给引导文案
            binding.tvCitySection.text = "已点亮城市（${cover.cityCount}/$totalCity）"
            binding.rvCities.adapter = ChipAdapter(cityInfo.keys.toList(), cityInfo.keys)
            binding.tvCityEmpty.visibility = if (cityInfo.isEmpty()) View.VISIBLE else View.GONE

            if (stressCount > 0) {
                val names = withContext(Dispatchers.IO) {
                    runCatching {
                        regionService.regionSearch("中国", 2, "base").execute()
                            .body()?.data?.districts?.firstOrNull()?.districts
                            ?.flatMap { it.districts ?: emptyList() }
                            ?.mapNotNull { it.name }?.distinct() ?: emptyList()
                    }.getOrDefault(emptyList())
                }.take(stressCount)
                if (names.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    cityInfo.clear()
                    names.forEach { n ->
                        cityInfo[n] = VisitedPlaceEntity(code = n, level = 1, name = n,
                            firstVisit = now, lastVisit = now, visitCount = 1)
                    }
                    binding.tvCitySection.text = "已点亮城市（${cityInfo.size}）"
                    binding.rvCities.adapter = ChipAdapter(cityInfo.keys.toList(), cityInfo.keys)
                    Logger.e("stress mock cities=${cityInfo.size}")
                    level = 1
                    if (!showingMap) switchMode(true) else buildMap()
                }
            }

            bitmap = withContext(Dispatchers.Default) {
                ReportGenerator.generate(
                    ReportGenerator.ReportData(
                        title = "我的足迹",
                        periodLabel = "已点亮 ${cover.provinceCount} 省 · ${cover.cityCount} 市",
                        totalKm = 0.0, tripCount = 0,
                        provinceCount = cover.provinceCount, cityCount = cover.cityCount,
                        longestKm = 0.0, topRoute = null,
                        flightKm = 0.0, railKm = 0.0, driveKm = 0.0
                    )
                )
            }

            if (showingMap) buildMap()
        }
    }

    /** 省/市总数：取自高德后端（中国/subdistrict=2），半年内走缓存。 */
    private suspend fun regionCounts(): Pair<Int, Int> {
        val key = "counts:cn:amap:2"
        RegionCache.get(this, key)?.let { cached ->
            val parts = cached.split(",")
            if (parts.size == 2) {
                val p = parts[0].trim().toIntOrNull()
                val c = parts[1].trim().toIntOrNull()
                if (p != null && c != null && p > 0 && c > 0) return p to c
            }
        }
        return try {
            val resp = regionService.regionSearch("中国", 2, "base").execute()
            val provinces = resp.body()?.data?.districts?.firstOrNull()?.districts ?: emptyList()
            val prov = provinces.size
            val city = provinces.sumOf { it.districts?.size ?: 0 }
            if (prov > 0 && city > 0) {
                RegionCache.put(this, key, "$prov,$city")
                prov to city
            } else {
                ChinaRegions.provinceCount to ChinaRegions.cityCount
            }
        } catch (e: Exception) {
            ChinaRegions.provinceCount to ChinaRegions.cityCount
        }
    }

    private fun switchMode(map: Boolean) {
        if (map && !mapReady) {
            Toast.makeText(this, "地图组件不可用", Toast.LENGTH_SHORT).show()
            return
        }
        showingMap = map
        hideLoading()
        style(binding.tvList, !map); style(binding.tvMap, map)
        binding.listScroll.visibility = if (map) View.GONE else View.VISIBLE
        binding.mapBox.visibility = if (map) View.VISIBLE else View.GONE
        binding.coverageCard.visibility = if (map) View.GONE else View.VISIBLE
        if (map) {
            mapView.onResume()
            buildMap()
        }
    }

    private fun selectLevel(lv: Int) {
        level = if (lv == 2) 1 else lv
        hideLoading()
        style(binding.tvProvinceLevel, level == 0)
        style(binding.tvCityLevel, level == 1)
        if (showingMap) buildMap()
    }

    private fun style(v: TextView, on: Boolean) {
        v.setBackgroundResource(if (on) R.drawable.bg_seg_on else android.R.color.transparent)
        v.setTextColor(ContextCompat.getColor(this, if (on) R.color.primary else R.color.ink_tertiary))
    }

    // ===== 地图（高德） =====

    private fun buildMap() {
        if (!mapReady) return
        val aMap = mapView.map
        aMap.clear()
        drawnRegions.clear()

        aMap.setOnMapClickListener { latLng ->
            val hit = drawnRegions.firstOrNull { contains(it.rings, latLng) }
            if (hit != null) showRegionSheet(hit.info)
            else Toast.makeText(this, "该区域尚未点亮", Toast.LENGTH_SHORT).show()
        }
        aMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                AMapLatLng(34.5, 104.0), if (level == 0) 3.8f else 5.0f
            )
        )

        if (level != 0) {
            if (!cameraListenerSet) {
                aMap.setOnCameraChangeListener(object : com.amap.api.maps.AMap.OnCameraChangeListener {
                    override fun onCameraChange(position: com.amap.api.maps.model.CameraPosition?) {}
                    override fun onCameraChangeFinish(position: com.amap.api.maps.model.CameraPosition?) {
                        renderViewport()
                    }
                })
                cameraListenerSet = true
            }
            startCityLoad()
            return
        }

        hideLoading()
        lifecycleScope.launch {
            val boundaries = withContext(Dispatchers.IO) { ProvinceBoundaries.load(this@FootprintActivity) }
            if (boundaries.isEmpty()) {
                fallbackDots()
                return@launch
            }
            drawProvinces(boundaries)
        }
    }

    private fun drawProvinces(boundaries: Map<String, ProvinceBoundaries.Boundary>) {
        val aMap = mapView.map
        ChinaRegions.provinces.forEach { short ->
            val boundary = boundaries[short] ?: return@forEach
            val info = provinceInfo[short]
            val lit = info != null
            val fill = if (lit) 0x882E5D5C.toInt() else 0x22DDE7E1
            val stroke = Color.parseColor(if (lit) "#1F4241" else "#C7D8CF")
            val amapRings = boundary.rings
                .filter { it.size >= 3 }
                .map { ring -> simplify(ring).map { AMapLatLng(it.first, it.second) } }
            amapRings.forEach { ring ->
                aMap.addPolygon(
                    PolygonOptions().addAll(ring).fillColor(fill).strokeColor(stroke).strokeWidth(3f)
                )
            }
            if (lit) drawnRegions.add(DrawnRegion(info!!, amapRings))
        }
    }

    private var searchLevel = 1

    private fun cacheKey(lv: Int, short: String): String = "amap:lvl$lv:$short"

    private fun startCityLoad() {
        if (cityLoading) return
        cityLoading = true
        val list = cityInfo.values.sortedByDescending { it.visitCount }
            .map { it.name }
            .filter { it.isNotBlank() && !it.contains("直辖县级行政区划") }
            .take(300)
        if (list.isEmpty()) { cityLoading = false; hideLoading(); return }
        showLoading(list.size)
        val started = System.currentTimeMillis()
        lifecycleScope.launch {
            // 1) 只补齐缺缓存的（30/批）
            val miss = list.filter { RegionCache.get(this@FootprintActivity, cacheKey(1, it)) == null }
            val chunks = miss.chunked(30)
            chunks.forEachIndexed { idx, chunk ->
                val resp = withContext(Dispatchers.IO) {
                    runCatching { regionService.regionSearchBatch(chunk, 0, "all").execute() }.getOrNull()
                }
                val districts = resp?.body()?.data?.districts ?: emptyList()
                withContext(Dispatchers.Main) {
                    districts.forEach { dto ->
                        val poly = dto.polyline
                        val kw = dto.keyword
                        val target = if (kw.isNullOrBlank()) null else matchName(chunk, kw)
                        if (!poly.isNullOrBlank() && target != null) {
                            RegionCache.put(this@FootprintActivity, cacheKey(1, target), poly)
                        }
                    }
                    val returned = districts.mapNotNull { dto -> dto.keyword?.let { matchName(chunk, it) } }.toSet()
                    chunk.filter { it !in returned }.forEach { RegionCache.putEmpty(this@FootprintActivity, cacheKey(1, it)) }
                }
                if (idx < chunks.size - 1) delay(1000)
            }

            // 2) 解析全部（缓存）几何 + 计算 bbox
            val geos = withContext(Dispatchers.Default) {
                val map = LinkedHashMap<String, CityGeo>()
                list.forEach { name ->
                    val poly = RegionCache.get(this@FootprintActivity, cacheKey(1, name))?.takeIf { it.isNotBlank() } ?: return@forEach
                    val rings = RegionWkt.parseAmap(poly)
                        .filter { it.size >= 3 }
                        .map { ring -> simplify(ring).map { AMapLatLng(it.first, it.second) } }
                    if (rings.isEmpty()) return@forEach
                    var minLa = 90.0; var maxLa = -90.0; var minLo = 180.0; var maxLo = -180.0
                    rings.forEach { r -> r.forEach { p ->
                        if (p.latitude < minLa) minLa = p.latitude
                        if (p.latitude > maxLa) maxLa = p.latitude
                        if (p.longitude < minLo) minLo = p.longitude
                        if (p.longitude > maxLo) maxLo = p.longitude
                    } }
                    cityInfo[name]?.let { map[name] = CityGeo(it, rings, minLa, maxLa, minLo, maxLo) }
                }
                map
            }
            withContext(Dispatchers.Main) {
                cityGeos.clear(); cityGeos.putAll(geos)
                Logger.e("city geos=${cityGeos.size} 准备耗时=${System.currentTimeMillis() - started}ms")
                cityLoading = false
                renderViewport()
                hideLoading()
            }
        }
    }

    /** 只渲染当前视野内的城市多边形。 */
    private fun renderViewport() {
        if (level == 0) return
        val aMap = mapView.map
        aMap.clear()
        drawnRegions.clear()
        val region = aMap.projection?.visibleRegion?.latLngBounds ?: return
        val sw = region.southwest; val ne = region.northeast
        val padLat = (ne.latitude - sw.latitude) * 0.1
        val padLng = (ne.longitude - sw.longitude) * 0.1
        val minLa = sw.latitude - padLat; val maxLa = ne.latitude + padLat
        val minLo = sw.longitude - padLng; val maxLo = ne.longitude + padLng

        var drawn = 0
        val limit = 120
        cityGeos.values.forEach { geo ->
            if (drawn >= limit) return@forEach
            if (geo.intersects(minLa, maxLa, minLo, maxLo)) {
                geo.rings.forEach { ring ->
                    if (ring.size < 3) return@forEach
                    aMap.addPolygon(
                        PolygonOptions().addAll(ring)
                            .fillColor(0x882E5D5C.toInt())
                            .strokeColor(Color.parseColor("#1F4241"))
                            .strokeWidth(3f)
                    )
                }
                drawnRegions.add(DrawnRegion(geo.info, geo.rings))
                drawn++
            }
        }
        binding.tvMapCoverage.text = "视野内 $drawn 城 · 已点亮 ${cityInfo.size}/${binding.tvCoverage.text.toString().substringAfter('/').substringBefore(' ')} 省"
    }

    /** 批量返回的 keyword 与请求名做模糊匹配。 */
    private fun matchName(chunk: List<String>, keyword: String): String? {
        return chunk.firstOrNull { it == keyword }
            ?: chunk.firstOrNull { keyword.startsWith(it) || it.startsWith(keyword) }
    }

    /** 顺序预缓存全部省/市边界：1 次/秒，遇到错误停止。 */
    private fun runPrefetch() {
        showLoading()
        lifecycleScope.launch {
            val provinceNames = ChinaRegions.provinces.map { ChinaRegions.fullProvinceName[it] ?: it }
            val cityNames = withContext(Dispatchers.IO) {
                runCatching {
                    regionService.regionSearch("中国", 2, "base").execute()
                        .body()?.data?.districts?.firstOrNull()?.districts
                        ?.flatMap { it.districts ?: emptyList() }
                        ?.mapNotNull { it.name }
                        ?.filter { it.isNotBlank() && !it.contains("直辖县级行政区划") }
                        ?.distinct() ?: emptyList()
                }.getOrDefault(emptyList())
            }
            val tasks = provinceNames.map { 0 to it } + cityNames.map { 1 to it }
            if (tasks.isEmpty()) { hideLoading(); Logger.e("prefetch: no names"); return@launch }
            var done = 0; var cached = 0
            for ((lv, name) in tasks) {
                val key = cacheKey(lv, name)
                if (RegionCache.get(this@FootprintActivity, key) != null) { done++; continue }
                val q = if (lv == 0) {
                    ChinaRegions.fullProvinceName.entries.firstOrNull { it.value == name }?.key ?: name
                } else {
                    if (name.endsWith("州") || name.endsWith("地区") || name.endsWith("盟") || name.endsWith("市")) name else name + "市"
                }
                val poly = try {
                    withContext(Dispatchers.IO) {
                        regionService.regionSearch(q, 0, "all").execute()
                            .body()?.data?.districts?.firstOrNull()?.polyline
                    }
                } catch (e: Exception) {
                    Logger.e("prefetch STOP at lv$lv $name: ${e.message}"); break
                }
                if (!poly.isNullOrBlank()) { RegionCache.put(this@FootprintActivity, key, poly); cached++ }
                else RegionCache.putEmpty(this@FootprintActivity, key)
                done++
                delay(1000)
            }
            hideLoading()
            Logger.e("prefetch done $done/${tasks.size} cached=$cached")
        }
    }

    private fun drawRegion(rings: List<List<Pair<Double, Double>>>, info: VisitedPlaceEntity) {
        val aMap = mapView.map
        val amapRings = rings.filter { it.size >= 3 }
            .map { ring -> simplify(ring).map { AMapLatLng(it.first, it.second) } }
        amapRings.forEach { ring ->
            aMap.addPolygon(
                PolygonOptions().addAll(ring)
                    .fillColor(0x882E5D5C.toInt())
                    .strokeColor(Color.parseColor("#1F4241"))
                    .strokeWidth(3f)
            )
        }
        if (amapRings.isNotEmpty()) drawnRegions.add(DrawnRegion(info, amapRings))
        cityDrawn++
    }

    /** 环几何抽稀：环点数超过上限时等间隔抽样，降低渲染开销。 */
    private fun simplify(ring: List<Pair<Double, Double>>, maxPoints: Int = 80): List<Pair<Double, Double>> {
        if (ring.size <= maxPoints) return ring
        val step = (ring.size + maxPoints - 1) / maxPoints
        val out = ArrayList<Pair<Double, Double>>(maxPoints + 1)
        var i = 0
        while (i < ring.size) { out.add(ring[i]); i += step }
        if (out.last() != ring.last()) out.add(ring.last())
        return out
    }

    /** 射线法：点是否在任一多边形环内。 */
    private fun contains(rings: List<List<AMapLatLng>>, p: AMapLatLng): Boolean {
        rings.forEach { ring ->
            var inside = false
            var j = ring.size - 1
            for (i in ring.indices) {
                val yi = ring[i].latitude
                val xi = ring[i].longitude
                val yj = ring[j].latitude
                val xj = ring[j].longitude
                val intersect = ((yi > p.latitude) != (yj > p.latitude)) &&
                    (p.longitude < (xj - xi) * (p.latitude - yi) / (yj - yi + 1e-12) + xi)
                if (intersect) inside = !inside
                j = i
            }
            if (inside) return true
        }
        return false
    }

    private fun fallbackDots() {
        val aMap = mapView.map
        val lit = if (level == 1) cityInfo.values else provinceInfo.values
        if (lit.isEmpty()) return
        lit.forEach { info ->
            val c = ChinaRegions.cities.firstOrNull { it.name == info.name }
                ?: ChinaRegions.cities.firstOrNull { it.province == info.name }
            c ?: return@forEach
            val radius = 20_000.0 + 15_000.0 * info.visitCount
            aMap.addCircle(
                CircleOptions()
                    .center(AMapLatLng(c.lat, c.lng))
                    .radius(radius)
                    .fillColor(0x662E5D5C)
                    .strokeColor(Color.parseColor("#1F4241"))
                    .strokeWidth(3f)
            )
        }
    }

    private fun showRegionSheet(info: VisitedPlaceEntity) {
        val df = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
        val admin = "到访 ${info.visitCount} 次 · 首次 ${df.format(Date(info.firstVisit))} · 最近 ${df.format(Date(info.lastVisit))}"
        PlaceSheet.show(this, info.name, admin)
    }

    // ===== 加载动效 =====

    private fun showLoading(total: Int = 0) {
        // 常驻悬浮动效：始终展示并持续播放
        binding.orbit.visibility = View.VISIBLE
        binding.orbit.start()
    }

    private fun hideLoading() {
        // 不停止动效，保持持续播放
    }

    private fun share() {
        val bmp = bitmap ?: return
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                val dir = File(cacheDir, "share").apply { mkdirs() }
                val target = File(dir, "footprint_${System.currentTimeMillis()}.png")
                FileOutputStream(target).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                target
            }
            val uri = FileProvider.getUriForFile(this@FootprintActivity, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "分享足迹"))
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    private class ChipAdapter(
        private val items: List<String>,
        private val lit: Set<String>
    ) : RecyclerView.Adapter<ChipAdapter.VH>() {

        class VH(val binding: ItemFootprintBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemFootprintBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(binding)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val name = items[position]
            val on = name in lit
            holder.binding.chip.text = name
            holder.binding.chip.setBackgroundResource(
                if (on) R.drawable.bg_chip else R.drawable.bg_pill
            )
            holder.binding.chip.setTextColor(
                ContextCompat.getColor(
                    holder.binding.root.context,
                    if (on) R.color.primary else R.color.ink_tertiary
                )
            )
        }
    }
}
