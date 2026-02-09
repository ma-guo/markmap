package com.zuxing.markmap

import android.app.Application
import android.content.pm.PackageManager
import com.baidu.location.LocationClient
import com.baidu.mapapi.SDKInitializer
import com.baidu.mapapi.common.BaiduMapSDKException
import com.zuxing.markmap.data.database.AppDatabase
import com.zuxing.markmap.data.repository.Repository

/**
 * 应用初始化类，负责百度地图 SDK 初始化
 */
class MarkMapApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        try {
            // 初始化数据库
            database = AppDatabase.getInstance(this)
            repository = Repository(
                database.groupDao(),
                database.lineDao(),
                database.pointDao()
            )

            // 从清单中读取百度地图 API_KEY
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            BaiduConfig.setAk(appInfo.metaData.getString("com.baidu.lbsapi.API_KEY") ?: "")

            SDKInitializer.setAgreePrivacy(this, true)
            SDKInitializer.initialize(this)
            LocationClient.setAgreePrivacy(true)
        } catch (e: BaiduMapSDKException) {
            e.printStackTrace()
        }
    }
}