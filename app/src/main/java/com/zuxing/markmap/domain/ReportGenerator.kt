package com.zuxing.markmap.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.util.Locale

/**
 * 年度 / 月度足迹报告长图生成器。
 */
object ReportGenerator {

    const val WIDTH = 1080
    const val HEIGHT = 1920

    private const val INK = "#1F2B2C"
    private const val INK2 = "#5F6F6D"
    private const val PAPER = "#F6F5EE"
    private const val PAPER_ALT = "#EFEEE6"
    private const val GOLD = "#BE9F66"
    private const val SEAL = "#A64734"

    data class ReportData(
        val title: String,
        val periodLabel: String,
        val totalKm: Double,
        val tripCount: Int,
        val provinceCount: Int,
        val cityCount: Int,
        val longestKm: Double,
        val topRoute: String?,
        val flightKm: Double,
        val railKm: Double,
        val driveKm: Double
    )

    fun generate(data: ReportData): Bitmap {
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor(PAPER))

        val brand = paint(GOLD, 26f, Typeface.create("sans-serif-medium", Typeface.NORMAL)).apply {
            letterSpacing = 0.3f
        }
        c.drawText("MARKMAP · 行迹", 80f, 110f, brand)

        val title = paint(INK, 66f, Typeface.create("serif", Typeface.BOLD))
        c.drawText(data.title, 80f, 210f, title)

        val period = paint(INK2, 30f, Typeface.create("sans-serif", Typeface.NORMAL))
        c.drawText(data.periodLabel, 80f, 260f, period)

        // 主指标卡
        val card = RectF(80f, 320f, (WIDTH - 80).toFloat(), 700f)
        c.drawRoundRect(card, 36f, 36f, paintFill(PAPER_ALT))
        drawBig(c, "总里程", formatKm(data.totalKm), 120f, 440f)
        drawBig(c, "行程", "${data.tripCount}", 560f, 440f)
        drawBig(c, "点亮省份", "${data.provinceCount}/34", 120f, 610f)
        drawBig(c, "点亮城市", "${data.cityCount}", 560f, 610f)

        // 明细
        var y = 800f
        y = drawRow(c, "最长一趟", formatKm(data.longestKm) + " km", y)
        y = drawRow(c, "Top 路线", data.topRoute ?: "—", y)

        // 交通方式
        y += 20f
        c.drawText("交通方式里程", 80f, y, paint(INK, 34f, Typeface.create("serif", Typeface.BOLD)))
        y += 30f
        y = drawBar(c, "飞行", data.flightKm, maxOf(data.totalKm, 1.0), "#2E5D5C", y)
        y = drawBar(c, "铁路", data.railKm, maxOf(data.totalKm, 1.0), "#B96A4B", y)
        y = drawBar(c, "自驾", data.driveKm, maxOf(data.totalKm, 1.0), "#70897A", y)

        // 印章
        val sealRect = RectF((WIDTH - 190).toFloat(), (HEIGHT - 220).toFloat(), (WIDTH - 80).toFloat(), (HEIGHT - 110).toFloat())
        c.drawRoundRect(sealRect, 12f, 12f, paintStroke(SEAL, 4f))
        val sealText = paint(SEAL, 60f, Typeface.create("serif", Typeface.BOLD)).apply {
            textAlign = Paint.Align.CENTER
        }
        val cy = sealRect.centerY() - (sealText.descent() + sealText.ascent()) / 2
        c.drawText("行", sealRect.centerX(), cy, sealText)

        val foot = paint(INK2, 28f, Typeface.create("serif", Typeface.NORMAL))
        c.drawText("所行皆所愿，所愿皆所成。", 80f, (HEIGHT - 150).toFloat(), foot)

        return bmp
    }

    private fun drawBig(c: Canvas, label: String, value: String, x: Float, y: Float) {
        c.drawText(value, x, y, paint(INK, 56f, Typeface.create("serif", Typeface.BOLD)))
        c.drawText(label, x, y + 40f, paint(INK2, 24f, Typeface.create("sans-serif", Typeface.NORMAL)))
    }

    private fun drawRow(c: Canvas, label: String, value: String, y: Float): Float {
        c.drawText(label, 80f, y, paint(INK2, 28f, Typeface.create("sans-serif", Typeface.NORMAL)))
        val vp = paint(INK, 32f, Typeface.create("serif", Typeface.BOLD)).apply { textAlign = Paint.Align.RIGHT }
        c.drawText(value, (WIDTH - 80).toFloat(), y, vp)
        c.drawLine(80f, y + 24f, (WIDTH - 80).toFloat(), y + 24f, paintStroke("#E9E7DE", 2f))
        return y + 90f
    }

    private fun drawBar(c: Canvas, label: String, km: Double, max: Double, color: String, y: Float): Float {
        c.drawText(label, 80f, y, paint(INK2, 26f, Typeface.create("sans-serif", Typeface.NORMAL)))
        val track = RectF(200f, y - 22f, (WIDTH - 260).toFloat(), y - 2f)
        c.drawRoundRect(track, 10f, 10f, paintFill("#E7E5DC"))
        val ratio = (km / max).coerceIn(0.0, 1.0).toFloat()
        val fill = RectF(track.left, track.top, track.left + track.width() * ratio, track.bottom)
        c.drawRoundRect(fill, 10f, 10f, paintFill(color))
        val vp = paint(INK, 26f, Typeface.create("serif", Typeface.BOLD)).apply { textAlign = Paint.Align.RIGHT }
        c.drawText(formatKm(km) + " km", (WIDTH - 80).toFloat(), y, vp)
        return y + 70f
    }

    private fun formatKm(km: Double): String = String.format(Locale.getDefault(), "%,.0f", km)

    private fun paint(color: String, size: Float, typeface: Typeface) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = Color.parseColor(color)
        textSize = size
        this.typeface = typeface
    }

    private fun paintFill(color: String) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = Color.parseColor(color)
        style = Paint.Style.FILL
    }

    private fun paintStroke(color: String, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = Color.parseColor(color)
        style = Paint.Style.STROKE
        strokeWidth = width
    }
}
