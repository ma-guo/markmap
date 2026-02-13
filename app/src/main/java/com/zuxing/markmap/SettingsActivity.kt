package com.zuxing.markmap

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.zuxing.markmap.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val prefs by lazy { getSharedPreferences("settings", 0) }

    private var pendingVibrate: Boolean = true
    private var pendingInterval: Long = DEFAULT_INTERVAL
    private var pendingDistance: Long = DEFAULT_DISTANCE

    companion object {
        const val KEY_VIBRATE = "vibrate_enabled"
        const val KEY_INTERVAL = "location_interval"
        const val KEY_DISTANCE = "location_distance"
        const val DEFAULT_VIBRATE = true
        const val DEFAULT_INTERVAL = 30_000L
        const val DEFAULT_DISTANCE = 10L

        val INTERVAL_OPTIONS = listOf(
            IntervalOption("5 秒", 5_000L),
            IntervalOption("10 秒", 10_000L),
            IntervalOption("20 秒", 20_000L),
            IntervalOption("30 秒", 30_000L),
            IntervalOption("1 分钟", 60_000L),
            IntervalOption("2 分钟", 120_000L),
            IntervalOption("3 分钟", 180_000L),
            IntervalOption("5 分钟", 300_000L)
        )

        val DISTANCE_OPTIONS = listOf(
            DistanceOption("5 米", 5),
            DistanceOption("10 米", 10),
            DistanceOption("20 米", 20),
            DistanceOption("30 米", 30),
            DistanceOption("50 米", 50),
            DistanceOption("100 米", 100),
            DistanceOption("200 米", 200),
            DistanceOption("500 米", 500),
            DistanceOption("1000 米", 1000),
            DistanceOption("2000 米", 2000),
            DistanceOption("3000 米", 3000),
            DistanceOption("5000 米", 5000),

        )
    }

    data class IntervalOption(val label: String, val value: Long)
    data class DistanceOption(val label: String, val value: Long)

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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> {
                saveSettings()
                true
            }
            else -> super.onOptionsItemSelected(item)
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
        val distance = prefs.getLong(KEY_DISTANCE, DEFAULT_DISTANCE)

        pendingVibrate = vibrateEnabled
        pendingInterval = interval
        pendingDistance = distance

        binding.switchVibrate.isChecked = vibrateEnabled

        val intervalOption = INTERVAL_OPTIONS.find { it.value == interval } ?: INTERVAL_OPTIONS[1]
        binding.actvInterval.setText(intervalOption.label, false)

        val distanceOption = DISTANCE_OPTIONS.find { it.value == distance } ?: DISTANCE_OPTIONS[1]
        binding.actvDistance.setText(distanceOption.label, false)
    }

    private fun setupListeners() {
        binding.switchVibrate.setOnCheckedChangeListener { _, isChecked ->
            pendingVibrate = isChecked
        }

        binding.actvInterval.setOnItemClickListener { _, _, position, _ ->
            pendingInterval = INTERVAL_OPTIONS[position].value
        }

        binding.actvDistance.setOnItemClickListener { _, _, position, _ ->
            pendingDistance = DISTANCE_OPTIONS[position].value
        }
    }

    private fun saveSettings() {
        prefs.edit().apply {
            putBoolean(KEY_VIBRATE, pendingVibrate)
            putLong(KEY_INTERVAL, pendingInterval)
            putLong(KEY_DISTANCE, pendingDistance)
        }.apply()
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
    }
}
