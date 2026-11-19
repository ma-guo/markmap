package com.zuxing.markmap

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.zuxing.markmap.databinding.ActivityMileageBinding
import com.zuxing.markmap.databinding.ItemMileageBinding
import com.zuxing.markmap.domain.AchievementEngine
import com.zuxing.markmap.domain.Mileage
import com.zuxing.markmap.domain.MileageKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MileageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMileageBinding
    private lateinit var app: MarkMapApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMileageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as MarkMapApplication
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val stats = withContext(Dispatchers.IO) { AchievementEngine.collectStats(app.repository) }
            val inflater = LayoutInflater.from(this@MileageActivity)
            binding.container.removeAllViews()
            addCard(inflater, MileageKind.FLIGHT, stats.flightKm)
            addCard(inflater, MileageKind.RAIL, stats.railKm)
            addCard(inflater, MileageKind.TOTAL, stats.totalKm)
        }
    }

    private fun addCard(inflater: LayoutInflater, kind: MileageKind, km: Double) {
        val item = ItemMileageBinding.inflate(inflater, binding.container, false)
        val p = Mileage.progress(km, kind)
        item.tvLabel.text = kind.label
        item.tvLevel.text = p.current.name
        item.tvKm.text = String.format(Locale.getDefault(), "%,.0f km", km)
        item.progress.progress = (p.ratio * 100).toInt()
        item.tvNext.text = if (p.next == null || p.remainKm <= 0.0) {
            "已达最高等级"
        } else {
            String.format(Locale.getDefault(), "距%s还需 %,.0f km", p.next.name, p.remainKm)
        }
        binding.container.addView(item.root)
    }
}
