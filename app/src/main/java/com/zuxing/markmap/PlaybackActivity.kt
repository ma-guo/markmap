package com.zuxing.markmap

import android.animation.ValueAnimator
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.Overlay
import com.baidu.mapapi.map.PolylineOptions
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.model.LatLngBounds
import com.zuxing.markmap.data.entity.PointEntity
import com.zuxing.markmap.databinding.ActivityPlaybackBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class PlaybackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaybackBinding
    private lateinit var app: MarkMapApplication
    private lateinit var mapView: MapView
    private var points: List<PointEntity> = emptyList()
    private var trackOverlay: Overlay? = null
    private var marker: Marker? = null
    private var animator: ValueAnimator? = null
    private var playing = false
    private var speedFactor = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!(application as MarkMapApplication).baiduReady) {
            android.widget.Toast.makeText(this, "地图组件不可用", android.widget.Toast.LENGTH_LONG).show()
            finish(); return
        }
        binding = ActivityPlaybackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as MarkMapApplication
        mapView = binding.mapView

        binding.navBar.applyTopInsetPadding(8f)
        binding.sheet.applyBottomInsetPadding()
        binding.btnBack.setOnClickListener { finish() }
        binding.btnRecenter.setOnClickListener { fitBounds() }
        binding.btnPlay.setOnClickListener { togglePlay() }
        binding.btnSpeed.setOnClickListener { cycleSpeed() }
        binding.seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                if (fromUser) {
                    animator?.cancel(); playing = false; binding.btnPlay.text = "播放"
                    renderAt(value)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        load()
    }

    private fun load() {
        binding.progressBar.visibility = android.view.View.VISIBLE
        val lineId = intent.getLongExtra("lineId", -1L)
        val groupId = intent.getLongExtra("groupId", -1L)
        lifecycleScope.launch {
            points = withContext(Dispatchers.IO) {
                when {
                    lineId != -1L -> app.repository.getPointsByLineIdOnce(lineId)
                        .sortedBy { it.sortOrder }
                    groupId != -1L -> app.repository.getLinesByGroupIdOnce(groupId)
                        .sortedBy { it.sortOrder }
                        .flatMap { app.repository.getPointsByLineIdOnce(it.id).sortedBy { p -> p.sortOrder } }
                    else -> emptyList()
                }
            }
            binding.progressBar.visibility = android.view.View.GONE
            setupTrack()
        }
    }

    private fun setupTrack() {
        val map = mapView.map
        map.clear()
        if (points.size < 2) return
        val latLngs = points.map { LatLng(it.latitude, it.longitude) }
        trackOverlay = map.addOverlay(PolylineOptions().points(latLngs).width(8).color(0xFF2E5D5C.toInt()))
        binding.seek.max = points.size - 1
        renderAt(0)
        fitBounds()
    }

    private fun renderAt(index: Int) {
        if (points.isEmpty()) return
        val i = index.coerceIn(0, points.size - 1)
        val p = points[i]
        marker?.remove()
        marker = mapView.map.addOverlay(
            MarkerOptions()
                .position(LatLng(p.latitude, p.longitude))
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.location_on_48dp_start))
        ) as Marker

        val speed = p.speed?.let { if (it > 0f) it * 3.6 else null }
        binding.tvSpeed.text = speed?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "—"
        binding.tvAltitude.text = p.altitude?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "—"
        val elapsed = p.createTime - points[0].createTime
        binding.tvElapsed.text = if (elapsed > 0) {
            val m = (elapsed / 60000).toInt(); val s = ((elapsed / 1000) % 60).toInt()
            String.format(Locale.getDefault(), "%d:%02d", m, s)
        } else "0:00"
        binding.seek.progress = i
    }

    private fun togglePlay() {
        if (points.size < 2) return
        if (playing) {
            animator?.cancel(); playing = false; binding.btnPlay.text = "播放"
        } else {
            val start = binding.seek.progress
            val baseDuration = (points.size * 260L) / speedFactor
            animator = ValueAnimator.ofInt(start, points.size - 1).apply {
                duration = baseDuration.coerceAtLeast(600L)
                addUpdateListener { renderAt(it.animatedValue as Int) }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        playing = false; binding.btnPlay.text = "播放"
                    }
                })
                start()
            }
            playing = true; binding.btnPlay.text = "暂停"
        }
    }

    private fun cycleSpeed() {
        speedFactor = when (speedFactor) { 1 -> 2; 2 -> 4; else -> 1 }
        binding.btnSpeed.text = "${speedFactor}x"
        if (playing) { animator?.cancel(); playing = false; togglePlay() }
    }

    private fun fitBounds() {
        if (points.size < 2) return
        val builder = LatLngBounds.Builder()
        points.forEach { builder.include(LatLng(it.latitude, it.longitude)) }
        mapView.map.animateMapStatus(MapStatusUpdateFactory.newLatLngBounds(builder.build(), 80, 80))
    }

    override fun onResume() { super.onResume(); if (this::mapView.isInitialized) mapView.onResume() }
    override fun onPause() { super.onPause(); if (this::mapView.isInitialized) mapView.onPause() }
    override fun onDestroy() {
        super.onDestroy()
        animator?.cancel()
        if (this::mapView.isInitialized) mapView.onDestroy()
    }
}
