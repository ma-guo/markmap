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
import com.zuxing.markmap.data.adapter.PointAdapter
import com.zuxing.markmap.databinding.FragmentPointListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PointListActivity : AppCompatActivity() {

    private lateinit var binding: FragmentPointListBinding
    private lateinit var app: MarkMapApplication
    private lateinit var adapter: PointAdapter
    private lateinit var prefs: SharedPreferences
    private var lineId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentPointListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lineId = intent.getLongExtra("lineId", -1L)
        app = application as MarkMapApplication
        prefs = getSharedPreferences("scroll_positions", 0)

        setupToolbar()
        setupRecyclerView()
        loadPoints()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_point_list, menu)
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
        val position = (binding.recyclerView.layoutManager as? LinearLayoutManager)
            ?.findFirstCompletelyVisibleItemPosition() ?: 0
        prefs.edit().putInt("point_list_$lineId", position).apply()
    }

    private fun getSavedScrollPosition(): Int {
        return prefs.getInt("point_list_$lineId", 0)
    }

    private fun setupRecyclerView() {
        adapter = PointAdapter { point ->
            navigateToEdit(point.id)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.fabAdd.setIcon(R.drawable.add_24px)
        binding.fabAdd.setOnClickListener {
            navigateToMap()
        }
    }

    private fun loadPoints() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            app.repository.getPointsByLineId(lineId).collectLatest { points ->
                binding.progressBar.visibility = View.GONE
                if (points.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                    adapter.submitList(points)
                    val savedPosition = getSavedScrollPosition()
                    if (savedPosition > 0 && savedPosition < points.size) {
                        binding.recyclerView.post {
                            binding.recyclerView.scrollToPosition(savedPosition)
                        }
                    }
                }
            }
        }
    }

    private fun navigateToEdit(pointId: Long) {
        val intent = Intent(this, PointEditActivity::class.java).apply {
            putExtra("lineId", lineId)
            putExtra("pointId", pointId)
        }
        startActivity(intent)
    }

    private fun navigateToMap() {
        val intent = Intent(this, MapActivity::class.java).apply {
            putExtra("lineId", lineId)
        }
        startActivity(intent)
    }

    private fun navigateToLineMap() {
        lifecycleScope.launch {
            val line = withContext(Dispatchers.IO) {
                app.repository.getLineById(lineId)
            }
            val groupId = line?.groupId ?: -1L
            val intent = Intent(this@PointListActivity, LineMapActivity::class.java).apply {
                putExtra("lineId", lineId)
                putExtra("groupId", groupId)
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        loadPoints()
    }
}
