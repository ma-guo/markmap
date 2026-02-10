package com.zuxing.markmap

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.navArgs
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.MyLocationData
import com.baidu.mapapi.model.LatLng
import com.zuxing.markmap.data.entity.PointEntity
import com.zuxing.markmap.databinding.FragmentMapBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val args: MapFragmentArgs by navArgs()

    private lateinit var mapView: MapView
    private lateinit var locationClient: LocationClient
    private lateinit var app: MarkMapApplication
    private var isFirstLocation = true
    private var isBackgroundLocationEnabled = false
    private var currentLocation: BDLocation? = null

    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            startLocation()
        } else {
            Toast.makeText(requireContext(), "需要定位权限", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        app = requireActivity().application as MarkMapApplication
        mapView = binding.bmapView

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
        setupClickListeners()

        if (args.lineId != -1L) {
            binding.fabMark.visibility = View.VISIBLE
        } else {
            binding.fabMark.visibility = View.GONE
        }

        if (checkPermissions()) {
            startLocation()
        } else {
            requestPermissions()
        }

        updateCenterLocation()
    }

    private fun updateCenterLocation() {
        if (!isAdded || _binding == null) return
        val centerLatLng = mapView.map.mapStatus.target
        val sb = StringBuilder().apply {
            append("中心位置\n")
            append("纬度: ").append(String.format("%.6f", centerLatLng.latitude)).append("\n")
            append("经度: ").append(String.format("%.6f", centerLatLng.longitude))
        }
        binding.tvCenterLocation.text = sb.toString()
    }

    private fun setupLocationClient() {
        locationClient = LocationClient(requireContext())

        locationClient.registerLocationListener(object : BDAbstractLocationListener() {
            override fun onReceiveLocation(location: BDLocation?) {
                location?.let {
                    activity?.runOnUiThread {
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

        val option = LocationClientOption().apply {
            locationMode = LocationClientOption.LocationMode.Hight_Accuracy
            setCoorType("bd09ll")
            setScanSpan(1000)
            openGps = true
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
            if (args.lineId != -1L) {
                markPoint()
            }
        }

        binding.btnSaveCenter.setOnClickListener {
            if (args.lineId != -1L) {
                saveCenterPoint()
            } else {
                Toast.makeText(requireContext(), "请先选择路线", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun markPoint() {
        val location = currentLocation
        if (location == null) {
            Toast.makeText(requireContext(), "请先获取定位信息", Toast.LENGTH_SHORT).show()
            return
        }

        if (location.locType != BDLocation.TypeGpsLocation &&
            location.locType != BDLocation.TypeNetWorkLocation &&
            location.locType != BDLocation.TypeOffLineLocation) {
            Toast.makeText(requireContext(), "定位失败，无法标记", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                try {
                    val maxSortOrder = getMaxSortOrder(args.lineId)
                    val sortOrder = maxSortOrder + 1

                    val point = PointEntity(
                        lineId = args.lineId,
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
                    Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Logger.e("保存点失败: ${e.message}")
                    Toast.makeText(requireContext(), "保存失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveCenterPoint() {
        if (!isAdded || _binding == null) return
        val centerLatLng = mapView.map.mapStatus.target
        val latitude = centerLatLng.latitude
        val longitude = centerLatLng.longitude

        binding.btnSaveCenter.animate()
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(150)
            .withEndAction {
                if (isAdded && _binding != null) {
                    binding.btnSaveCenter.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(150)
                        .start()
                }
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
                    if (result != null && result.result == 0 ) {
                        address = result.data?.address?:""
                        description = result.data?.description?:""
                    } else {
                        Toast.makeText(requireContext(), result?.message?:"未知错误", Toast.LENGTH_SHORT).show()
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
                val maxSortOrder = getMaxSortOrder(args.lineId)
                val sortOrder = maxSortOrder + 1

                val point = PointEntity(
                    lineId = args.lineId,
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

                activity?.runOnUiThread {
                    if (!isAdded || _binding == null) return@runOnUiThread
                    binding.btnSaveCenter.animate()
                        .scaleX(1.2f)
                        .scaleY(1.2f)
                        .setDuration(200)
                        .withEndAction {
                            if (!isAdded || _binding == null) return@withEndAction
                            binding.btnSaveCenter.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(200)
                                .start()
                        }
                        .start()

                    binding.btnSaveCenter.text = "已保存"
                    Toast.makeText(requireContext(), "位置已保存", Toast.LENGTH_SHORT).show()

                    binding.btnSaveCenter.postDelayed({
                        if (!isAdded || _binding == null) return@postDelayed
                        binding.btnSaveCenter.text = "保存"
                        binding.btnSaveCenter.isEnabled = true
                    }, 2000)
                }
            } catch (e: Exception) {
                Logger.e("保存点失败: ${e.message}")
                activity?.runOnUiThread {
                    if (!isAdded || _binding == null) return@runOnUiThread
                    binding.btnSaveCenter.text = "保存"
                    binding.btnSaveCenter.isEnabled = true
                    Toast.makeText(requireContext(), "保存失败", Toast.LENGTH_SHORT).show()
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
        isBackgroundLocationEnabled = !isBackgroundLocationEnabled
        if (isBackgroundLocationEnabled) {
            LocationService.start(requireContext())
            Toast.makeText(requireContext(), "后台定位已开启", Toast.LENGTH_SHORT).show()
        } else {
            LocationService.stop(requireContext())
            Toast.makeText(requireContext(), "后台定位已关闭", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        permissionLauncher.launch(requiredPermissions)
    }

    private fun startLocation() {
        if (!isAdded || _binding == null) return
        binding.progressBar.visibility = View.VISIBLE
        Logger.d("开始定位, isStarted=${locationClient.isStarted}")
        if (!locationClient.isStarted) {
            val result = locationClient.start()
            Logger.d("start() 返回: $result")
        }
    }

    private fun processLocation(location: BDLocation) {
        if (!isAdded || _binding == null) return
        binding.progressBar.visibility = View.GONE
        currentLocation = location

        if (location.locType == BDLocation.TypeGpsLocation ||
            location.locType == BDLocation.TypeNetWorkLocation ||
            location.locType == BDLocation.TypeOffLineLocation) {

            val sb = StringBuilder().apply {
                append("定位时间: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())).append("\n")
                append("纬度: ").append(location.latitude).append("\n")
                append("经度: ").append(location.longitude).append("\n")
                append("海拔: ").append(if (location.hasAltitude()) "${location.altitude}m" else "未知").append("\n")
                append("地址: ").append(location.addrStr).append("\n")
                append("位置描述: ").append(location.locationDescribe).append("\n")
            }
            binding.tvLocationInfo.text = sb.toString()

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

            fetchNearbyPlaces(location.latitude, location.longitude)
        } else {
            binding.tvLocationInfo.text = "定位失败，错误码: ${location.locType}"
        }
    }

    private fun fetchNearbyPlaces(latitude: Double, longitude: Double) {
        val retrofit = Retrofit.Builder()
            .baseUrl(BaiduConfig.baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(BaiduPlaceService::class.java)

        service.reverseGeocoding(
            longitude = longitude.toString(),
            latitude = latitude.toString(),
        ).enqueue(object : Callback<ReverseGeocodingResponse> {
            override fun onResponse(call: Call<ReverseGeocodingResponse>, response: Response<ReverseGeocodingResponse>) {
                if (!isAdded || _binding == null) return
                if (response.isSuccessful) {
                    val result = response.body()
                    if (result != null && result.result == 0) {
                        val sb = StringBuilder("附近位置:\n")
                        val data = result.data
                        sb.append("${data?.description ?: ""} (${data?.address ?: ""})\n")
                        binding.tvNearbyPlaces.text = sb.toString()
                    } else {
                        binding.tvNearbyPlaces.text = "未找到附近位置"
                    }
                } else {
                    binding.tvNearbyPlaces.text = "查询失败"
                }
            }

            override fun onFailure(call: Call<ReverseGeocodingResponse>, t: Throwable) {
                if (!isAdded || _binding == null) return
                binding.tvNearbyPlaces.text = "网络错误: ${t.message}"
            }
        })
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.map.isMyLocationEnabled = false
        locationClient.stop()
        mapView.onDestroy()
        _binding = null
    }
}
