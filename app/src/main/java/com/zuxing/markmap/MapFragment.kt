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
import com.baidu.mapapi.map.MapStatusUpdate
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
            Toast.makeText(requireContext(), "需要定位权限才能使用此功能", Toast.LENGTH_LONG).show()
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

    private fun setupLocationClient() {
        locationClient = LocationClient(requireContext())
        locationClient.registerLocationListener(object : BDAbstractLocationListener() {
            override fun onReceiveLocation(location: BDLocation) {
                activity?.runOnUiThread {
                    processLocation(location)
                }
            }
        })

        val option = LocationClientOption()
        option.locationMode = LocationClientOption.LocationMode.Hight_Accuracy
        option.setCoorType("bd09ll")
        option.setScanSpan(1000)
        option.setOpenGps(true)
        option.setLocationNotify(true)
        option.setIgnoreKillProcess(false)
        option.SetIgnoreCacheException(false)
        option.setWifiCacheTimeOut(5 * 60 * 1000)
        option.setEnableSimulateGps(false)
        option.setIsNeedAddress(true)
        option.setIsNeedAltitude(true)
        option.setIsNeedLocationDescribe(true)
//        option.setIsNeedPoiList(true)

        locationClient.locOption = option
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
        binding.progressBar.visibility = View.VISIBLE
        if (!locationClient.isStarted) {
            locationClient.start()
        }
    }

    private fun processLocation(location: BDLocation) {
        binding.progressBar.visibility = View.GONE

        if (location.locType == BDLocation.TypeGpsLocation ||
            location.locType == BDLocation.TypeNetWorkLocation ||
            location.locType == BDLocation.TypeOffLineLocation) {

            val sb = StringBuilder()
            sb.append("定位时间: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())).append("\n")
            sb.append("纬度: ").append(location.latitude).append("\n")
            sb.append("经度: ").append(location.longitude).append("\n")
            sb.append("海拔: ").append(if (location.hasAltitude()) "${location.altitude}m" else "未知").append("\n")
            sb.append("地址: ").append(location.addrStr).append("\n")
            sb.append("位置描述: ").append(location.locationDescribe).append("\n")

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
//                update.zoom(MapStatusUpdateFactory.zoomTo(18f))
            }

            fetchNearbyPlaces(location.latitude, location.longitude)
        } else {
            binding.tvLocationInfo.text = "定位失败，错误码: ${location.locType}"
        }
    }

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
            ak = "请替换为你的百度地图API_KEY"
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