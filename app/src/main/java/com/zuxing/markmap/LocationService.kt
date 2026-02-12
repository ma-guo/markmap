package com.zuxing.markmap

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.zuxing.markmap.data.entity.PointEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class LocationService : Service() {

    private lateinit var locationClient: LocationClient
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var app: MarkMapApplication
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channelId = "location_service_channel"
    private var lineId: Long = -1L
    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null

    companion object {
        private const val TAG = "LocationService"
        private const val EXTRA_LINE_ID = "line_id"

        fun start(context: Context, lineId: Long) {
            val intent = Intent(context, LocationService::class.java).apply {
                putExtra(EXTRA_LINE_ID, lineId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Logger.d("后台定位服务已启动, lineId=$lineId")
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationService::class.java)
            context.stopService(intent)
            Logger.d("后台定位服务已停止")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("settings", 0)
        app = application as MarkMapApplication
        createNotificationChannel()
        setupLocationClient()

        handler = Handler(Looper.getMainLooper())
        runnable = object : Runnable {
            override fun run() {
                requestLocation()
                handler.postDelayed(this, getIntervalMillis())
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lineId = intent?.getLongExtra(EXTRA_LINE_ID, -1L) ?: -1L
        startForeground(1, createNotification())
        handler.post(runnable)
        return START_STICKY
    }

    private fun getIntervalMillis(): Long {
        val interval = prefs.getLong(SettingsActivity.KEY_INTERVAL, SettingsActivity.DEFAULT_INTERVAL)
        return interval
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "定位服务",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "用于后台定位的通知"
            }
            channel.setShowBadge(true)
            channel.enableVibration(true)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, channelId)
        .setContentTitle("定位服务运行中")
        .setContentText("正在后台获取位置信息")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setOngoing(true)
        .build()

    private fun setupLocationClient() {
        locationClient = LocationClient(this)
        locationClient.registerLocationListener(object : BDAbstractLocationListener() {
            override fun onReceiveLocation(location: BDLocation?) {
                location?.let {
                    Logger.d("后台定位成功: lat=${it.latitude}, lng=${it.longitude}")
                    savePointIfNeeded(it)
                    vibrateIfEnabled()
                }
            }
        })

        val option = LocationClientOption().apply {
            locationMode = LocationClientOption.LocationMode.Battery_Saving
            setCoorType("bd09ll")
            setScanSpan(0)
            isOpenGps = true
            isOpenGnss = true
            setIsNeedAddress(true)
        }
        locationClient.locOption = option
    }

    private fun requestLocation() {
        if (!locationClient.isStarted) {
            locationClient.start()
        }
    }

    private fun savePointIfNeeded(location: BDLocation) {
        if (lineId == -1L) return

        val lat = location.latitude
        val lng = location.longitude

        val distanceThreshold = prefs.getDouble(SettingsActivity.KEY_DISTANCE, SettingsActivity.DEFAULT_DISTANCE)

        val distance = lastLatitude?.let { lastLat ->
            lastLongitude?.let { lastLng ->
                calculateDistance(lastLat, lastLng, lat, lng)
            }
        } ?: Double.MAX_VALUE

        if (distance >= distanceThreshold) {
            serviceScope.launch {
                try {
                    val maxSortOrder = getMaxSortOrder()
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
                    Logger.d("保存点成功, 距离=$distance, 阈值=$distanceThreshold")

                    lastLatitude = lat
                    lastLongitude = lng
                } catch (e: Exception) {
                    Logger.e("保存点失败: ${e.message}")
                }
            }
        } else {
            Logger.d("距离太近($distance < $distanceThreshold), 不保存")
        }
    }

    private suspend fun getMaxSortOrder(): Int {
        return try {
            val points = app.repository.getPointsByLineId(lineId).first()
            points.maxOfOrNull { it.sortOrder } ?: 0
        } catch (e: Exception) {
            Logger.e("获取排序失败: ${e.message}")
            0
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private fun vibrateIfEnabled() {
        val vibrateEnabled = prefs.getBoolean(SettingsActivity.KEY_VIBRATE, SettingsActivity.DEFAULT_VIBRATE)
        if (vibrateEnabled) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            @Suppress("DEPRECATION")
            vibrator.vibrate(1000)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
        locationClient.stop()
        serviceScope.cancel()
    }

    private fun android.content.SharedPreferences.getDouble(key: String, defaultValue: Double): Double {
        return java.lang.Double.longBitsToDouble(getLong(key, java.lang.Double.doubleToRawLongBits(defaultValue)))
    }
}
