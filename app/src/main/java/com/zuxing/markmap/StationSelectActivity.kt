package com.zuxing.markmap

import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zuxing.markmap.databinding.ActivityStationSelectBinding
import com.zuxing.markmap.databinding.ItemStationBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class StationSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStationSelectBinding
    private lateinit var trainService: TrainService
    private lateinit var prefs: SharedPreferences
    private lateinit var adapter: StationAdapter
    private var allStations = listOf<StationEntity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStationSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("stations", MODE_PRIVATE)

        setupToolbar()
        setupRecyclerView()
        setupSearch()
        initService()
        loadStations()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = StationAdapter { station ->
            val code = station.stationTelecode ?: ""
            intent.putExtra("stationCode", code)
            intent.putExtra("stationName", station.stationName ?: "")
            setResult(RESULT_OK, intent)
            finish()
        }
        binding.rvStations.layoutManager = LinearLayoutManager(this)
        binding.rvStations.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filter(s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun initService() {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://mobile.12306.cn/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        trainService = retrofit.create(TrainService::class.java)
    }

    private fun loadStations() {
        val cached = prefs.getString("station_list_json", null)
        if (!cached.isNullOrEmpty()) {
            allStations = parseStationList(cached)
            filter(binding.etSearch.text?.toString() ?: "")
            return
        }
        fetchFromApi()
    }

    private fun fetchFromApi() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    trainService.getAllStations().execute()
                }
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body()?.status == true) {
                        val stations = response.body()?.data?.stations ?: emptyList()
                        allStations = stations
                        saveCache()
                        filter(binding.etSearch.text?.toString() ?: "")
                        if (stations.isNotEmpty()) {
                            binding.tvEmpty.visibility = View.GONE
                        } else {
                            binding.tvEmpty.text = "暂无车站数据"
                            binding.tvEmpty.visibility = View.VISIBLE
                        }
                    } else {
                        Logger.e("获取车站列表失败")
                        showLoadError()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Logger.e("获取车站列表异常", e)
                    showLoadError()
                }
            }
        }
    }

    private fun saveCache() {
        val json = Gson().toJson(allStations)
        prefs.edit().putString("station_list_json", json).apply()
    }

    private fun parseStationList(json: String): List<StationEntity> {
        val type = object : TypeToken<List<StationEntity>>() {}.type
        return try {
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun filter(query: String) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            allStations
        } else {
            allStations.filter { s ->
                (s.stationName ?: "").contains(q, ignoreCase = true) ||
                (s.stationPycode ?: "").lowercase().contains(q) ||
                (s.stationFirstcode ?: "").lowercase().contains(q) ||
                (s.stationTelecode ?: "").lowercase().contains(q)
            }
        }
        adapter.submitList(filtered)
        binding.tvEmpty.visibility = if (filtered.isEmpty() && allStations.isNotEmpty()) View.VISIBLE else View.GONE
        if (filtered.isEmpty() && allStations.isNotEmpty()) {
            binding.tvEmpty.text = "无匹配车站"
        }
    }

    private fun showLoadError() {
        binding.tvEmpty.text = allStations.isEmpty().let {
            if (it) "加载失败，请刷新" else "无匹配车站"
        }
        binding.tvEmpty.visibility = View.VISIBLE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_station_select, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                fetchFromApi()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private class StationAdapter(
        private val onItemClick: (StationEntity) -> Unit
    ) : ListAdapter<StationEntity, StationAdapter.ViewHolder>(DiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemStationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class ViewHolder(
            private val binding: ItemStationBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(station: StationEntity) {
                binding.tvStationName.text = station.stationName ?: ""
                binding.tvStationCode.text = station.stationTelecode ?: ""
                binding.tvStationProvince.text = station.provinceName ?: ""
                binding.root.setOnClickListener { onItemClick(station) }
            }
        }

        private class DiffCallback : DiffUtil.ItemCallback<StationEntity>() {
            override fun areItemsTheSame(a: StationEntity, b: StationEntity): Boolean =
                a.stationTelecode == b.stationTelecode
            override fun areContentsTheSame(a: StationEntity, b: StationEntity): Boolean =
                a == b
        }
    }
}