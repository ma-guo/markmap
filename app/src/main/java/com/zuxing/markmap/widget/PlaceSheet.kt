package com.zuxing.markmap.widget

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.zuxing.markmap.databinding.DialogPlaceSheetBinding

/**
 * 长地名「详情气泡」：完整名称 + 行政区/时间，支持复制名称与在地图查看。
 */
object PlaceSheet {

    fun show(
        context: Context,
        main: String,
        admin: String,
        meta: String? = null,
        onMap: (() -> Unit)? = null
    ) {
        val dialog = BottomSheetDialog(context)
        val binding = DialogPlaceSheetBinding.inflate(LayoutInflater.from(context))
        binding.tvSheetName.text = main
        binding.tvSheetAdmin.text = listOfNotNull(admin.takeIf { it.isNotBlank() }, meta)
            .joinToString(" · ")
        binding.btnCopy.setOnClickListener {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("place", main))
            Toast.makeText(context, "已复制名称", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        binding.btnMap.setOnClickListener {
            onMap?.invoke()
            dialog.dismiss()
        }
        dialog.setContentView(binding.root)
        dialog.show()
    }
}
