package com.zuxing.markmap

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.MyLocationData
import com.baidu.mapapi.map.Overlay
import com.baidu.mapapi.model.LatLng
import com.zuxing.markmap.data.entity.LineEntity
import com.zuxing.markmap.data.entity.PointEntity
import com.zuxing.markmap.databinding.ActivityMapBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapBinding

    private var lineId: Long = -1L
    private var currentLineName: String = "未选择"

    private lateinit var mapView: MapView
    private lateinit var locationClient: LocationClient
    private lateinit var app: MarkMapApplication
    private lateinit var vibrator: Vibrator
    private lateinit var prefs: android.content.SharedPreferences
    private var isFirstLocation = true
    private var isBackgroundLocationEnabled = false
    private var currentLocation: BDLocation? = null
    private var lastAutoSaveLat: Double? = null
    private var lastAutoSaveLng: Double? = null
    private var lastPointLat: Double? = null
    private var lastPointLng: Double? = null
    private var pointOverlays: MutableList<Overlay> = mutableListOf()

    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val vibratePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            enableBackgroundLocation()
        } else {
            Toast.makeText(this, "需要震动权限", Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            startLocation()
        } else {
            Toast.makeText(this, "需要定位权限", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lineId = intent.getLongExtra("lineId", -1L)

        app = application as MarkMapApplication
        mapView = binding.bmapView
        prefs = getSharedPreferences("settings", 0)

        vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        setupToolbar()
        mapView.map.isMyLocationEnabled = true

        mapView.map.setOnMapStatusChangeListener(object : com.baidu.mapapi.map.BaiduMap.OnMapStatusChangeListener {
            override fun onMapStatusChangeStart(mapStatus: com.baidu.mapapi.map.MapStatus?) {}
            override fun onMapStatusChangeStart(mapStatus: com.baidu.mapapi.map.MapStatus?, p1: Int) {}
            override fun onMapStatusChange(mapStatus: com.baidu.mapapi.map.MapStatus?) {}
            override fun onMapStatusChangeFinish(mapStatus: com.baidu.mapapi.map.MapStatus?) {
                updateCenterLocation()
            }
        })

        setupLocationClient()
        setupFloatingButtons()
        setupClickListeners()

        if (lineId != -1L) {
            binding.fabMark.visibility = View.VISIBLE
            binding.fabBackgroundLocation.visibility = View.VISIBLE
        } else {
            binding.fabMark.visibility = View.GONE
            binding.fabBackgroundLocation.visibility = View.GONE
        }

        if (checkPermissions()) {
            startLocation()
        } else {
            requestPermissions()
        }

        updateCenterLocation()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_map, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_select_line -> {
                showLineSelectionDialog()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showLineSelectionDialog() {
        lifecycleScope.launch {
            try {
                val lines = app.repository.getAllLines().first()
                if (lines.isEmpty()) {
                    Toast.makeText(this@MapActivity, "暂无路线，请先创建路线", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val lineNames = lines.map { it.name }.toTypedArray()
                AlertDialog.Builder(this@MapActivity)
                    .setTitle("选择路线")
                    .setItems(lineNames) { _, which ->
                        selectLine(lines[which])
                    }
                    .setNegativeButton("取消", null)
                    .setNeutralButton("清除选择") { _, _ ->
                        clearLineSelection()
                    }
                    .show()
            } catch (e: Exception) {
                Logger.e("获取路线列表失败: ${e.message}")
                Toast.makeText(this@MapActivity, "获取路线失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun selectLine(line: LineEntity) {
        lineId = line.id
        currentLineName = line.name
        binding.tvCurrentLine.text = "当前路线: $currentLineName"
        binding.fabMark.visibility = View.VISIBLE
        binding.fabBackgroundLocation.visibility = View.VISIBLE
        loadLinePoints()
    }

    private fun clearLineSelection() {
        lineId = -1L
        currentLineName = "未选择"
        binding.tvCurrentLine.text = "当前路线: 未选择"
        binding.fabMark.visibility = View.GONE
        binding.fabBackgroundLocation.visibility = View.GONE
        binding.tvDistanceToLast.text = "距上一个点: - 米"
        lastPointLat = null
        lastPointLng = null
        clearPointOverlays()
    }

    private fun loadLinePoints() {
        if (lineId == -1L) return

        lifecycleScope.launch {
            try {
                val points = app.repository.getPointsByLineId(lineId).first()
                displayLinePoints(points)
            } catch (e: Exception) {
                Logger.e("加载路线点失败: ${e.message}")
            }
        }
    }

    private fun displayLinePoints(points: List<PointEntity>) {
        clearPointOverlays()

        points.forEachIndexed { index, point ->
            val latLng = LatLng(point.latitude, point.longitude)
            val marker = mapView.map.addOverlay(
                MarkerOptions()
                    .position(latLng)
                    .title("点${index + 1}")
            ) as Overlay
            pointOverlays.add(marker)
        }

        if (points.isNotEmpty()) {
            val lastPoint = points.last()
            lastPointLat = lastPoint.latitude
            lastPointLng = lastPoint.longitude
            val ll = LatLng(lastPoint.latitude, lastPoint.longitude)
            val update = MapStatusUpdateFactory.newLatLng(ll)
            mapView.map.animateMapStatus(update)
        }
    }

    private fun clearPointOverlays() {
        pointOverlays.forEach { it.remove() }
        pointOverlays.clear()
    }

    private fun setupFloatingButtons() {
        binding.fabBackgroundLocation.setIcon(R.drawable.lock_24px)
        binding.fabLocation.setIcon(R.drawable.my_location_24px)
    }

    private fun updateCenterLocation() {
        val centerLatLng = mapView.map.mapStatus.target
        val sb = StringBuilder().apply {
            append("中心位置\n")
            append("纬度: ").append(String.format("%.6f", centerLatLng.latitude)).append("\n")
            append("经度: ").append(String.format("%.6f", centerLatLng.longitude))
        }
        binding.tvCenterLocation.text = sb.toString()
    }

    private fun setupLocationClient() {
        locationClient = LocationClient(this)

        locationClient.registerLocationListener(object : BDAbstractLocationListener() {
            override fun onReceiveLocation(location: BDLocation?) {
                location?.let {
                    runOnUiThread {
                        processLocation(it)
                    }
                }
            }

            override fun onLocDiagnosticMessage(locType: Int, diagnosticType: Int, diagnosticInfo: String?) {
                val msg = "定位诊断 - 类型: $locType, 诊断类型: $diagnosticType, 信息: $diagnosticInfo"
                Logger.d(msg)
            }

            override fun onReceiveLocString(locStr: String?) {
                locStr?.let {
                    Logger.d("定位字符串: $it")
                }
            }
        })

        val interval = prefs.getLong(SettingsActivity.KEY_INTERVAL, SettingsActivity.DEFAULT_INTERVAL)
        val option = LocationClientOption().apply {
            locationMode = LocationClientOption.LocationMode.Hight_Accuracy
            setCoorType("bd09ll")
            setScanSpan(interval.toInt())
            openGps = true
            isOpenGnss = true
            isLocationNotify = true
            setIgnoreKillProcess(true)
            SetIgnoreCacheException(false)
            setWifiCacheTimeOut(2 * 60 * 1000)
            enableSimulateGps = true
            setIsNeedAddress(true)
            setIsNeedAltitude(true)
            setIsNeedLocationDescribe(true)
            isNeedPoiRegion = true
            setIsNeedLocationPoiList(true)
        }
        locationClient.locOption = option

        Logger.d("定位客户端配置完成")
    }

    private fun setupClickListeners() {
        binding.fabLocation.setOnClickListener {
            if (checkPermissions()) {
                isFirstLocation = true
                startLocation()
            } else {
                requestPermissions()
            }
        }

        binding.fabBackgroundLocation.setOnClickListener {
            toggleBackgroundLocation()
        }

        binding.fabMark.setOnClickListener {
            if (lineId != -1L) {
                markPoint()
            }
        }

        binding.btnSaveCenter.setOnClickListener {
            if (lineId != -1L) {
                saveCenterPoint()
            } else {
                Toast.makeText(this, "请先选择路线", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun markPoint() {
        val location = currentLocation
        if (location == null) {
            Toast.makeText(this, "请先获取定位信息", Toast.LENGTH_SHORT).show()
            return
        }

        if (location.locType != BDLocation.TypeGpsLocation &&
            location.locType != BDLocation.TypeNetWorkLocation &&
            location.locType != BDLocation.TypeOffLineLocation
        ) {
            Toast.makeText(this, "定位失败，无法标记", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val maxSortOrder = getMaxSortOrder(lineId)
                val sortOrder = maxSortOrder + 1

                val point = PointEntity(
                    lineId = lineId,
                    longitude = location.longitude,
                    latitude = location.latitude,
                    altitude = if (location.hasAltitude()) location.altitude else null,
                    address = location.addrStr,
                    description = location.locationDescribe,
                    sortOrder = sortOrder,
                    createTime = System.currentTimeMillis(),
                    modifyTime = System.currentTimeMillis()
                )

                app.repository.insertPoint(point)
                Toast.makeText(this@MapActivity, "已保存", Toast.LENGTH_SHORT).show()
                lastPointLat = location.latitude
                lastPointLng = location.longitude
                loadLinePoints()
            } catch (e: Exception) {
                Logger.e("保存点失败: ${e.message}")
                Toast.makeText(this@MapActivity, "保存失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveCenterPoint() {
        val centerLatLng = mapView.map.mapStatus.target
        val latitude = centerLatLng.latitude
        val longitude = centerLatLng.longitude

        binding.btnSaveCenter.animate()
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(150)
            .withEndAction {
                binding.btnSaveCenter.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(150)
                    .start()
            }
            .start()

        binding.btnSaveCenter.isEnabled = false
        binding.btnSaveCenter.text = "保存中..."

        fetchAddressAndSave(latitude, longitude)
    }

    private fun fetchAddressAndSave(latitude: Double, longitude: Double) {
        val retrofit = Retrofit.Builder()
            .baseUrl(BaiduConfig.baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(BaiduPlaceService::class.java)

        service.reverseGeocoding(
            latitude = latitude.toString(),
            longitude = longitude.toString(),
        ).enqueue(object : Callback<ReverseGeocodingResponse> {
            override fun onResponse(call: Call<ReverseGeocodingResponse>, response: Response<ReverseGeocodingResponse>) {
                var address = ""
                var description = ""

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result != null && result.result == 0) {
                        address = result.data?.address ?: ""
                        description = result.data?.description ?: ""
                    } else {
                        Toast.makeText(this@MapActivity, result?.message ?: "未知错误", Toast.LENGTH_SHORT).show()
                    }
                }

                savePointToDatabase(latitude, longitude, address, description)
            }

            override fun onFailure(call: Call<ReverseGeocodingResponse>, t: Throwable) {
                Logger.e("获取地址失败: ${t.message}")
                savePointToDatabase(latitude, longitude, "", "")
            }
        })
    }

    private fun savePointToDatabase(latitude: Double, longitude: Double, address: String, description: String) {
        lifecycleScope.launch {
            try {
                val maxSortOrder = getMaxSortOrder(lineId)
                val sortOrder = maxSortOrder + 1

                val point = PointEntity(
                    lineId = lineId,
                    longitude = longitude,
                    latitude = latitude,
                    altitude = null,
                    address = address.ifEmpty { null },
                    description = description.ifEmpty { null },
                    sortOrder = sortOrder,
                    createTime = System.currentTimeMillis(),
                    modifyTime = System.currentTimeMillis()
                )

                app.repository.insertPoint(point)

                runOnUiThread {
                    binding.btnSaveCenter.animate()
                        .scaleX(1.2f)
                        .scaleY(1.2f)
                        .setDuration(200)
                        .withEndAction {
                            binding.btnSaveCenter.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(200)
                                .start()
                        }
                        .start()

                    binding.btnSaveCenter.text = "已保存"
                    Toast.makeText(this@MapActivity, "位置已保存", Toast.LENGTH_SHORT).show()
                    lastPointLat = latitude
                    lastPointLng = longitude
                    loadLinePoints()

                    binding.btnSaveCenter.postDelayed({
                        binding.btnSaveCenter.text = "保存"
                        binding.btnSaveCenter.isEnabled = true
                    }, 2000)
                }
            } catch (e: Exception) {
                Logger.e("保存点失败: ${e.message}")
                runOnUiThread {
                    binding.btnSaveCenter.text = "保存"
                    binding.btnSaveCenter.isEnabled = true
                    Toast.makeText(this@MapActivity, "保存失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun getMaxSortOrder(lineId: Long): Int {
        return try {
            val points = app.repository.getPointsByLineId(lineId).first()
            points.maxOfOrNull { it.sortOrder } ?: 0
        } catch (e: Exception) {
            Logger.e("获取排序失败: ${e.message}")
            0
        }
    }

    private fun toggleBackgroundLocation() {
        if (isBackgroundLocationEnabled) {
            disableBackgroundLocation()
        } else {
            checkVibratePermission()
        }
    }

    private fun checkVibratePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.VIBRATE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            enableBackgroundLocation()
        } else {
            vibratePermissionLauncher.launch(Manifest.permission.VIBRATE)
        }
    }

    private fun enableBackgroundLocation() {
        isBackgroundLocationEnabled = true
        isFirstLocation = true
        lastAutoSaveLat = null
        lastAutoSaveLng = null
        binding.fabBackgroundLocation.setIcon(R.drawable.lock_open_48px)
        binding.fabBackgroundLocation.setIconTintAnim()
        Toast.makeText(this, "自动保存已开启", Toast.LENGTH_SHORT).show()
    }

    private fun disableBackgroundLocation() {
        isBackgroundLocationEnabled = false
        binding.fabBackgroundLocation.setIcon(R.drawable.lock_24px)
        binding.fabBackgroundLocation.setIconTint(Color.WHITE)
        Toast.makeText(this, "自动保存已关闭", Toast.LENGTH_SHORT).show()
    }

    private fun checkPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        permissionLauncher.launch(requiredPermissions)
    }

    private fun startLocation() {
        binding.progressBar.visibility = View.VISIBLE
        Logger.d("开始定位, isStarted=${locationClient.isStarted}")
        if (!locationClient.isStarted) {
            val result = locationClient.start()
            Logger.d("start() 返回: $result")
        }
    }

    private fun processLocation(location: BDLocation) {
        binding.progressBar.visibility = View.GONE
        currentLocation = location

        if (location.locType == BDLocation.TypeGpsLocation ||
            location.locType == BDLocation.TypeNetWorkLocation ||
            location.locType == BDLocation.TypeOffLineLocation
        ) {

            binding.tvLocationInfo.text = StringBuilder().apply {
                append("定位时间: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())).append("\n")
                append("纬度: ").append(location.latitude).append("\n")
                append("经度: ").append(location.longitude).append("\n")
                append("海拔: ").append(if (location.hasAltitude()) "${location.altitude}m" else "未知")
            }.toString()
            binding.tvLocationAddress.text = StringBuilder().apply {
                append("地址: ").append(location.addrStr).append("\n")
                location.locationDescribe?.let {
                    append("位置描述: ").append(it)
                }
            }.toString()

            val distance = lastPointLat?.let { lastLat ->
                lastPointLng?.let { lastLng ->
                    Utils.calculateDistance(lastLat, lastLng, location.latitude, location.longitude)
                }
            }

            binding.tvDistanceToLast.text = if (distance != null) {
                String.format("距上一个点: %.1f 米", distance)
            } else {
                "距上一个点: - 米"
            }

            val locData = MyLocationData.Builder()
                .accuracy(location.radius)
                .latitude(location.latitude)
                .longitude(location.longitude)
                .build()
            mapView.map.setMyLocationData(locData)

            if (isFirstLocation) {
                isFirstLocation = false
                val ll = LatLng(location.latitude, location.longitude)
                val update = MapStatusUpdateFactory.newLatLng(ll)
                mapView.map.animateMapStatus(update)
            }

            if (isBackgroundLocationEnabled) {
                checkAndAutoSavePoint(location)
            }

            binding.fabMark.visibility = View.VISIBLE
        } else {
            binding.tvLocationInfo.text = "定位失败，错误码: ${location.locType}"
            binding.tvLocationAddress.text = ""
            binding.fabMark.visibility = View.GONE
        }
    }

    private fun checkAndAutoSavePoint(location: BDLocation) {
        if (lineId == -1L) return

        val lat = location.latitude
        val lng = location.longitude
        val distanceThreshold = prefs.getFloat(SettingsActivity.KEY_DISTANCE, SettingsActivity.DEFAULT_DISTANCE.toFloat()).toDouble()

        val distance = lastAutoSaveLat?.let { lastLat ->
            lastAutoSaveLng?.let { lastLng ->
                Utils.calculateDistance(lastLat, lastLng, lat, lng)
            }
        } ?: Double.MAX_VALUE

        if (distance >= distanceThreshold.toDouble()) {
            lifecycleScope.launch {
                try {
                    val maxSortOrder = getMaxSortOrder(lineId)
                    val point = PointEntity(
                        lineId = lineId,
                        longitude = lng,
                        latitude = lat,
                        altitude = if (location.hasAltitude()) location.altitude else null,
                        address = location.addrStr,
                        description = location.locationDescribe,
                        sortOrder = maxSortOrder + 1,
                        createTime = System.currentTimeMillis(),
                        modifyTime = System.currentTimeMillis()
                    )
                    app.repository.insertPoint(point)
                    Logger.d("自动保存点成功, 距离=$distance")

                    lastAutoSaveLat = lat
                    lastAutoSaveLng = lng
                    lastPointLat = lat
                    lastPointLng = lng
                    vibrate()
                    loadLinePoints()
                } catch (e: Exception) {
                    Logger.e("自动保存点失败: ${e.message}")
                }
            }
        } else {
            Logger.d("距离太近($distance < $distanceThreshold), 不保存")
        }
    }

    private fun vibrate() {
        val vibrateEnabled = prefs.getBoolean(SettingsActivity.KEY_VIBRATE, SettingsActivity.DEFAULT_VIBRATE)
        if (vibrateEnabled) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
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
        mapView.map.isMyLocationEnabled = false
        locationClient.stop()
        mapView.onDestroy()
    }
}
