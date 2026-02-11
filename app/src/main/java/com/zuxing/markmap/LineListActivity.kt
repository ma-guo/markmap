package com.zuxing.markmap

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.zuxing.markmap.data.adapter.LineAdapter
import com.zuxing.markmap.databinding.ActivityLineListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LineListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLineListBinding
    private lateinit var app: MarkMapApplication
    private lateinit var adapter: LineAdapter
    private lateinit var prefs: SharedPreferences
    private var groupId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLineListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupId = intent.getLongExtra("groupId", -1L)
        app = application as MarkMapApplication
        prefs = getSharedPreferences("scroll_positions", 0)

        setupToolbar()
        setupRecyclerView()
        loadLines()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_line_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_map -> {
                navigateToLineMap()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPause() {
        super.onPause()
        saveScrollPosition()
    }

    private fun saveScrollPosition() {
        val position = (binding.recyclerView.layoutManager as? LinearLayoutManager)
            ?.findFirstCompletelyVisibleItemPosition() ?: 0
        prefs.edit().putInt("line_list_$groupId", position).apply()
    }

    private fun getSavedScrollPosition(): Int {
        return prefs.getInt("line_list_$groupId", 0)
    }

    private fun setupRecyclerView() {
        adapter = LineAdapter(
            onItemClick = { line ->
                navigateToPointList(line.id)
            },
            onItemLongClick = { line ->
                navigateToEdit(line.id)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.fabAdd.setIcon(R.drawable.add_24px)
        binding.fabAdd.setOnClickListener {
            navigateToEdit(null)
        }
    }

    private fun loadLines() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            app.repository.getLinesByGroupId(groupId).collectLatest { lines ->
                binding.progressBar.visibility = View.GONE
                if (lines.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                    adapter.submitList(lines)
                    val savedPosition = getSavedScrollPosition()
                    if (savedPosition > 0 && savedPosition < lines.size) {
                        binding.recyclerView.post {
                            binding.recyclerView.scrollToPosition(savedPosition)
                        }
                    }
                }
            }
        }
    }

    private fun navigateToEdit(lineId: Long?) {
        val intent = Intent(this, LineEditActivity::class.java).apply {
            putExtra("groupId", groupId)
            putExtra("lineId", lineId ?: -1L)
        }
        startActivity(intent)
    }

    private fun navigateToPointList(lineId: Long) {
        val intent = Intent(this, PointListActivity::class.java).apply {
            putExtra("lineId", lineId)
        }
        startActivity(intent)
    }

    private fun navigateToLineMap() {
        val intent = Intent(this, LineMapActivity::class.java).apply {
            putExtra("lineId", -1L)
            putExtra("groupId", groupId)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        loadLines()
    }
}
