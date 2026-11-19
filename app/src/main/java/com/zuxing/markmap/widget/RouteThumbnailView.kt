package com.zuxing.markmap.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min

/**
 * 淡墨远山 + 真实轨迹缩略图。用于首页卡片与行程详情头图。
 */
class RouteThumbnailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private var points: List<Pair<Double, Double>> = emptyList()
    private var routeColor: Int = Color.parseColor("#2E5D5C")
    private var endColor: Int = Color.parseColor("#B96A4B")

    private val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.WHITE
    }

    fun setRoute(route: List<Pair<Double, Double>>, color: Int = Color.parseColor("#2E5D5C"), endDot: Int = Color.parseColor("#B96A4B")) {
        points = route
        routeColor = color
        endColor = endDot
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            Color.parseColor("#EEF2ED"), Color.parseColor("#DCE7E1"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, skyPaint)
        skyPaint.shader = null

        drawRidge(canvas, w, h, 0.62f, "#C7D8CF", 0.72f)
        drawRidge(canvas, w, h, 0.78f, "#B3C8BE", 0.72f)
        drawRidge(canvas, w, h, 0.92f, "#A2B9AE", 0.55f)

        if (points.size >= 2) {
            drawRoute(canvas, w, h)
        }
    }

    private fun drawRidge(canvas: Canvas, w: Float, h: Float, baseFraction: Float, colorHex: String, alpha: Float) {
        val base = h * baseFraction
        val path = Path().apply {
            moveTo(0f, base)
            cubicTo(w * 0.15f, base - h * 0.14f, w * 0.26f, base + h * 0.04f, w * 0.40f, base - h * 0.10f)
            cubicTo(w * 0.54f, base - h * 0.24f, w * 0.68f, base - h * 0.06f, w * 0.82f, base - h * 0.14f)
            cubicTo(w * 0.90f, base - h * 0.19f, w * 0.96f, base - h * 0.10f, w, base - h * 0.06f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        ridgePaint.color = Color.parseColor(colorHex)
        ridgePaint.alpha = (alpha * 255).toInt()
        canvas.drawPath(path, ridgePaint)
    }

    private fun drawRoute(canvas: Canvas, w: Float, h: Float) {
        val padding = 16f * density
        val areaLeft = padding
        val areaTop = padding
        val areaRight = w - padding
        val areaBottom = h - padding

        val minLat = points.minOf { it.first }
        val maxLat = points.maxOf { it.first }
        val minLng = points.minOf { it.second }
        val maxLng = points.maxOf { it.second }
        val midLat = (minLat + maxLat) / 2
        val lngScale = cos(Math.toRadians(midLat))
        val spanX = ((maxLng - minLng) * lngScale).coerceAtLeast(1e-6)
        val spanY = (maxLat - minLat).coerceAtLeast(1e-6)
        val scale = min(
            (areaRight - areaLeft) / spanX.toFloat(),
            (areaBottom - areaTop) / spanY.toFloat()
        )
        val offsetX = (areaLeft + areaRight) / 2 - (spanX.toFloat() * scale) / 2
        val offsetY = (areaTop + areaBottom) / 2 - (spanY.toFloat() * scale) / 2

        fun px(lng: Double) = offsetX + ((lng - minLng) * lngScale).toFloat() * scale
        fun py(lat: Double) = offsetY + (maxLat - lat).toFloat() * scale

        val path = Path()
        points.forEachIndexed { index, p ->
            val x = px(p.second)
            val y = py(p.first)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        routePaint.color = routeColor
        canvas.drawPath(path, routePaint)

        val first = points.first()
        val last = points.last()
        dotPaint.color = Color.WHITE
        canvas.drawCircle(px(first.second), py(first.first), 4.5f * density, dotPaint)
        canvas.drawCircle(px(first.second), py(first.first), 4.5f * density, dotStroke)
        dotPaint.color = endColor
        canvas.drawCircle(px(last.second), py(last.first), 5f * density, dotPaint)
        canvas.drawCircle(px(last.second), py(last.first), 5f * density, dotStroke)
    }
}
