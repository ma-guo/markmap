package com.zuxing.markmap

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.zuxing.markmap.data.entity.PointEntity
import com.zuxing.markmap.databinding.ActivityProfileBinding
import com.zuxing.markmap.domain.ProfileBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var app: MarkMapApplication
    private var profile: ProfileBuilder.Profile? = null
    private var altitudeMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as MarkMapApplication
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.tvAlt.setOnClickListener { setMode(true) }
        binding.tvSpeed.setOnClickListener { setMode(false) }

        load()
    }

    private fun setMode(altitude: Boolean) {
        altitudeMode = altitude
        style(binding.tvAlt, altitude)
        style(binding.tvSpeed, !altitude)
        render()
    }

    private fun style(v: TextView, on: Boolean) {
        v.setBackgroundResource(if (on) R.drawable.bg_seg_on else android.R.color.transparent)
        v.setTextColor(ContextCompat.getColor(this, if (on) R.color.primary else R.color.ink_tertiary))
    }

    private fun load() {
        val lineId = intent.getLongExtra("lineId", -1L)
        val groupId = intent.getLongExtra("groupId", -1L)
        lifecycleScope.launch {
            val profiles = withContext(Dispatchers.IO) { loadProfiles(lineId, groupId) }
            profile = mergeProfiles(profiles)
            render()
        }
    }

    private suspend fun loadProfiles(lineId: Long, groupId: Long): List<ProfileBuilder.Profile> {
        return when {
            lineId != -1L -> listOf(ProfileBuilder.build(app.repository.getPointsByLineIdOnce(lineId)))
            groupId != -1L -> app.repository.getLinesByGroupIdOnce(groupId)
                .sortedBy { it.sortOrder }
                .map { ProfileBuilder.build(app.repository.getPointsByLineIdOnce(it.id)) }
            else -> emptyList()
        }
    }

    /** 按段拼接：各段里程顺序累加，段间跳变不计入里程。 */
    private fun mergeProfiles(profiles: List<ProfileBuilder.Profile>): ProfileBuilder.Profile {
        if (profiles.isEmpty()) return ProfileBuilder.build(emptyList())
        val samples = ArrayList<ProfileBuilder.Sample>()
        var kmOffset = 0.0
        var totalKm = 0.0; var duration = 0L; var ascent = 0.0; var descent = 0.0
        var maxSpeed = 0.0; var maxAlt: Double? = null; var minAlt: Double? = null
        for (pf in profiles) {
            pf.samples.forEach { s ->
                samples.add(s.copy(km = s.km + kmOffset))
            }
            kmOffset += pf.totalKm
            totalKm += pf.totalKm
            duration += pf.durationMs
            ascent += pf.ascent; descent += pf.descent
            maxSpeed = maxOf(maxSpeed, pf.maxSpeedKmh)
            pf.maxAltitude?.let { maxAlt = if (maxAlt == null) it else maxOf(maxAlt!!, it) }
            pf.minAltitude?.let { minAlt = if (minAlt == null) it else minOf(minAlt!!, it) }
        }
        val avg = if (duration > 0) totalKm / (duration / 3_600_000.0) else 0.0
        return ProfileBuilder.Profile(samples, totalKm, duration, avg, maxSpeed, maxAlt, minAlt, ascent, descent)
    }

    private fun render() {
        val p = profile ?: return
        if (altitudeMode) {
            val data = p.samples.mapNotNull { s -> s.altitude?.let { s.km to it } }
            binding.chart.setData(data, "m")
            binding.tvLabelA.text = "公里"; binding.tvStatA.text = fmt(p.totalKm)
            binding.tvLabelB.text = "时长"; binding.tvStatB.text = duration(p.durationMs)
            binding.tvLabelC.text = "爬升"; binding.tvStatC.text = "+" + fmt(p.ascent)
        } else {
            val data = p.samples.mapNotNull { s -> s.speedKmh?.let { s.km to it } }
            binding.chart.setData(data, "km/h", Color.parseColor("#B96A4B"))
            binding.tvLabelA.text = "平均"; binding.tvStatA.text = fmt(p.avgSpeedKmh)
            binding.tvLabelB.text = "最高"; binding.tvStatB.text = fmt(p.maxSpeedKmh)
            binding.tvLabelC.text = "公里"; binding.tvStatC.text = fmt(p.totalKm)
        }
    }

    private fun fmt(v: Double): String = String.format(Locale.getDefault(), "%,.0f", v)

    private fun duration(ms: Long): String {
        if (ms <= 0) return "—"
        val h = ms / 3_600_000; val m = (ms % 3_600_000) / 60_000
        return if (h > 0) "${h}h${m}m" else "${m}m"
    }
}
