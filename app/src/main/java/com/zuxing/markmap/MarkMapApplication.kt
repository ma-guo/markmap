package com.zuxing.markmap

import android.app.Application
import com.baidu.location.LocationClient
import com.baidu.mapapi.SDKInitializer
import com.baidu.mapapi.common.BaiduMapSDKException

class MarkMapApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            SDKInitializer.setAgreePrivacy(this, true)
            SDKInitializer.initialize(this)
            LocationClient.setAgreePrivacy( true)
        } catch (e: BaiduMapSDKException) {
            e.printStackTrace()
        }
    }
}