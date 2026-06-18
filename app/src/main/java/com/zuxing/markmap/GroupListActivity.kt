package com.zuxing.markmap

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.zuxing.markmap.data.adapter.GroupAdapter
import com.zuxing.markmap.databinding.ActivityGroupListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_group_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_train -> {
                showTrainInputDialog()
                true
            }
            R.id.action_ticket -> {
                startActivity(Intent(this, TicketQueryActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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

    private fun showTrainInputDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_train_keyboard, null)
        val etTrainCode = dialogView.findViewById<EditText>(R.id.etTrainCode)
        val tvDateValue = dialogView.findViewById<TextView>(R.id.tvDateValue)
        val dateContainer = dialogView.findViewById<View>(R.id.dateContainer) ?: tvDateValue.parent as? View ?: tvDateValue

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
                if (tag == "X") {
                    etTrainCode.setText("")
                } else {
                    etTrainCode.append(tag)
                }
            }
        }

        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

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
