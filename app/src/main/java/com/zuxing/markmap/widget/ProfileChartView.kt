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

/**
 * 剖面图：横轴里程，纵轴数值（高度或速度）。黛青描边 + 渐变面积。
 */
class ProfileChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private var series: List<Pair<Double, Double>> = emptyList() // km -> value
    private var unit: String = "m"
    private var lineColor: Int = Color.parseColor("#2E5D5C")

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E9E7DE")
        strokeWidth = 1f * density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#98A4A0")
        textSize = 10f * density
    }

    fun setData(points: List<Pair<Double, Double>>, unit: String, color: Int = Color.parseColor("#2E5D5C")) {
        series = points
        this.unit = unit
        lineColor = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val padLeft = 34f * density
        val padRight = 10f * density
        val padTop = 14f * density
        val padBottom = 22f * density
        val left = padLeft
        val right = w - padRight
        val top = padTop
        val bottom = h - padBottom

        // 网格
        for (i in 0..3) {
            val y = top + (bottom - top) * i / 3f
            canvas.drawLine(left, y, right, y, gridPaint)
        }

        if (series.size < 2) {
            canvas.drawText("暂无数据", left, (top + bottom) / 2, textPaint)
            return
        }

        val maxKm = series.last().first.coerceAtLeast(1e-6)
        val values = series.map { it.second }
        var minV = values.minOrNull() ?: 0.0
        var maxV = values.maxOrNull() ?: 1.0
        if (maxV - minV < 1e-6) { maxV += 1.0; minV -= 1.0 }
        // 留出上下边距
        val vSpan = maxV - minV
        minV -= vSpan * 0.08
        maxV += vSpan * 0.08

        val path = Path()
        series.forEachIndexed { i, (km, v) ->
            val x = left + (right - left) * (km / maxKm).toFloat()
            val y = bottom - (bottom - top) * ((v - minV) / (maxV - minV)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        val area = Path(path).apply {
            lineTo(right, bottom)
            lineTo(left, bottom)
            close()
        }
        fillPaint.shader = LinearGradient(
            0f, top, 0f, bottom,
            Color.argb(60, Color.red(lineColor), Color.green(lineColor), Color.blue(lineColor)),
            Color.argb(0, Color.red(lineColor), Color.green(lineColor), Color.blue(lineColor)),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(area, fillPaint)
        fillPaint.shader = null

        linePaint.color = lineColor
        canvas.drawPath(path, linePaint)

        // 轴标注
        canvas.drawText(formatV(maxV), 2f * density, top + 8f * density, textPaint)
        canvas.drawText(formatV(minV), 2f * density, bottom, textPaint)
        canvas.drawText("0", left, h - 6f * density, textPaint)
        canvas.drawText(formatKm(maxKm) + " km", right - 44f * density, h - 6f * density, textPaint)
    }

    private fun formatV(v: Double): String = when {
        v >= 1000 -> "${(v / 1000).toInt()}k"
        v >= 10 -> v.toInt().toString()
        else -> String.format("%.1f", v)
    }

    private fun formatKm(km: Double): String = if (km >= 100) km.toInt().toString() else String.format("%.1f", km)
}
