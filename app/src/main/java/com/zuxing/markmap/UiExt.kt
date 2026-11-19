package com.zuxing.markmap

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * 沉浸式页面：把状态栏高度作为顶部内边距应用到浮动控件上，
 * 保证返回/更多等按钮不会被状态栏遮挡，且各页对齐一致。
 *
 * @param extraTopDp 状态栏之外的额外间距
 */
fun View.applyTopInsetPadding(extraTopDp: Float = 0f) {
    val extra = (extraTopDp * resources.displayMetrics.density).toInt()
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
        view.updatePadding(top = top + extra)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/**
 * 底部系统栏（导航条/手势条）作为内边距，避免底部按钮被遮挡。
 */
fun View.applyBottomInsetPadding(extraBottomDp: Float = 0f) {
    val extra = (extraBottomDp * resources.displayMetrics.density).toInt()
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        view.updatePadding(bottom = bottom + extra)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/**
 * 把状态栏高度作为顶部外边距应用到控件上（用于固定高度的 Toolbar）。
 */
fun View.applyTopInsetMargin(extraTopDp: Float = 0f) {
    val extra = (extraTopDp * resources.displayMetrics.density).toInt()
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
        val lp = view.layoutParams
        if (lp is android.view.ViewGroup.MarginLayoutParams) {
            lp.topMargin = top + extra
            view.layoutParams = lp
        }
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
