package com.zuxing.markmap

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zuxing.markmap.databinding.ActivityFootprintBinding
import com.zuxing.markmap.databinding.ItemFootprintBinding
import com.zuxing.markmap.domain.ChinaRegions
import com.zuxing.markmap.domain.FootprintEngine
import com.zuxing.markmap.domain.ReportGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class FootprintActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFootprintBinding
    private lateinit var app: MarkMapApplication
    private var bitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFootprintBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as MarkMapApplication
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rvProvinces.layoutManager = GridLayoutManager(this, 4)
        binding.rvCities.layoutManager = GridLayoutManager(this, 4)
        binding.btnShareFootprint.setOnClickListener { share() }

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val places = withContext(Dispatchers.IO) { FootprintEngine.rebuild(app.repository) }
            val cover = FootprintEngine.coverage(places)
            val litProvinces = cover.provinces.map { it.name }.toSet()
            val litCities = cover.cities.map { it.name }.toSet()

            binding.tvCoverage.text =
                "已点亮 ${cover.provinceCount}/${ChinaRegions.provinceCount} 省 · " +
                    "${cover.cityCount}/${ChinaRegions.cityCount} 市"
            binding.pbProvince.progress = (cover.provinceCount * 100 / ChinaRegions.provinceCount)
            binding.pbCity.progress = (cover.cityCount * 100 / ChinaRegions.cityCount.coerceAtLeast(1))

            val provinces = ChinaRegions.provinces.sortedByDescending { it in litProvinces }
            val cities = ChinaRegions.cities.map { it.name }.distinct().sortedByDescending { it in litCities }
            binding.rvProvinces.adapter = ChipAdapter(provinces, litProvinces)
            binding.rvCities.adapter = ChipAdapter(cities, litCities)

            bitmap = withContext(Dispatchers.Default) {
                ReportGenerator.generate(
                    ReportGenerator.ReportData(
                        title = "我的足迹",
                        periodLabel = "已点亮 ${cover.provinceCount} 省 · ${cover.cityCount} 市",
                        totalKm = 0.0,
                        tripCount = 0,
                        provinceCount = cover.provinceCount,
                        cityCount = cover.cityCount,
                        longestKm = 0.0,
                        topRoute = null,
                        flightKm = 0.0,
                        railKm = 0.0,
                        driveKm = 0.0
                    )
                )
            }
        }
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
                androidx.core.content.ContextCompat.getColor(
                    holder.binding.root.context,
                    if (on) R.color.primary else R.color.ink_tertiary
                )
            )
        }
    }
}
