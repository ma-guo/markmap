package com.zuxing.markmap

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.zuxing.markmap.data.adapter.GroupAdapter
import com.zuxing.markmap.databinding.ActivityGroupListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GroupListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupListBinding
    private lateinit var app: MarkMapApplication
    private lateinit var adapter: GroupAdapter
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as MarkMapApplication
        prefs = getSharedPreferences("scroll_positions", 0)

        setupToolbar()
        setupRecyclerView()
        loadGroups()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
    }

    override fun onPause() {
        super.onPause()
        val position = (binding.recyclerView.layoutManager as? LinearLayoutManager)
            ?.findFirstCompletelyVisibleItemPosition() ?: 0
        prefs.edit().putInt("group_list", position).apply()
    }

    private fun getSavedScrollPosition(): Int {
        return prefs.getInt("group_list", 0)
    }

    private fun setupRecyclerView() {
        adapter = GroupAdapter(
            onItemClick = { group ->
                navigateToLineList(group.id)
            },
            onItemLongClick = { group ->
                navigateToEdit(group.id)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.fabAdd.setIcon(R.drawable.add_24px)
        binding.fabAdd.setOnClickListener {
            navigateToEdit(null)
        }
    }

    private fun loadGroups() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            app.repository.getAllGroups().collectLatest { groups ->
                binding.progressBar.visibility = View.GONE
                if (groups.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                    adapter.submitList(groups)
                    val savedPosition = getSavedScrollPosition()
                    if (savedPosition > 0 && savedPosition < groups.size) {
                        binding.recyclerView.post {
                            binding.recyclerView.scrollToPosition(savedPosition)
                        }
                    }
                }
            }
        }
    }

    private fun navigateToEdit(groupId: Long? = null) {
        val intent = Intent(this, GroupEditActivity::class.java).apply {
            putExtra("groupId", groupId ?: -1L)
        }
        startActivity(intent)
    }

    private fun navigateToLineList(groupId: Long) {
        val intent = Intent(this, LineListActivity::class.java).apply {
            putExtra("groupId", groupId)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        loadGroups()
    }
}
