package com.zuxing.markmap.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min

/**
 * 分享卡生成器：米纸底、宋体标题、轨迹线、统计与印章。
 * 纯 Canvas 绘制，保证不同机型输出一致。
 */
object ShareCardGenerator {

    const val WIDTH = 1080
    const val HEIGHT = 1440

    private const val INK = "#1F2B2C"
    private const val INK_SECONDARY = "#5F6F6D"
    private const val PAPER = "#F6F5EE"
    private const val PAPER_ALT = "#EFEEE6"
    private const val GOLD = "#BE9F66"
    private const val SEAL = "#A64734"
    private const val CLAY = "#B96A4B"

    enum class Theme { MOUNTAIN, MINIMAL, AIR }

    fun generate(title: String, result: TrackMerger.Result, theme: Theme = Theme.MOUNTAIN): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bg = when (theme) {
            Theme.MOUNTAIN -> PAPER
            Theme.MINIMAL -> "#FBFAF6"
            Theme.AIR -> "#EAF0F4"
        }
        canvas.drawColor(Color.parseColor(bg))

        drawHeader(canvas, title, result, theme)
        drawMapCard(canvas, result)
        drawStats(canvas, result)
        drawFooter(canvas)

        return bitmap
    }

    private fun drawHeader(canvas: Canvas, title: String, result: TrackMerger.Result, theme: Theme) {
        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(GOLD)
            textSize = 26f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.28f
        }
        canvas.drawText("MARKMAP · 行迹", 80f, 108f, brand)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(INK)
            textSize = 62f
            typeface = Typeface.create("serif", Typeface.BOLD)
        }
        val fullTitle = title.ifBlank { "我的行程" }
        val maxW = (WIDTH - 160).toFloat()
        if (titlePaint.measureText(fullTitle) <= maxW) {
            canvas.drawText(fullTitle, 80f, 208f, titlePaint)
        } else {
            var line1 = fullTitle
            while (line1.length > 1 && titlePaint.measureText(line1) > maxW) line1 = line1.dropLast(1)
            val line2 = fullTitle.substring(line1.length)
            canvas.drawText(line1, 80f, 196f, titlePaint)
            val safe2 = TextUtils.ellipsize(line2, titlePaint, maxW, TextUtils.TruncateAt.END).toString()
            canvas.drawText(safe2, 80f, 196f + 66f, titlePaint)
        }

        val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(INK_SECONDARY)
            textSize = 30f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        canvas.drawText(buildDateRange(result), 80f, 258f, subtitle)
    }

    private fun drawMapCard(canvas: Canvas, result: TrackMerger.Result) {
        val card = RectF(80f, 316f, (WIDTH - 80).toFloat(), 940f)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(PAPER_ALT)
        }
        canvas.drawRoundRect(card, 36f, 36f, cardPaint)

        if (result.points.isEmpty()) return

        val inner = RectF(card.left + 44f, card.top + 44f, card.right - 44f, card.bottom - 44f)
        val projection = Projection(result, inner)

        result.ranges.forEach { range ->
            val segmentPoints = result.points.subList(range.startIndex, range.endIndex + 1)
            if (segmentPoints.size < 2) return@forEach
            val path = Path()
            segmentPoints.forEachIndexed { index, point ->
                val x = projection.x(point.longitude)
                val y = projection.y(point.latitude)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 7f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = Color.parseColor(TransportMode.from(range.transportMode).colorHex)
            }
            canvas.drawPath(path, linePaint)
        }

        val connectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.parseColor(CLAY)
            pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
        }
        result.connectors.forEach { connector ->
            canvas.drawLine(
                projection.x(connector.from.longitude),
                projection.y(connector.from.latitude),
                projection.x(connector.to.longitude),
                projection.y(connector.to.latitude),
                connectorPaint
            )
        }

        drawEndpoint(
            canvas,
            projection.x(result.points.first().longitude),
            projection.y(result.points.first().latitude),
            Color.parseColor("#2E5D5C")
        )
        drawEndpoint(
            canvas,
            projection.x(result.points.last().longitude),
            projection.y(result.points.last().latitude),
            Color.parseColor(CLAY)
        )
    }

    private fun drawEndpoint(canvas: Canvas, x: Float, y: Float, color: Int) {
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawCircle(x, y, 14f, white)
        canvas.drawCircle(x, y, 9f, fill)
    }

    private fun drawStats(canvas: Canvas, result: TrackMerger.Result) {
        val distance = formatDistance(result.totalDistanceMeters)
        val duration = formatDuration(result)
        val points = "${result.points.size}"

        drawStat(canvas, 80f, "总里程", distance)
        drawStat(canvas, 420f, "历时", duration)
        drawStat(canvas, 760f, "地点", points)
    }

    private fun drawStat(canvas: Canvas, x: Float, label: String, value: String) {
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(INK)
            textSize = 54f
            typeface = Typeface.create("serif", Typeface.BOLD)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(INK_SECONDARY)
            textSize = 24f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        canvas.drawText(value, x, 1050f, valuePaint)
        canvas.drawText(label, x, 1092f, labelPaint)
    }

    private fun drawFooter(canvas: Canvas) {
        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E9E7DE")
            strokeWidth = 2f
        }
        canvas.drawLine(80f, 1180f, (WIDTH - 80).toFloat(), 1180f, divider)

        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(INK_SECONDARY)
            textSize = 28f
            typeface = Typeface.create("serif", Typeface.NORMAL)
            letterSpacing = 0.08f
        }
        canvas.drawText("山高水长 · 行迹可寻", 80f, 1286f, footer)

        val sealRect = RectF((WIDTH - 190).toFloat(), 1218f, (WIDTH - 80).toFloat(), 1328f)
        val sealStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor(SEAL)
        }
        canvas.drawRoundRect(sealRect, 10f, 10f, sealStroke)

        val sealText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(SEAL)
            textSize = 62f
            typeface = Typeface.create("serif", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val centerY = sealRect.centerY() - (sealText.descent() + sealText.ascent()) / 2
        canvas.drawText("行", sealRect.centerX(), centerY, sealText)
    }

    private fun buildDateRange(result: TrackMerger.Result): String {
        val times = result.points.map { it.time }.filter { it > 0 }
        if (times.isEmpty()) return "一路山河，皆成行迹"
        val start = times.minOrNull()!!
        val end = times.maxOrNull()!!
        val format = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
        val startText = format.format(Date(start))
        val endText = format.format(Date(end))
        return if (startText == endText) startText else "$startText — $endText"
    }

    private fun formatDistance(meters: Double): String {
        return if (meters >= 1000) {
            String.format(Locale.getDefault(), "%.1f km", meters / 1000)
        } else {
            String.format(Locale.getDefault(), "%.0f m", meters)
        }
    }

    private fun formatDuration(result: TrackMerger.Result): String {
        val times = result.points.map { it.time }.filter { it > 0 }
        if (times.isEmpty()) return "—"
        val millis = times.maxOrNull()!! - times.minOrNull()!!
        if (millis <= 0) return "—"
        val hours = millis / 3_600_000
        val minutes = (millis % 3_600_000) / 60_000
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private class Projection(result: TrackMerger.Result, private val area: RectF) {
        private val minLat = result.points.minOf { it.latitude }
        private val maxLat = result.points.maxOf { it.latitude }
        private val minLng = result.points.minOf { it.longitude }
        private val maxLng = result.points.maxOf { it.longitude }
        private val midLat = (minLat + maxLat) / 2
        private val lngScale = cos(Math.toRadians(midLat))
        private val spanX = ((maxLng - minLng) * lngScale).coerceAtLeast(1e-6)
        private val spanY = (maxLat - minLat).coerceAtLeast(1e-6)
        private val scale = min(area.width() / spanX.toFloat(), area.height() / spanY.toFloat())
        private val offsetX = area.centerX() - (spanX.toFloat() * scale) / 2
        private val offsetY = area.centerY() - (spanY.toFloat() * scale) / 2

        fun x(lng: Double): Float = offsetX + ((lng - minLng) * lngScale).toFloat() * scale

        fun y(lat: Double): Float = offsetY + (maxLat - lat).toFloat() * scale
    }
}
