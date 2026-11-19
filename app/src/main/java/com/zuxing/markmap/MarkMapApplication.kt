package com.zuxing.markmap

import android.app.Application
import android.content.pm.PackageManager
import com.baidu.location.LocationClient
import com.baidu.mapapi.SDKInitializer
import com.zuxing.markmap.data.database.AppDatabase
import com.zuxing.markmap.data.repository.Repository

/**
 * 应用初始化类，负责数据层与百度地图 SDK 初始化。
 */
class MarkMapApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: Repository
        private set

    /** 百度 SDK 初始化失败时置为 false，相关页面降级处理。 */
    var baiduReady: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()

        // 数据层：必须成功，否则应用无法工作。
        database = AppDatabase.getInstance(this)
        repository = Repository(
            database.groupDao(),
            database.lineDao(),
            database.pointDao(),
            database.mergedTrackPlanDao(),
            database.achievementDao(),
            database.visitedPlaceDao()
        )

        // 地图/定位 SDK：尽量初始化，失败也不应导致应用启动崩溃。
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            BaiduConfig.setAk(appInfo.metaData.getString("com.baidu.lbsapi.API_KEY") ?: "")

            SDKInitializer.setAgreePrivacy(this, true)
            SDKInitializer.initialize(this)
            LocationClient.setAgreePrivacy(true)
            baiduReady = true
        } catch (e: Throwable) {
            baiduReady = false
            Logger.e("百度地图 SDK 初始化失败: ${e.message}")
            e.printStackTrace()
        }
    }
}
