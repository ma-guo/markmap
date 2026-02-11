package com.zuxing.markmap

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import java.util.concurrent.TimeUnit

/**
 * 后台定位服务
 */
class LocationService : Service() {

    private lateinit var locationClient: LocationClient
    private val channelId = "location_service_channel"

    companion object {
        private const val WORK_NAME = "BackgroundLocationWork"
        private const val TAG = "LocationService"

        fun start(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<LocationWorker>(1, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "后台定位服务已启动")
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "后台定位服务已停止")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupLocationClient()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())
        return START_STICKY
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
                    Log.d(TAG, "后台定位成功: lat=${it.latitude}, lng=${it.longitude}")
                }
            }
        })

        val option = LocationClientOption().apply {
            locationMode = LocationClientOption.LocationMode.Battery_Saving
            setCoorType("bd09ll")
            setScanSpan(60000) // 1分钟
            isOpenGps = true
            isOpenGnss = true
            setIsNeedAddress(true)
        }
        locationClient.locOption = option
    }

    override fun onDestroy() {
        super.onDestroy()
        locationClient.stop()
    }
}

/**
 * 后台定位 Worker
 */
class LocationWorker(
    context: Context,
    params: androidx.work.WorkerParameters
) : androidx.work.CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "LocationWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "执行后台定位")
        return try {
            val locationClient = LocationClient(applicationContext)
            val latch = java.util.concurrent.CountDownLatch(1)
            var locationResult: BDLocation? = null

            locationClient.registerLocationListener(object : BDAbstractLocationListener() {
                override fun onReceiveLocation(location: BDLocation?) {
                    location?.let {
                        Log.d(TAG, "后台定位: lat=${it.latitude}, lng=${it.longitude}")
                        locationResult = it
                        latch.countDown()
                    }
                }
            })

            val option = LocationClientOption().apply {
                locationMode = LocationClientOption.LocationMode.Battery_Saving
                setCoorType("bd09ll")
                setScanSpan(0)
                setOpenGps(true)
                setIsNeedAddress(true)
            }
            locationClient.locOption = option

            if (!locationClient.isStarted) {
                locationClient.start()
            }

            latch.await(30, TimeUnit.SECONDS)
            locationClient.stop()

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "后台定位失败", e)
            Result.retry()
        }
    }
}