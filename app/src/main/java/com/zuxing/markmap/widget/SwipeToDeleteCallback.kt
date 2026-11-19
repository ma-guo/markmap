package com.zuxing.markmap.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.zuxing.markmap.R

/**
 * 左滑删除，配合 Snackbar 撤销使用。
 */
class SwipeToDeleteCallback(
    context: Context,
    private val onSwiped: (position: Int) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

    private val density = context.resources.displayMetrics.density
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.seal)
    }
    private val icon = ContextCompat.getDrawable(context, R.drawable.delete_24px)?.apply {
        setTint(ContextCompat.getColor(context, R.color.white))
    }
    private val corner = 16f * density

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.adapterPosition
        if (position != RecyclerView.NO_POSITION) {
            onSwiped(position)
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView
        if (dX < 0) {
            val rect = RectF(
                itemView.right + dX,
                itemView.top.toFloat() + 4 * density,
                itemView.right.toFloat(),
                itemView.bottom.toFloat() - 4 * density
            )
            c.drawRoundRect(rect, corner, corner, backgroundPaint)
            icon?.let {
                val iconSize = 22 * density.toInt()
                val left = (itemView.right - iconSize - 24 * density).toInt()
                val top = itemView.top + (itemView.height - iconSize) / 2
                it.setBounds(left, top, left + iconSize, top + iconSize)
                it.draw(c)
            }
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}
