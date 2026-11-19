package com.zuxing.markmap

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.zuxing.markmap.databinding.ActivityReportBinding
import com.zuxing.markmap.domain.LocationResolver
import com.zuxing.markmap.domain.ReportGenerator
import com.zuxing.markmap.domain.TrackStats
import com.zuxing.markmap.domain.TransportMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale

class ReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportBinding
    private lateinit var app: MarkMapApplication
    private var yearly = false
    private var bitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as MarkMapApplication
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.segMonth.setOnClickListener { select(false) }
        binding.segYear.setOnClickListener { select(true) }
        binding.btnGenerate.setOnClickListener { buildBitmap() }
        binding.btnShare.setOnClickListener { share() }

        select(false)
    }

    private fun select(year: Boolean) {
        yearly = year
        style(binding.segMonth, !year)
        style(binding.segYear, year)
        buildBitmap()
    }

    private fun style(view: TextView, on: Boolean) {
        view.setBackgroundResource(if (on) R.drawable.bg_chip else R.drawable.bg_pill)
        view.setTextColor(ContextCompat.getColor(this, if (on) R.color.primary else R.color.ink_secondary))
        view.typeface = if (on) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
    }

    private fun buildBitmap() {
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) { aggregate() }
            binding.tvStats.text =
                "总里程 ${"%,.0f".format(data.totalKm)} km · ${data.tripCount} 段 · ${data.cityCount} 城市"
            val bmp = withContext(Dispatchers.Default) { ReportGenerator.generate(data) }
            bitmap = bmp
            binding.ivPreview.setImageBitmap(bmp)
        }
    }

    private suspend fun aggregate(): ReportGenerator.ReportData {
        val cal = Calendar.getInstance()
        val start: Long
        val label: String
        if (yearly) {
            cal.set(Calendar.DAY_OF_YEAR, 1); cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            start = cal.timeInMillis
            label = "${cal.get(Calendar.YEAR)} 年度足迹报告"
        } else {
            cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            start = cal.timeInMillis
            label = "${cal.get(Calendar.YEAR)} 年 ${cal.get(Calendar.MONTH) + 1} 月足迹报告"
        }

        val lines = app.repository.getAllLines().first()
        var totalKm = 0.0; var flightKm = 0.0; var railKm = 0.0; var driveKm = 0.0
        var longest = 0.0; var topRoute: String? = null
        var count = 0
        val cities = mutableSetOf<String>()

        for (line in lines) {
            val t = line.startTime ?: line.createTime
            if (t < start) continue
            val points = app.repository.getPointsByLineIdOnce(line.id)
            val km = TrackStats.fromPoints(points).distanceMeters / 1000.0
            count++
            totalKm += km
            when (TransportMode.from(line.transportMode)) {
                TransportMode.FLIGHT -> flightKm += km
                TransportMode.TRAIN -> railKm += km
                TransportMode.DRIVE -> driveKm += km
                else -> {}
            }
            if (km > longest) { longest = km; topRoute = line.name }
            val sorted = points.sortedBy { it.sortOrder }
            listOfNotNull(sorted.firstOrNull(), sorted.lastOrNull()).distinctBy { it.id }.forEach { p ->
                LocationResolver.resolve(p.province, p.city, p.address, p.latitude, p.longitude)
                    .city?.let { cities.add(it) }
            }
        }

        return ReportGenerator.ReportData(
            title = if (yearly) "年度足迹" else "月度足迹",
            periodLabel = label,
            totalKm = totalKm,
            tripCount = count,
            provinceCount = cities.size, // 省数用城市去重近似上报，纯展示
            cityCount = cities.size,
            longestKm = longest,
            topRoute = topRoute,
            flightKm = flightKm,
            railKm = railKm,
            driveKm = driveKm
        )
    }

    private fun share() {
        val bmp = bitmap ?: return
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                val dir = File(cacheDir, "share").apply { mkdirs() }
                val target = File(dir, "report_${System.currentTimeMillis()}.png")
                FileOutputStream(target).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                target
            }
            val uri = FileProvider.getUriForFile(this@ReportActivity, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "分享报告"))
        }
    }
}
