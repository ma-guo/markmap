package com.zuxing.markmap.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
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
        iconView.setColorFilter(color)
    }
}
