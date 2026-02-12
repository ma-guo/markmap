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

class LocationService : Service() {

    private lateinit var locationClient: LocationClient
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable
    private lateinit var prefs: android.content.SharedPreferences
    private val channelId = "location_service_channel"

    companion object {
        private const val TAG = "LocationService"

        fun start(context: Context) {
            val intent = Intent(context, LocationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Logger.d("后台定位服务已启动")
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
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于后台定位的通知"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, channelId)
        .setContentTitle("定位服务运行中")
        .setContentText("正在后台获取位置信息")
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setOngoing(true)
        .build()

    private fun setupLocationClient() {
        locationClient = LocationClient(this)
        locationClient.registerLocationListener(object : BDAbstractLocationListener() {
            override fun onReceiveLocation(location: BDLocation?) {
                location?.let {
                    Logger.d("后台定位成功: lat=${it.latitude}, lng=${it.longitude}")
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
            vibrator.vibrate(100)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
        locationClient.stop()
    }
}
