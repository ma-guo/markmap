package com.zuxing.markmap.domain

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * 行政区边界本地缓存：按 keyword 存储（polyline WKT），半年内优先使用缓存，
 * 减少后端请求并加速色块显示。
 */
object RegionCache {

    private const val TTL_MS = 180L * 24 * 60 * 60 * 1000 // 约半年
    private const val TTL_NEGATIVE_MS = 24L * 60 * 60 * 1000 // 失败负缓存 1 天
    private const val EMPTY = "###EMPTY###"

    private fun dir(context: Context): File =
        File(context.filesDir, "region_cache").apply { if (!exists()) mkdirs() }

    private fun fileFor(context: Context, keyword: String): File {
        val md5 = MessageDigest.getInstance("MD5")
            .digest(keyword.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir(context), md5)
    }

    /** 命中且未过期则返回（可能为空串表示“已确认无边界”），否则 null。 */
    fun get(context: Context, keyword: String): String? {
        val f = fileFor(context, keyword)
        if (!f.exists()) return null
        return try {
            val lines = f.readText().split("\n", limit = 2)
            val ts = lines.getOrNull(0)?.toLongOrNull() ?: return null
            val payload = lines.getOrNull(1) ?: return null
            val ttl = if (payload == EMPTY) TTL_NEGATIVE_MS else TTL_MS
            if (System.currentTimeMillis() - ts > ttl) {
                f.delete()
                return null
            }
            if (payload == EMPTY) "" else payload.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    fun put(context: Context, keyword: String, polyline: String) {
        if (polyline.isBlank()) return
        try {
            fileFor(context, keyword).writeText("${System.currentTimeMillis()}\n$polyline")
        } catch (e: Exception) {
            // ignore
        }
    }

    /** 记录“该关键字无边界”，短期避免重复请求。 */
    fun putEmpty(context: Context, keyword: String) {
        try {
            fileFor(context, keyword).writeText("${System.currentTimeMillis()}\n$EMPTY")
        } catch (e: Exception) {
            // ignore
        }
    }
}
