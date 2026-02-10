package com.zuxing.markmap

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MapStatus
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.model.LatLng
import com.zuxing.markmap.data.entity.PointEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.Callback
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

class PickLocationActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var selectedLatitude: Double = 0.0
    private var selectedLongitude: Double = 0.0
    private var selectedAddress: String = ""
    private var selectedDescription: String = ""
    private var originalLatitude: Double = 0.0
    private var originalLongitude: Double = 0.0
    private var marker: Marker? = null
    private var hasOriginalMarker = false

    private var lineId: Long = -1L
    private var pointId: Long = -1L
    private var isEditMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pick_location)

        lineId = intent.getLongExtra("lineId", -1L)
        pointId = intent.getLongExtra("pointId", -1L)
        isEditMode = pointId != -1L

        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)

        setupToolbar()
        setupMap()

        if (latitude != 0.0 && longitude != 0.0) {
            originalLatitude = latitude
            originalLongitude = longitude
            selectedLatitude = latitude
            selectedLongitude = longitude
            addOriginalMarker(latitude, longitude)
            centerMap(latitude, longitude)
            updateSelectedLocationInfo()
        } else if (isEditMode) {
            loadExistingPoint()
        } else {
            Toast.makeText(this, "无法获取位置", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        toolbar.title = "选择位置"
    }

    private fun setupMap() {
        mapView = findViewById(R.id.mapView)
        mapView.map.setOnMapStatusChangeListener(object : BaiduMap.OnMapStatusChangeListener {
            override fun onMapStatusChangeStart(mapStatus: MapStatus?) {}
            override fun onMapStatusChangeStart(mapStatus: MapStatus?, p1: Int) {}
            override fun onMapStatusChange(mapStatus: MapStatus?) {}
            override fun onMapStatusChangeFinish(mapStatus: MapStatus?) {
                updateSelectedLocationInfo()
            }
        })
    }

    private fun loadExistingPoint() {
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progressBar)
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val app = application as MarkMapApplication
                val existingPoint = app.repository.getPointById(pointId)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    existingPoint?.let { point ->
                        originalLatitude = point.latitude
                        originalLongitude = point.longitude
                        selectedLatitude = point.latitude
                        selectedLongitude = point.longitude
                        selectedAddress = point.address ?: ""
                        selectedDescription = point.description ?: ""

                        addOriginalMarker(point.latitude, point.longitude)
                        centerMap(point.latitude, point.longitude)
                        updateSelectedLocationInfo()
                    } ?: run {
                        Toast.makeText(this@PickLocationActivity, "点不存在", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                Logger.e("加载点失败: ${e.message}")
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@PickLocationActivity, "加载失败", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun centerMap(latitude: Double, longitude: Double) {
        val update = MapStatusUpdateFactory.newLatLng(LatLng(latitude, longitude))
        mapView.map.animateMapStatus(update)
    }

    private fun addOriginalMarker(latitude: Double, longitude: Double) {
        val point = LatLng(latitude, longitude)
        val options = MarkerOptions()
            .position(point)
            .icon(BitmapDescriptorFactory.fromResource(android.R.drawable.ic_menu_mylocation))
            .title("原始位置")
        marker = mapView.map.addOverlay(options) as Marker
        hasOriginalMarker = true
    }

    private fun updateSelectedLocationInfo() {
        val centerLatLng = mapView.map.mapStatus.target
        selectedLatitude = centerLatLng.latitude
        selectedLongitude = centerLatLng.longitude

        val tvSelectedLocation = findViewById<android.widget.TextView>(R.id.tvSelectedLocation)
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progressBar)

        progressBar.visibility = View.VISIBLE
        tvSelectedLocation.text = "获取位置信息中..."

        fetchAddress(selectedLatitude, selectedLongitude)
    }

    private fun fetchAddress(latitude: Double, longitude: Double) {
        val retrofit = Retrofit.Builder()
            .baseUrl(BaiduConfig.baseUrl)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()

        val service = retrofit.create(BaiduPlaceService::class.java)

        service.reverseGeocoding(
            latitude = latitude.toString(),
            longitude = longitude.toString(),
        ).enqueue(object : Callback<ReverseGeocodingResponse> {
            override fun onResponse(call: retrofit2.Call<ReverseGeocodingResponse>, response: Response<ReverseGeocodingResponse>) {
                val progressBar = findViewById<android.widget.ProgressBar>(R.id.progressBar)
                val tvSelectedLocation = findViewById<android.widget.TextView>(R.id.tvSelectedLocation)

                progressBar.visibility = View.GONE

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result != null && result.result == 0) {
                        val data = result.data
                        selectedAddress = data?.address ?: ""
                        selectedDescription = data?.description ?: ""

                        val prefix = if (hasOriginalMarker && (latitude != originalLatitude || longitude != originalLongitude)) {
                            "新位置\n"
                        } else {
                            ""
                        }
                        tvSelectedLocation.text = buildString {
                            append(prefix)
                            append("位置: ${selectedAddress}\n")
                            append("地址: ${selectedDescription}\n")
                            append("坐标: ${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}")
                        }
                    } else {
                        selectedAddress = ""
                        selectedDescription = ""
                        val prefix = if (hasOriginalMarker && (latitude != originalLatitude || longitude != originalLongitude)) {
                            "新位置\n"
                        } else {
                            ""
                        }
                        tvSelectedLocation.text = buildString {
                            append(prefix)
                            append("坐标: ${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}\n")
                            append("(未找到附近地点信息)")
                        }
                    }
                } else {
                    selectedAddress = ""
                    selectedDescription = ""
                    val prefix = if (hasOriginalMarker && (latitude != originalLatitude || longitude != originalLongitude)) {
                        "新位置\n"
                    } else {
                        ""
                    }
                    tvSelectedLocation.text = buildString {
                        append(prefix)
                        append("坐标: ${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}\n")
                        append("(获取地址失败)")
                    }
                }
            }

            override fun onFailure(call: retrofit2.Call<ReverseGeocodingResponse>, t: Throwable) {
                val progressBar = findViewById<android.widget.ProgressBar>(R.id.progressBar)
                val tvSelectedLocation = findViewById<android.widget.TextView>(R.id.tvSelectedLocation)

                progressBar.visibility = View.GONE
                selectedAddress = ""
                selectedDescription = ""

                val prefix = if (hasOriginalMarker && (latitude != originalLatitude || longitude != originalLongitude)) {
                    "新位置\n"
                } else {
                    ""
                }
                tvSelectedLocation.text = buildString {
                    append(prefix)
                    append("坐标: ${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}\n")
                    append("(网络错误)")
                }
            }
        })
    }

    fun onConfirmClick(view: View) {
        if (selectedLatitude == 0.0 && selectedLongitude == 0.0) {
            Toast.makeText(this, "请先选择位置", Toast.LENGTH_SHORT).show()
            return
        }

        val resultIntent = Intent().apply {
            putExtra("latitude", selectedLatitude)
            putExtra("longitude", selectedLongitude)
            putExtra("address", selectedAddress)
            putExtra("description", selectedDescription)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }
}