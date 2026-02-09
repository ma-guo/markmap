package com.zuxing.markmap

import android.util.Log

/**
 * 公共日志工具类
 */
object Logger {

    private const val DEFAULT_TAG = "MarkMapLog"

    fun d(message: String, tag: String = DEFAULT_TAG) {
        Log.d(tag, message)
    }

    fun i(message: String, tag: String = DEFAULT_TAG) {
        Log.i(tag, message)
    }

    fun w(message: String, tag: String = DEFAULT_TAG) {
        Log.w(tag, message)
    }

    fun e(message: String, tag: String = DEFAULT_TAG) {
        Log.e(tag, message)
    }

    fun e(message: String, throwable: Throwable, tag: String = DEFAULT_TAG) {
        Log.e(tag, message, throwable)
    }

    fun v(message: String, tag: String = DEFAULT_TAG) {
        Log.v(tag, message)
    }
}