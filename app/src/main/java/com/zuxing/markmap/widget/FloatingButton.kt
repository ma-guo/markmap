package com.zuxing.markmap.widget

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import androidx.cardview.widget.CardView
import com.zuxing.markmap.R

@SuppressLint("Recycle")
class FloatingButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr) {

    private val iconView: ImageView
    private val iconSize: Int = (24 * resources.displayMetrics.density).toInt()
    private val buttonSize: Int = (56 * resources.displayMetrics.density).toInt()
    private var colorAnimator: ObjectAnimator? = null

    init {

        layoutParams = LayoutParams(buttonSize, buttonSize)
        radius = buttonSize / 2f
        cardElevation = buttonSize / 7f
        setCardBackgroundColor(resources.getColor(R.color.teal_200, null))
        foreground = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).getDrawable(0)

        iconView = ImageView(context).apply {
            layoutParams = LayoutParams(iconSize, iconSize).apply {
                gravity = Gravity.CENTER
            }
            setImageResource(R.drawable.add_24px)
            setColorFilter(resources.getColor(R.color.white, null))
        }
        isClickable = true
        minimumWidth = buttonSize
        minimumHeight = buttonSize
        addView(iconView)
    }

    fun setIcon(resId: Int) {
        iconView.setImageResource(resId)
    }

    fun setIconTint(color: Int) {
        colorAnimator?.cancel()
        colorAnimator = null
        iconView.setColorFilter(color)

    }

    fun setIconTintAnim() {
        // 创建一个颜色呼吸灯动画：从红色渐变到蓝色
        colorAnimator = ObjectAnimator.ofArgb(
            iconView,  // 要动画的View
            "colorFilter",  // 要动画的属性
            Color.RED,  // 起始颜色
            Color.BLUE // 结束颜色
        )

        // 设置动画参数
        colorAnimator?.setDuration(1000) // 一个完整周期（红->蓝->红）耗时1000毫秒
        colorAnimator?.repeatMode = ValueAnimator.REVERSE // 动画反向播放，实现呼吸感
        colorAnimator?.repeatCount = ValueAnimator.INFINITE // 无限循环


        // 启动动画
        colorAnimator?.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        colorAnimator?.cancel()
        colorAnimator = null
    }
}
