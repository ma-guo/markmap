package com.zuxing.markmap

/**
 * 百度地图配置常量
 */
object BaiduConfig {
    lateinit var API_KEY: String
        private set

    fun setAk(ak: String) {
        API_KEY = ak;
    }
}