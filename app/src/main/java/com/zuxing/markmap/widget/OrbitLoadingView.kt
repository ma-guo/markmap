package com.zuxing.markmap.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * 悬浮加载动效（设计稿 D）：三个小球同半径绕行，周期 1.4s / 1.9s / 2.4s，第三个反向；
 * 无圆环、无光晕、无文字。使用 Choreographer 帧回调持续驱动。
 */
class OrbitLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val colors = intArrayOf(
        Color.parseColor("#2E5D5C"),
        Color.parseColor("#B96A4B"),
        Color.parseColor("#BE9F66")
    )
    private val periods = floatArrayOf(1330f, 1830f, 2330f)
    private val dirs = floatArrayOf(1f, 1f, -1f)
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var glowShader: RadialGradient? = null
    private var shaderSize = 0

    private var running = false
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun isAnimating(): Boolean = running

    fun start() {
        if (running) return
        running = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stop() { running = false }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); start() }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); stop() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val now = System.currentTimeMillis()
        val cx = w / 2f; val cy = h / 2f
        val minSide = minOf(w, h)
        val glowR = minSide * 0.5f
        val orbitR = glowR * 0.48f          // 光晕直径 ≈ 轨道的 2.08 倍
        val ballR = orbitR * 0.111f

        // 径向黛青光晕底衬
        if (glowShader == null || shaderSize != minSide.toInt()) {
            glowShader = RadialGradient(
                cx, cy, glowR,
                intArrayOf(
                    Color.argb(72, 46, 93, 92),   // 28%
                    Color.argb(26, 46, 93, 92),   // 10%
                    Color.argb(0, 46, 93, 92)
                ),
                floatArrayOf(0f, 0.55f, 0.78f),
                Shader.TileMode.CLAMP
            )
            shaderSize = minSide.toInt()
        }
        glowPaint.shader = glowShader
        canvas.drawCircle(cx, cy, glowR, glowPaint)
        glowPaint.shader = null
        for (i in 0..2) {
            val angle = Math.toRadians(
                ((now % periods[i].toLong()) / periods[i] * 360f * dirs[i]).toDouble()
            )
            val x = cx + orbitR * cos(angle).toFloat()
            val y = cy + orbitR * sin(angle).toFloat()
            ballPaint.color = colors[i]
            canvas.drawCircle(x, y, ballR, ballPaint)
        }
    }
}
