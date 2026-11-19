package com.zuxing.markmap

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.zuxing.markmap.databinding.ActivityShareBinding
import com.zuxing.markmap.domain.MergeConfig
import com.zuxing.markmap.domain.ShareCardGenerator
import com.zuxing.markmap.domain.TrackMerger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ShareActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShareBinding
    private lateinit var app: MarkMapApplication
    private var groupId: Long = -1L
    private var bitmap: Bitmap? = null
    private var theme: ShareCardGenerator.Theme = ShareCardGenerator.Theme.MOUNTAIN
    private var title: String = "我的行程"
    private var lastResult: com.zuxing.markmap.domain.TrackMerger.Result? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as MarkMapApplication
        groupId = intent.getLongExtra("groupId", -1L)

        binding.navBar.applyTopInsetPadding(8f)
        binding.dock.applyBottomInsetPadding()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnMore.setOnClickListener { showMoreMenu(it) }
        binding.btnSaveImage.setOnClickListener { saveImage() }
        binding.btnShareCard.setOnClickListener { shareImage() }
        setupTemplates()

        loadData()
    }

    private fun regenerate() {
        val result = lastResult ?: return
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.Default) { ShareCardGenerator.generate(title, result, theme) }
            bitmap = bmp
            binding.ivPreview.setImageBitmap(bmp)
        }
    }

    private fun setupTemplates() {
        binding.tplMountain.setOnClickListener { selectTemplate(binding.tplMountain) }
        binding.tplMinimal.setOnClickListener { selectTemplate(binding.tplMinimal) }
        binding.tplAir.setOnClickListener { selectTemplate(binding.tplAir) }
    }

    private fun selectTemplate(selected: TextView) {
        theme = when (selected.id) {
            R.id.tplMinimal -> ShareCardGenerator.Theme.MINIMAL
            R.id.tplAir -> ShareCardGenerator.Theme.AIR
            else -> ShareCardGenerator.Theme.MOUNTAIN
        }
        regenerate()
        listOf(binding.tplMountain, binding.tplMinimal, binding.tplAir).forEach { view ->
            if (view === selected) {
                view.setBackgroundResource(R.drawable.bg_chip)
                view.setTextColor(ContextCompat.getColor(this, R.color.primary))
                view.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
                view.setBackgroundResource(R.drawable.bg_pill)
                view.setTextColor(ContextCompat.getColor(this, R.color.ink_secondary))
                view.typeface = android.graphics.Typeface.DEFAULT
            }
        }
    }

    private fun loadData() {
        if (groupId == -1L) {
            finish()
            return
        }
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val group = app.repository.getGroupById(groupId)
            val title = group?.name?.takeIf { it.isNotBlank() } ?: "我的行程"

            val inputs = withContext(Dispatchers.IO) {
                app.repository.getLinesByGroupIdOnce(groupId).map { line ->
                    TrackMerger.SegmentInput(
                        id = line.id,
                        name = line.name,
                        transportMode = line.transportMode,
                        points = app.repository.getPointsByLineIdOnce(line.id)
                    )
                }
            }

            val plan = app.repository.getMergedPlansByGroupId(groupId).first().firstOrNull()
            val config = MergeConfig.fromJson(plan?.configJson)
            val ordered = config?.order(inputs) ?: inputs
            val includeConnectors = config?.includeConnectors ?: true
            val result = TrackMerger.merge(ordered, includeConnectors)

            this@ShareActivity.title = title
            theResult@ run { lastResult = result }
            val generated = withContext(Dispatchers.Default) {
                if (result.points.isEmpty()) null else ShareCardGenerator.generate(title, result, theme)
            }
            bitmap = generated
            binding.progressBar.visibility = View.GONE
            if (generated != null) {
                binding.ivPreview.setImageBitmap(generated)
            } else {
                Toast.makeText(this@ShareActivity, "暂无可用轨迹生成卡片", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showMoreMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "保存到相册")
            menu.add(0, 2, 1, "系统分享")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        saveImage()
                        true
                    }
                    2 -> {
                        shareImage()
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun saveImage() {
        val current = bitmap ?: run {
            Toast.makeText(this, "卡片尚未生成", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) { saveToGallery(current) }
            val message = if (uri != null) "已保存到相册" else "保存失败，请检查存储权限"
            Toast.makeText(this@ShareActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveToGallery(image: Bitmap): Uri? {
        val fileName = "markmap_${System.currentTimeMillis()}.png"
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MarkMap")
                } else {
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                        "MarkMap"
                    ).apply { mkdirs() }
                    put(MediaStore.Images.Media.DATA, File(dir, fileName).absolutePath)
                }
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            contentResolver.openOutputStream(uri)?.use { out ->
                image.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            uri
        } catch (e: Exception) {
            null
        }
    }

    private fun shareImage() {
        val current = bitmap ?: run {
            Toast.makeText(this, "卡片尚未生成", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                val dir = File(cacheDir, "share").apply { mkdirs() }
                val target = File(dir, "markmap_${System.currentTimeMillis()}.png")
                FileOutputStream(target).use { out ->
                    current.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                target
            }
            val uri = FileProvider.getUriForFile(this@ShareActivity, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "分享行程卡"))
        }
    }
}
