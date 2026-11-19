package com.zuxing.markmap

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.zuxing.markmap.data.adapter.GroupAdapter
import com.zuxing.markmap.data.adapter.ModeCount
import com.zuxing.markmap.data.adapter.TripCardItem
import com.zuxing.markmap.databinding.ActivityGroupListBinding
import com.zuxing.markmap.domain.TrackStats
import com.google.android.material.snackbar.Snackbar
import com.zuxing.markmap.domain.TransportMode
import com.zuxing.markmap.widget.SwipeToDeleteCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class GroupListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupListBinding
    private lateinit var app: MarkMapApplication
    private lateinit var adapter: GroupAdapter
    private lateinit var prefs: SharedPreferences

    private var allItems: List<TripCardItem> = emptyList()
    private var query: String = ""
    private val recordDays = mutableSetOf<Long>()
    private var memoryTripName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as MarkMapApplication
        prefs = getSharedPreferences("scroll_positions", 0)

        setupHeader()
        setupRecyclerView()
        setupTabs()
        loadGroups()
    }

    private fun setupHeader() {
        binding.btnMore.setOnClickListener { showMoreMenu(it) }
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim().orEmpty()
                applyFilter()
            }
        })
    }

    private fun setupTabs() {
        binding.tabFootprint.setOnClickListener {
            startActivity(Intent(this, FootprintActivity::class.java))
        }
        binding.tabMine.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun showMoreMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "车次轨迹")
            menu.add(0, 2, 1, "余票查询")
            menu.add(0, 3, 2, "航班参考")
            menu.add(0, 4, 3, "搜索")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        showTrainInputDialog()
                        true
                    }
                    2 -> {
                        startActivity(Intent(this@GroupListActivity, TicketQueryActivity::class.java))
                        true
                    }
                    3 -> {
                        startActivity(Intent(this@GroupListActivity, FlightActivity::class.java))
                        true
                    }
                    4 -> {
                        startActivity(Intent(this@GroupListActivity, SearchActivity::class.java))
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    override fun onPause() {
        super.onPause()
        val position = (binding.recyclerView.layoutManager as? LinearLayoutManager)
            ?.findFirstCompletelyVisibleItemPosition() ?: 0
        prefs.edit().putInt("group_list", position).apply()
    }

    private fun getSavedScrollPosition(): Int = prefs.getInt("group_list", 0)

    private fun setupRecyclerView() {
        adapter = GroupAdapter(
            onItemClick = { item -> navigateToDetail(item.groupId) },
            onItemLongClick = { item -> navigateToEdit(item.groupId) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        ItemTouchHelper(SwipeToDeleteCallback(this) { position ->
            adapter.currentList.getOrNull(position)?.let { deleteGroup(it) }
        }).attachToRecyclerView(binding.recyclerView)

        binding.fabAdd.setIcon(R.drawable.add_24px)
        binding.fabAdd.setOnClickListener { navigateToEdit(null) }
    }

    private fun loadGroups() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            app.repository.getAllGroups().collectLatest { groups ->
                val items = withContext(Dispatchers.IO) { buildItems(groups) }
                allItems = items
                binding.progressBar.visibility = View.GONE
                updateGreeting()
                applyFilter()
            }
        }
    }

    private suspend fun buildItems(groups: List<com.zuxing.markmap.data.entity.GroupEntity>): List<TripCardItem> {
        return groups.mapIndexed { index, group ->
            val lines = app.repository.getLinesByGroupIdOnce(group.id)
            var distance = 0.0
            var duration = 0L
            var minTime = Long.MAX_VALUE
            var maxTime = Long.MIN_VALUE
            val modeCounts = LinkedHashMap<TransportMode, Int>()
            val routeCoords = mutableListOf<Pair<Double, Double>>()

            lines.forEach { line ->
                val points = app.repository.getPointsByLineIdOnce(line.id)
                points.forEach { routeCoords.add(it.latitude to it.longitude) }
                points.forEach { p -> recordDays.add(dayStart(p.createTime)) }
                val stats = TrackStats.fromPoints(points)
                distance += stats.distanceMeters
                val lineDuration = if (line.startTime != null && line.endTime != null &&
                    line.endTime!! > line.startTime!!
                ) {
                    line.endTime!! - line.startTime!!
                } else {
                    stats.durationMillis
                }
                if (lineDuration > 0) duration += lineDuration
                stats.startTime?.let { if (it < minTime) minTime = it }
                stats.endTime?.let { if (it > maxTime) maxTime = it }
                val mode = TransportMode.from(line.transportMode)
                modeCounts[mode] = (modeCounts[mode] ?: 0) + 1
            }

            val modes = modeCounts.entries
                .sortedByDescending { it.value }
                .map { ModeCount(it.key, it.value) }

            TripCardItem(
                groupId = group.id,
                name = group.name,
                distanceMeters = distance,
                segmentCount = lines.size,
                durationMillis = duration,
                dateText = formatDateRange(
                    minTime.takeIf { minTime != Long.MAX_VALUE },
                    maxTime.takeIf { maxTime != Long.MIN_VALUE }
                ),
                modes = modes,
                mossVariant = index % 2 == 1,
                route = sampleRoute(routeCoords)
            )
        }
    }

    private fun sampleRoute(coords: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (coords.size <= 400) return coords
        val step = coords.size / 400 + 1
        return coords.filterIndexed { index, _ -> index % step == 0 }
    }

    private fun dayStart(ms: Long): Long {
        val c = java.util.Calendar.getInstance()
        c.timeInMillis = ms
        c.set(java.util.Calendar.HOUR_OF_DAY, 0)
        c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun computeStreak(): Int {
        if (recordDays.isEmpty()) return 0
        var streak = 0
        var day = dayStart(System.currentTimeMillis())
        while (recordDays.contains(day)) {
            streak++
            day -= 24L * 3600 * 1000
        }
        return streak
    }

    private fun updateGreeting() {
        val streak = computeStreak()
        val base = if (streak > 0) "已连续记录 $streak 天，继续加油。" else "记录每一段旅程，随时回看与分享。"
        binding.tvGreeting.text = base
    }

    private fun applyFilter() {
        val filtered = if (query.isBlank()) {
            allItems
        } else {
            allItems.filter { it.name.contains(query, ignoreCase = true) }
        }
        if (filtered.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
            adapter.submitList(filtered)
        }
    }

    private fun formatDateRange(start: Long?, end: Long?): String {
        if (start == null) return ""
        val format = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
        val startText = format.format(Date(start))
        if (end == null || end == start) return startText
        val endText = format.format(Date(end))
        return "$startText – ${endText.substring(5)}"
    }

    private fun showTrainInputDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_train_keyboard, null)
        val etTrainCode = dialogView.findViewById<EditText>(R.id.etTrainCode)
        val tvDateValue = dialogView.findViewById<TextView>(R.id.tvDateValue)
        val dateContainer = dialogView.findViewById<View>(R.id.dateContainer)
            ?: tvDateValue.parent as? View ?: tvDateValue

        val calendar = Calendar.getInstance()
        var selectedYear = calendar.get(Calendar.YEAR)
        var selectedMonth = calendar.get(Calendar.MONTH)
        var selectedDay = calendar.get(Calendar.DAY_OF_MONTH)

        fun updateDateDisplay() {
            val dateStr = String.format(Locale.CHINA, "%04d%02d%02d", selectedYear, selectedMonth + 1, selectedDay)
            tvDateValue.text = dateStr
            tvDateValue.tag = dateStr
        }
        updateDateDisplay()

        dateContainer.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                selectedYear = year
                selectedMonth = month
                selectedDay = day
                updateDateDisplay()
            }, selectedYear, selectedMonth, selectedDay).show()
        }

        val keyIds = listOf(
            R.id.keyG, R.id.keyD, R.id.keyC, R.id.keyK, R.id.keyL, R.id.keyT,
            R.id.keyZ, R.id.keyY, R.id.keyS,
            R.id.key0, R.id.key1, R.id.key2, R.id.key3, R.id.key4,
            R.id.key5, R.id.key6, R.id.key7, R.id.key8, R.id.key9,
            R.id.keyX
        )

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        keyIds.forEach { id ->
            dialogView.findViewById<View>(id).setOnClickListener { v ->
                val tag = v.tag as? String ?: return@setOnClickListener
                if (tag == "X") etTrainCode.setText("") else etTrainCode.append(tag)
            }
        }

        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btnConfirm).setOnClickListener {
            val trainCode = etTrainCode.text.toString().trim()
            val date = tvDateValue.tag as? String ?: ""
            if (trainCode.isEmpty()) {
                Toast.makeText(this, "请输入列车车次", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (date.isEmpty()) {
                Toast.makeText(this, "请选择日期", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            startActivity(Intent(this, TrainMapActivity::class.java).apply {
                putExtra("trainCode", trainCode)
                putExtra("date", date)
            })
        }

        dialog.show()
    }

    private fun deleteGroup(item: TripCardItem) {
        lifecycleScope.launch {
            app.repository.softDeleteGroup(item.groupId)
            Snackbar.make(binding.root, "已删除「${item.name}」", Snackbar.LENGTH_LONG)
                .setAction("撤销") {
                    lifecycleScope.launch { app.repository.restoreGroup(item.groupId) }
                }
                .show()
        }
    }

    private fun navigateToEdit(groupId: Long? = null) {
        startActivity(Intent(this, GroupEditActivity::class.java).apply {
            putExtra("groupId", groupId ?: -1L)
        })
    }

    private fun navigateToDetail(groupId: Long) {
        startActivity(Intent(this, TripDetailActivity::class.java).apply {
            putExtra("groupId", groupId)
        })
    }

    override fun onResume() {
        super.onResume()
        loadGroups()
    }
}
