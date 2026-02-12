package com.zuxing.markmap

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.zuxing.markmap.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val prefs by lazy { getSharedPreferences("settings", 0) }

    companion object {
        const val KEY_VIBRATE = "vibrate_enabled"
        const val KEY_INTERVAL = "location_interval"
        const val KEY_DISTANCE = "location_distance"
        const val DEFAULT_VIBRATE = true
        const val DEFAULT_INTERVAL = 30_000L
        const val DEFAULT_DISTANCE = 10.0

        val INTERVAL_OPTIONS = listOf(
            IntervalOption("10 秒", 10_000L),
            IntervalOption("30 秒", 30_000L),
            IntervalOption("1 分钟", 60_000L),
            IntervalOption("2 分钟", 120_000L),
            IntervalOption("3 分钟", 180_000L),
            IntervalOption("5 分钟", 300_000L)
        )

        val DISTANCE_OPTIONS = listOf(
            DistanceOption("5 米", 5.0),
            DistanceOption("10 米", 10.0),
            DistanceOption("20 米", 20.0),
            DistanceOption("30 米", 30.0),
            DistanceOption("50 米", 50.0),
            DistanceOption("100 米", 100.0),
            DistanceOption("200 米", 200.0),
            DistanceOption("500 米", 500.0),
            DistanceOption("1000 米", 1000.0)
        )
    }

    data class IntervalOption(val label: String, val value: Long)
    data class DistanceOption(val label: String, val value: Double)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupIntervalDropdown()
        setupDistanceDropdown()
        loadSettings()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupIntervalDropdown() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            INTERVAL_OPTIONS.map { it.label }
        )
        binding.actvInterval.setAdapter(adapter)
    }

    private fun setupDistanceDropdown() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            DISTANCE_OPTIONS.map { it.label }
        )
        binding.actvDistance.setAdapter(adapter)
    }

    private fun loadSettings() {
        val vibrateEnabled = prefs.getBoolean(KEY_VIBRATE, DEFAULT_VIBRATE)
        val interval = prefs.getLong(KEY_INTERVAL, DEFAULT_INTERVAL)
        val distance = prefs.getDouble(KEY_DISTANCE, DEFAULT_DISTANCE)

        binding.switchVibrate.isChecked = vibrateEnabled

        val intervalOption = INTERVAL_OPTIONS.find { it.value == interval } ?: INTERVAL_OPTIONS[1]
        binding.actvInterval.setText(intervalOption.label, false)

        val distanceOption = DISTANCE_OPTIONS.find { it.value == distance } ?: DISTANCE_OPTIONS[1]
        binding.actvDistance.setText(distanceOption.label, false)
    }

    private fun setupListeners() {
        binding.switchVibrate.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_VIBRATE, isChecked).apply()
        }

        binding.actvInterval.setOnItemClickListener { _, _, position, _ ->
            val interval = INTERVAL_OPTIONS[position].value
            prefs.edit().putLong(KEY_INTERVAL, interval).apply()
        }

        binding.actvDistance.setOnItemClickListener { _, _, position, _ ->
            val distance = DISTANCE_OPTIONS[position].value
            prefs.edit().putDouble(KEY_DISTANCE, distance).apply()
        }
    }

    private fun SharedPreferences.Editor.putDouble(key: String, value: Double): SharedPreferences.Editor {
        return putLong(key, java.lang.Double.doubleToRawLongBits(value))
    }

    private fun SharedPreferences.getDouble(key: String, defaultValue: Double): Double {
        return java.lang.Double.longBitsToDouble(getLong(key, java.lang.Double.doubleToRawLongBits(defaultValue)))
    }
}
