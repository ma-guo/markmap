package com.zuxing.markmap

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zuxing.markmap.databinding.ActivitySearchBinding
import com.zuxing.markmap.databinding.ItemSearchBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var app: MarkMapApplication
    private lateinit var adapter: SearchAdapter
    private var groupsById = mapOf<Long, String>()

    data class Result(
        val type: String,
        val name: String,
        val sub: String,
        val groupId: Long,
        val lineId: Long,
        val pointId: Long
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as MarkMapApplication
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = SearchAdapter { r -> open(r) }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.etQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { search(s?.toString()?.trim().orEmpty()) }
        })

        lifecycleScope.launch {
            groupsById = withContext(Dispatchers.IO) {
                app.repository.getAllGroups().first().associate { it.id to it.name }
            }
            val initial = intent.getStringExtra("query").orEmpty()
            binding.etQuery.setText(initial)
            binding.etQuery.setSelection(initial.length)
            search(initial)
        }
    }

    private fun search(q: String) {
        if (q.isBlank()) { adapter.submitList(emptyList()); binding.tvEmpty.visibility = View.GONE; return }
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) {
                val out = mutableListOf<Result>()
                // 行程
                app.repository.getAllGroups().first()
                    .filter { it.name.contains(q, true) }
                    .forEach { g -> out.add(Result("行程", g.name, "行程", g.id, -1L, -1L)) }
                // 地点
                app.repository.getAllPoints().first()
                    .filter { p ->
                        (p.description?.contains(q, true) == true) ||
                            (p.address?.contains(q, true) == true) ||
                            p.latitude.toString().contains(q) || p.longitude.toString().contains(q)
                    }
                    .take(200)
                    .forEach { p ->
                        val line = app.repository.getLineById(p.lineId)
                        val gid = line?.groupId ?: -1L
                        val gname = groupsById[gid] ?: ""
                        val title = p.description?.takeIf { it.isNotBlank() }
                            ?: p.address?.takeIf { it.isNotBlank() } ?: "地点"
                        val sub = listOfNotNull(
                            p.address?.takeIf { it.isNotBlank() },
                            if (gname.isNotBlank()) "行程「$gname」" else null
                        ).joinToString(" · ")
                        out.add(Result("地点", title, sub, gid, p.lineId, p.id))
                    }
                out
            }
            adapter.submitList(results)
            binding.tvEmpty.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun open(r: Result) {
        when (r.type) {
            "行程" -> startActivity(Intent(this, TripDetailActivity::class.java).apply { putExtra("groupId", r.groupId) })
            "地点" -> {
                startActivity(Intent(this, PointEditActivity::class.java).apply {
                    putExtra("lineId", r.lineId)
                    putExtra("pointId", r.pointId)
                })
            }
        }
    }

    private class SearchAdapter(private val onClick: (Result) -> Unit) :
        ListAdapter<Result, SearchAdapter.VH>(Diff()) {

        class VH(val binding: ItemSearchBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = getItem(position)
            holder.binding.tvType.text = r.type
            holder.binding.tvName.text = r.name
            holder.binding.tvSub.text = r.sub
            holder.binding.root.setOnClickListener { onClick(r) }
        }

        class Diff : DiffUtil.ItemCallback<Result>() {
            override fun areItemsTheSame(a: Result, b: Result) =
                a.type == b.type && a.pointId == b.pointId && a.groupId == b.groupId
            override fun areContentsTheSame(a: Result, b: Result) = a == b
        }
    }
}
