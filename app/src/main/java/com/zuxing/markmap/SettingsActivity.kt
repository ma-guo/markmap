package com.zuxing.markmap

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
        const val DEFAULT_VIBRATE = true
        const val DEFAULT_INTERVAL = 30_000L

        val INTERVAL_OPTIONS = listOf(
            IntervalOption("10 秒", 10_000L),
            IntervalOption("30 秒", 30_000L),
            IntervalOption("1 分钟", 60_000L),
            IntervalOption("2 分钟", 120_000L),
            IntervalOption("3 分钟", 180_000L),
            IntervalOption("5 分钟", 300_000L)
        )
    }

    data class IntervalOption(val label: String, val value: Long)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupIntervalDropdown()
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

    private fun loadSettings() {
        val vibrateEnabled = prefs.getBoolean(KEY_VIBRATE, DEFAULT_VIBRATE)
        val interval = prefs.getLong(KEY_INTERVAL, DEFAULT_INTERVAL)

        binding.switchVibrate.isChecked = vibrateEnabled

        val option = INTERVAL_OPTIONS.find { it.value == interval } ?: INTERVAL_OPTIONS[1]
        binding.actvInterval.setText(option.label, false)
    }

    private fun setupListeners() {
        binding.switchVibrate.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_VIBRATE, isChecked).apply()
        }

        binding.actvInterval.setOnItemClickListener { _, _, position, _ ->
            val interval = INTERVAL_OPTIONS[position].value
            prefs.edit().putLong(KEY_INTERVAL, interval).apply()
        }
    }
}
