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
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.MyLocationData
import com.baidu.mapapi.model.LatLng
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

/**
 * 地图页面 Fragment，实现定位和附近位置查询功能
 */
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private lateinit var mapView: MapView
    private lateinit var locationClient: LocationClient
    private var isFirstLocation = true

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
        mapView = binding.bmapView
        setupLocationClient()
        setupClickListeners()

        if (checkPermissions()) {
            startLocation()
        } else {
            requestPermissions()
        }
    }

    /**
     * 配置定位客户端参数
     */
    private fun setupLocationClient() {
        locationClient = LocationClient(requireContext())

        // 必须先注册监听器
        locationClient.registerLocationListener(object : BDAbstractLocationListener() {
            override fun onReceiveLocation(location: BDLocation?) {
                location?.let {
                    Logger.d("收到定位: lat=${it.latitude}, lng=${it.longitude}, type=${it.locType}")
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
            setScanSpan(0)
            setOpenGps(true)
            setLocationNotify(false)
            setIgnoreKillProcess(true)
            SetIgnoreCacheException(false)
            setWifiCacheTimeOut(5 * 60 * 1000)
            setEnableSimulateGps(false)
            setIsNeedAddress(true)
            setIsNeedAltitude(true)
            setIsNeedLocationDescribe(true)
            isNeedPoiRegion = true
            setIsNeedLocationPoiList(true)
        }
        locationClient.locOption = option

        Logger.d("定位客户端配置完成")
    }

    /**
     * 设置点击事件监听器
     */
    private fun setupClickListeners() {
        binding.fabLocation.setOnClickListener {
            if (checkPermissions()) {
                isFirstLocation = true
                startLocation()
            } else {
                requestPermissions()
            }
        }
    }

    /**
     * 检查定位权限
     */
    private fun checkPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 请求定位权限
     */
    private fun requestPermissions() {
        permissionLauncher.launch(requiredPermissions)
    }

    /**
     * 开始定位
     */
    private fun startLocation() {
        binding.progressBar.visibility = View.VISIBLE
        Logger.d("开始定位, isStarted=${locationClient.isStarted}")
        if (!locationClient.isStarted) {
            val result = locationClient.start()
            Logger.d("start() 返回: $result")
        }
    }

    /**
     * 处理定位结果
     */
    private fun processLocation(location: BDLocation) {
        binding.progressBar.visibility = View.GONE

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

            // 更新地图上的位置显示
            val locData = MyLocationData.Builder()
                .accuracy(location.radius)
                .latitude(location.latitude)
                .longitude(location.longitude)
                .build()
            mapView.map.setMyLocationData(locData)

            // 首次定位时移动地图到当前位置
            if (isFirstLocation) {
                isFirstLocation = false
                val ll = LatLng(location.latitude, location.longitude)
                val update = MapStatusUpdateFactory.newLatLng(ll)
                mapView.map.animateMapStatus(update)
            }

            // 查询附近位置
            fetchNearbyPlaces(location.latitude, location.longitude)
        } else {
            binding.tvLocationInfo.text = "定位失败，错误码: ${location.locType}"
        }
    }

    /**
     * 调用百度地图 API 查询附近位置
     */
    private fun fetchNearbyPlaces(latitude: Double, longitude: Double) {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.map.baidu.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(BaiduPlaceService::class.java)

        service.searchNearby(
            query = "周边",
            location = "$latitude,$longitude",
            radius = 1000,
            output = "json",
            ak = BaiduConfig.API_KEY
        ).enqueue(object : Callback<PlaceSearchResponse> {
            override fun onResponse(call: Call<PlaceSearchResponse>, response: Response<PlaceSearchResponse>) {
                if (response.isSuccessful) {
                    val result = response.body()
                    if (result != null && result.status == "SUCCESS") {
                        val sb = StringBuilder("附近位置:\n")
                        result.results.take(5).forEachIndexed { index, place ->
                            sb.append("${index + 1}. ${place.name} (${place.address})\n")
                        }
                        binding.tvNearbyPlaces.text = sb.toString()
                    } else {
                        binding.tvNearbyPlaces.text = "未找到附近位置"
                    }
                } else {
                    binding.tvNearbyPlaces.text = "查询失败"
                }
            }

            override fun onFailure(call: Call<PlaceSearchResponse>, t: Throwable) {
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
        locationClient.stop()
        mapView.onDestroy()
        _binding = null
    }
}

/**
 * 百度地图附近搜索 API 接口
 */
interface BaiduPlaceService {
    @GET("place/v2/search")
    fun searchNearby(
        @Query("query") query: String,
        @Query("location") location: String,
        @Query("radius") radius: Int,
        @Query("output") output: String,
        @Query("ak") ak: String
    ): Call<PlaceSearchResponse>
}

data class PlaceSearchResponse(
    val status: String,
    val message: String?,
    val results: List<PlaceResult>
)

data class PlaceResult(
    val name: String,
    val address: String,
    val location: Location,
    val uid: String?
)

data class Location(
    val lat: Double,
    val lng: Double
)