package com.zuxing.markmap

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
                finish()
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
            .baseUrl("https://api.map.baidu.com/")
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()

        val service = retrofit.create(BaiduPlaceService::class.java)

        service.reverseGeocoding(
            location = "$latitude,$longitude",
            output = "json",
            ak = BaiduConfig.API_KEY
        ).enqueue(object : retrofit2.Callback<ReverseGeocodingResponse> {
            override fun onResponse(call: retrofit2.Call<ReverseGeocodingResponse>, response: retrofit2.Response<ReverseGeocodingResponse>) {
                binding.btnFetchFromBaidu.isEnabled = true
                binding.btnFetchFromBaidu.text = "从百度获取"

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result != null && result.status == "0") {
                        result.result?.let { geocodingResult ->
                            binding.etAddress.setText(geocodingResult.formattedAddress ?: "")

                            geocodingResult.pois?.firstOrNull()?.let { poi ->
                                binding.etDescription.setText(poi.name ?: "")
                            } ?: geocodingResult.sematicDescription?.let { desc ->
                                if (binding.etDescription.text.isNullOrBlank()) {
                                    binding.etDescription.setText(desc)
                                }
                            }

                            geocodingResult.location?.let { loc ->
                                loc.altitude?.let { altitude ->
                                    binding.etAltitude.setText(String.format("%.1f", altitude))
                                }
                            }

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

    interface BaiduPlaceService {
        @retrofit2.http.GET("geocoding/v3/")
        fun reverseGeocoding(
            @retrofit2.http.Query("location") location: String,
            @retrofit2.http.Query("output") output: String,
            @retrofit2.http.Query("ak") ak: String
        ): retrofit2.Call<ReverseGeocodingResponse>
    }

    data class ReverseGeocodingResponse(
        val status: String,
        val message: String?,
        val result: GeocodingResult?
    )

    data class GeocodingResult(
        val formattedAddress: String?,
        val location: LocationInfo?,
        val pois: List<PoiInfo>?,
        val sematicDescription: String?
    )

    data class LocationInfo(
        val lat: Double?,
        val lng: Double?,
        val altitude: Double?
    )

    data class PoiInfo(
        val name: String?,
        val address: String?
    )
}
