package com.zuxing.markmap

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.zuxing.markmap.data.entity.PointEntity
import com.zuxing.markmap.databinding.FragmentPointEditBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PointEditActivity : AppCompatActivity() {

    private lateinit var binding: FragmentPointEditBinding
    private lateinit var app: MarkMapApplication
    private var currentPoint: PointEntity? = null
    private var isEditMode: Boolean = false
    private var lineId: Long = -1L
    private var pointId: Long = -1L
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0

    private val pickLocationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                val latitude = data.getDoubleExtra("latitude", 0.0)
                val longitude = data.getDoubleExtra("longitude", 0.0)
                val address = data.getStringExtra("address") ?: ""
                val description = data.getStringExtra("description") ?: ""

                if (latitude != 0.0 && longitude != 0.0) {
                    currentLatitude = latitude
                    currentLongitude = longitude

                    binding.etLongitude.setText(String.format("%.6f", currentLongitude))
                    binding.etLatitude.setText(String.format("%.6f", currentLatitude))
                    if (address.isNotEmpty()) {
                        binding.etAddress.setText(address)
                    }
                    if (description.isNotEmpty()) {
                        binding.etDescription.setText(description)
                    }

                    updateSaveButtonState()
                    Toast.makeText(this, "位置已选择", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentPointEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as MarkMapApplication

        lineId = intent.getLongExtra("lineId", -1L)
        pointId = intent.getLongExtra("pointId", -1L)
        isEditMode = pointId != -1L

        setupToolbar()
        setupViews()

        if (isEditMode) {
            loadPoint()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_point_edit, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete -> {
                showDeleteConfirmationDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("删除确认")
            .setMessage("确定要删除这个点吗？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                deletePoint()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deletePoint() {
        if (!isEditMode || currentPoint == null) {
            Toast.makeText(this, "无法删除", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                app.repository.deletePoint(currentPoint!!)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PointEditActivity, "删除成功", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Logger.e("删除点失败: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PointEditActivity, "删除失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupViews() {
        binding.etLongitude.addTextChangedListener { updateSaveButtonState() }
        binding.etLatitude.addTextChangedListener { updateSaveButtonState() }

        binding.btnSelectOnMap.setOnClickListener {
            navigateToPickLocation()
        }

        binding.btnFetchFromBaidu.setOnClickListener {
            fetchFromBaidu()
        }

        binding.btnSave.setOnClickListener {
            savePoint()
        }
    }

    private fun updateSaveButtonState() {
        val longitude = binding.etLongitude.text.toString().toDoubleOrNull()
        val latitude = binding.etLatitude.text.toString().toDoubleOrNull()
        binding.btnSave.isEnabled = longitude != null && latitude != null
    }

    private fun navigateToPickLocation() {
        val longitude = binding.etLongitude.text.toString().toDoubleOrNull() ?: currentLongitude
        val latitude = binding.etLatitude.text.toString().toDoubleOrNull() ?: currentLatitude

        val intent = Intent(this, PickLocationActivity::class.java).apply {
            putExtra("latitude", latitude)
            putExtra("longitude", longitude)
            putExtra("lineId", lineId)
            putExtra("pointId", pointId)
        }
        pickLocationLauncher.launch(intent)
    }

    private fun loadPoint() {
        lifecycleScope.launch(Dispatchers.IO) {
            currentPoint = app.repository.getPointById(pointId)
            val line = app.repository.getLineById(lineId)
            withContext(Dispatchers.Main) {
                currentPoint?.let { point ->
                    binding.etLongitude.setText(String.format("%.6f", point.longitude))
                    binding.etLatitude.setText(String.format("%.6f", point.latitude))
                    point.altitude?.let { binding.etAltitude.setText(String.format("%.1f", it)) }
                    binding.etAddress.setText(point.address ?: "")
                    binding.etDescription.setText(point.description ?: "")
                    binding.etSortOrder.setText(point.sortOrder.toString())
                    binding.tvLineName.text = "所属线: ${line?.name ?: ""}"
                    binding.tvLineName.visibility = android.view.View.VISIBLE
                    binding.tvCreateTime.text = "创建时间: ${formatTime(point.createTime)}"
                    binding.tvCreateTime.visibility = android.view.View.VISIBLE

                    currentLatitude = point.latitude
                    currentLongitude = point.longitude

                    updateSaveButtonState()
                }
            }
        }
    }

    private fun savePoint() {
        val longitude = binding.etLongitude.text.toString().toDoubleOrNull()
        val latitude = binding.etLatitude.text.toString().toDoubleOrNull()

        if (longitude == null || latitude == null) {
            Toast.makeText(this, "请填写经纬度", Toast.LENGTH_SHORT).show()
            return
        }

        val altitude = binding.etAltitude.text.toString().toDoubleOrNull()
        val address = binding.etAddress.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val sortOrder = binding.etSortOrder.text.toString().toIntOrNull() ?: 0

        lifecycleScope.launch(Dispatchers.IO) {
            val point = PointEntity(
                id = if (isEditMode) pointId else 0,
                lineId = lineId,
                longitude = longitude,
                latitude = latitude,
                altitude = altitude,
                address = address.ifBlank { null },
                description = description.ifBlank { null },
                sortOrder = sortOrder,
                createTime = currentPoint?.createTime ?: System.currentTimeMillis(),
                modifyTime = System.currentTimeMillis()
            )

            if (isEditMode) {
                app.repository.updatePoint(point)
            } else {
                app.repository.insertPoint(point)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@PointEditActivity, "保存成功", Toast.LENGTH_SHORT).show()
//                finish()
            }
        }
    }

    private fun fetchFromBaidu() {
        val latitude = binding.etLatitude.text.toString().toDoubleOrNull()
        val longitude = binding.etLongitude.text.toString().toDoubleOrNull()

        if (latitude == null || longitude == null) {
            Toast.makeText(this, "请先填写经纬度", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnFetchFromBaidu.isEnabled = false
        binding.btnFetchFromBaidu.text = "获取中..."

        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl(BaiduConfig.baseUrl)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()

        val service = retrofit.create(BaiduPlaceService::class.java)

        service.reverseGeocoding(
            latitude = latitude.toString(),
            longitude = longitude.toString(),
        ).enqueue(object : retrofit2.Callback<ReverseGeocodingResponse> {
            override fun onResponse(call: retrofit2.Call<ReverseGeocodingResponse>, response: retrofit2.Response<ReverseGeocodingResponse>) {
                binding.btnFetchFromBaidu.isEnabled = true
                binding.btnFetchFromBaidu.text = "从百度获取"

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result != null && result.result == 0) {
                        result.data?.let { geocodingResult ->
                            binding.etAddress.setText(geocodingResult.address ?: "")

                            binding.etDescription.setText(geocodingResult.description?:"")
                            Toast.makeText(this@PointEditActivity, "获取成功", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@PointEditActivity, "获取失败: ${result?.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@PointEditActivity, "获取失败", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: retrofit2.Call<ReverseGeocodingResponse>, t: Throwable) {
                binding.btnFetchFromBaidu.isEnabled = true
                binding.btnFetchFromBaidu.text = "从百度获取"
                Logger.e("从百度获取失败: ${t.message}")
                Toast.makeText(this@PointEditActivity, "获取失败: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun formatTime(time: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(time))
    }
}
