package com.zuxing.markmap

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.Overlay
import com.baidu.mapapi.map.PolylineOptions
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.model.LatLngBounds
import com.zuxing.markmap.databinding.ActivityFlightBinding
import com.zuxing.markmap.domain.Airports
import com.zuxing.markmap.domain.GreatCircle
import com.zuxing.markmap.domain.TrackMerger
import com.zuxing.markmap.domain.TransportMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class FlightActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFlightBinding
    private lateinit var app: MarkMapApplication
    private lateinit var mapView: MapView
    private var dep: com.zuxing.markmap.domain.Airport? = null
    private var arr: com.zuxing.markmap.domain.Airport? = null
    private var polyline: Overlay? = null
    private var route: List<Pair<Double, Double>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!(application as MarkMapApplication).baiduReady) {
            android.widget.Toast.makeText(this, "地图组件不可用", android.widget.Toast.LENGTH_LONG).show()
            finish(); return
        }
        binding = ActivityFlightBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as MarkMapApplication
        mapView = binding.mapView

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val labels = Airports.all.map { it.label }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels)
        binding.actvDep.setAdapter(adapter)
        binding.actvArr.setAdapter(adapter)
        binding.actvDep.setOnItemClickListener { _, _, pos, _ -> dep = Airports.all[pos]; refresh() }
        binding.actvArr.setOnItemClickListener { _, _, pos, _ -> arr = Airports.all[pos]; refresh() }
        binding.btnSave.setOnClickListener { save() }

        // 默认北京→上海
        dep = Airports.byCode("PEK"); arr = Airports.byCode("SHA")
        binding.actvDep.setText(dep?.label, false)
        binding.actvArr.setText(arr?.label, false)
        refresh()
    }

    private fun refresh() {
        val a = dep ?: return
        val b = arr ?: return
        route = GreatCircle.interpolate(a, b)
        val km = route.zipWithNext().sumOf { (p1, p2) ->
            TrackMerger.distanceMeters(p1.first, p1.second, p2.first, p2.second)
        } / 1000.0
        binding.tvInfo.text = String.format(
            Locale.getDefault(), "%s → %s · 大圆参考航线 · 约 %,.0f km（虚线）", a.label, b.label, km
        )

        val map = mapView.map
        map.clear()
        val latLngs = route.map { LatLng(it.first, it.second) }
        polyline = map.addOverlay(
            PolylineOptions().points(latLngs).width(8)
                .color(0xFF4A6FA5.toInt())
                .dottedLine(true)
        )
        map.addOverlay(
            MarkerOptions().position(latLngs.first())
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.location_on_48dp_start))
        )
        map.addOverlay(
            MarkerOptions().position(latLngs.last())
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.location_on_48dp_end))
        )
        val builder = LatLngBounds.Builder()
        latLngs.forEach { builder.include(it) }
        map.animateMapStatus(MapStatusUpdateFactory.newLatLngBounds(builder.build(), 80, 80))
    }

    private fun save() {
        if (route.size < 2) return
        lifecycleScope.launch {
            val groups = withContext(Dispatchers.IO) { app.repository.getAllGroups().first() }
            if (groups.isEmpty()) {
                android.widget.Toast.makeText(this@FlightActivity, "请先创建行程", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            val names = groups.map { it.name }.toTypedArray()
            androidx.appcompat.app.AlertDialog.Builder(this@FlightActivity)
                .setTitle("保存为路段")
                .setItems(names) { _, which -> persist(groups[which].id) }
                .show()
        }
    }

    private fun persist(groupId: Long) {
        val a = dep ?: return
        val b = arr ?: return
        val name = "${a.code} → ${b.code}"
        val now = System.currentTimeMillis()
        lifecycleScope.launch(Dispatchers.IO) {
            val lineId = app.repository.insertLine(
                com.zuxing.markmap.data.entity.LineEntity(
                    name = name,
                    groupId = groupId,
                    sortOrder = 0,
                    transportMode = TransportMode.FLIGHT.name,
                    startTime = now,
                    endTime = now + 2 * 3_600_000L,
                    isReference = true
                )
            )
            val step = (route.size / 32).coerceAtLeast(1)
            val pts = route.filterIndexed { i, _ -> i % step == 0 }.mapIndexed { index, (lat, lng) ->
                com.zuxing.markmap.data.entity.PointEntity(
                    lineId = lineId,
                    longitude = lng,
                    latitude = lat,
                    description = "参考航线",
                    sortOrder = index + 1,
                    createTime = now + index * 60_000L,
                    modifyTime = now + index * 60_000L
                )
            }
            app.repository.insertPoints(pts)
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(this@FlightActivity, "已保存参考航线到行程", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() { super.onResume(); if (this::mapView.isInitialized) mapView.onResume() }
    override fun onPause() { super.onPause(); if (this::mapView.isInitialized) mapView.onPause() }
    override fun onDestroy() { super.onDestroy(); if (this::mapView.isInitialized) mapView.onDestroy() }
}
