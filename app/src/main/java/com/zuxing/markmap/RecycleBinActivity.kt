package com.zuxing.markmap

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.zuxing.markmap.data.adapter.RecycleAdapter
import com.zuxing.markmap.data.adapter.RecycleItem
import com.zuxing.markmap.data.adapter.RecycleType
import com.zuxing.markmap.databinding.ActivityRecycleBinBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RecycleBinActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecycleBinBinding
    private lateinit var app: MarkMapApplication
    private lateinit var adapter: RecycleAdapter

    private var currentType: RecycleType = RecycleType.GROUP
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecycleBinBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as MarkMapApplication

        setupToolbar()
        setupRecyclerView()
        setupSegments()
        selectType(RecycleType.GROUP)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = RecycleAdapter(
            onRestore = { item -> restore(item) },
            onPurge = { item -> confirmPurge(item) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupSegments() {
        binding.segGroups.setOnClickListener { selectType(RecycleType.GROUP) }
        binding.segLines.setOnClickListener { selectType(RecycleType.LINE) }
        binding.segPoints.setOnClickListener { selectType(RecycleType.POINT) }
    }

    private fun selectType(type: RecycleType) {
        currentType = type
        highlight(binding.segGroups, type == RecycleType.GROUP)
        highlight(binding.segLines, type == RecycleType.LINE)
        highlight(binding.segPoints, type == RecycleType.POINT)
        load()
    }

    private fun highlight(view: TextView, selected: Boolean) {
        if (selected) {
            view.setBackgroundResource(R.drawable.bg_chip)
            view.setTextColor(ContextCompat.getColor(this, R.color.primary))
            view.typeface = android.graphics.Typeface.DEFAULT_BOLD
        } else {
            view.setBackgroundResource(R.drawable.bg_pill)
            view.setTextColor(ContextCompat.getColor(this, R.color.ink_secondary))
            view.typeface = android.graphics.Typeface.DEFAULT
        }
    }

    private fun load() {
        loadJob?.cancel()
        binding.progressBar.visibility = View.VISIBLE
        loadJob = lifecycleScope.launch {
            when (currentType) {
                RecycleType.GROUP -> app.repository.getDeletedGroups().collectLatest { groups ->
                    submit(
                        groups.map {
                            RecycleItem(RecycleType.GROUP, it.id, it.name, it.deleteTime)
                        }
                    )
                }
                RecycleType.LINE -> app.repository.getDeletedLines().collectLatest { lines ->
                    submit(
                        lines.map {
                            RecycleItem(RecycleType.LINE, it.id, it.name, it.deleteTime)
                        }
                    )
                }
                RecycleType.POINT -> app.repository.getDeletedPoints().collectLatest { points ->
                    submit(
                        points.map {
                            val label = it.description?.takeIf { d -> d.isNotBlank() }
                                ?: it.address?.takeIf { a -> a.isNotBlank() }
                                ?: "${it.latitude}, ${it.longitude}"
                            RecycleItem(RecycleType.POINT, it.id, label, it.deleteTime)
                        }
                    )
                }
            }
        }
    }

    private fun submit(items: List<RecycleItem>) {
        binding.progressBar.visibility = View.GONE
        if (items.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
        adapter.submitList(items)
    }

    private fun restore(item: RecycleItem) {
        lifecycleScope.launch {
            when (item.type) {
                RecycleType.GROUP -> app.repository.restoreGroup(item.id)
                RecycleType.LINE -> app.repository.restoreLine(item.id)
                RecycleType.POINT -> app.repository.restorePoint(item.id)
            }
            Toast.makeText(this@RecycleBinActivity, "已恢复", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmPurge(item: RecycleItem) {
        AlertDialog.Builder(this)
            .setTitle("彻底删除")
            .setMessage("确定要彻底删除「${item.name}」吗？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    when (item.type) {
                        RecycleType.GROUP -> app.repository.deleteGroupById(item.id)
                        RecycleType.LINE -> app.repository.deleteLineById(item.id)
                        RecycleType.POINT -> app.repository.deletePointById(item.id)
                    }
                    Toast.makeText(this@RecycleBinActivity, "已彻底删除", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
