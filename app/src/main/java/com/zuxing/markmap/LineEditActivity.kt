package com.zuxing.markmap

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.zuxing.markmap.data.entity.GroupEntity
import com.zuxing.markmap.data.entity.LineEntity
import com.zuxing.markmap.databinding.ActivityLineEditBinding
import com.zuxing.markmap.domain.TransportMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LineEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLineEditBinding
    private lateinit var app: MarkMapApplication
    private var currentLine: LineEntity? = null
    private var isEditMode: Boolean = false
    private var groupId: Long = -1L
    private var lineId: Long = -1L
    private var groupList: List<GroupEntity> = emptyList()
    private var groupItems: List<GroupItem> = emptyList()
    private var selectedGroupId: Long = -1L
    private var selectedGroupName: String = ""
    private var selectedMode: TransportMode = TransportMode.OTHER

    data class GroupItem(val id: Long, val name: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLineEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as MarkMapApplication

        groupId = intent.getLongExtra("groupId", -1L)
        lineId = intent.getLongExtra("lineId", -1L)
        isEditMode = lineId != -1L

        setupToolbar()
        setupViews()
        setupModeDropdown()
        loadGroups()

        if (isEditMode) {
            loadLine()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_line_edit, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete -> {
                showDeleteConfirmDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDeleteConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("删除路段")
            .setMessage("确定要删除路段 \"${currentLine?.name}\" 吗？删除后可在回收站恢复。")
            .setPositiveButton("删除") { _, _ ->
                deleteLine()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteLine() {
        currentLine?.let { line ->
            lifecycleScope.launch {
                app.repository.softDeleteLine(line.id)
                Toast.makeText(this@LineEditActivity, "路段已删除", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun setupViews() {
        binding.etName.addTextChangedListener {
            updateSaveButtonState()
        }

        binding.actvGroup.setOnItemClickListener { _, _, position, _ ->
            val selectedItem = groupItems[position]
            selectedGroupId = selectedItem.id
            selectedGroupName = selectedItem.name
            updateSaveButtonState()
        }

        binding.btnSave.setOnClickListener {
            saveLine()
        }
    }

    private fun setupModeDropdown() {
        val labels = TransportMode.values().map { it.label }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels)
        binding.actvMode.setAdapter(adapter)
        binding.actvMode.setText(selectedMode.label, false)
        binding.actvMode.setOnItemClickListener { _, _, position, _ ->
            selectedMode = TransportMode.values()[position]
            updateSaveButtonState()
        }
    }

    private fun loadGroups() {
        lifecycleScope.launch {
            groupList = app.repository.getAllGroups().first()
            setupGroupDropdown()
        }
    }

    private fun setupGroupDropdown() {
        groupItems = groupList.map { GroupItem(it.id, it.name) }
        val groupNames = groupList.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, groupNames)
        binding.actvGroup.setAdapter(adapter)

        if (isEditMode && currentLine != null) {
            val group = groupList.find { it.id == currentLine!!.groupId }
            group?.let {
                selectedGroupId = it.id
                selectedGroupName = it.name
                binding.actvGroup.setText(it.name, false)
            }
        } else if (groupId != -1L) {
            val group = groupList.find { it.id == groupId }
            group?.let {
                selectedGroupId = it.id
                selectedGroupName = it.name
                binding.actvGroup.setText(it.name, false)
            }
        }
        updateSaveButtonState()
    }

    private fun updateSaveButtonState() {
        if (isEditMode && currentLine != null) {
            val name = binding.etName.text.toString().trim()
            val hasChanges = name.isNotBlank() &&
                    selectedGroupId != -1L &&
                    (name != currentLine!!.name ||
                            selectedGroupId != currentLine!!.groupId ||
                            selectedMode.name != currentLine!!.transportMode)
            binding.btnSave.isEnabled = hasChanges
        } else {
            binding.btnSave.isEnabled = binding.etName.text!!.isNotBlank() && selectedGroupId != -1L
        }
    }

    private fun loadLine() {
        lifecycleScope.launch {
            currentLine = app.repository.getLineById(lineId)
            currentLine?.let { line ->
                binding.etName.setText(line.name)
                binding.tvCreateTime.text = "创建时间: ${formatTime(line.createTime)}"
                binding.tvCreateTime.visibility = android.view.View.VISIBLE

                selectedMode = TransportMode.from(line.transportMode)
                binding.actvMode.setText(selectedMode.label, false)

                selectedGroupId = line.groupId
                val group = groupList.find { it.id == line.groupId }
                group?.let {
                    selectedGroupName = it.name
                    binding.actvGroup.setText(it.name, false)
                }
                updateSaveButtonState()
            }
        }
    }

    private fun saveLine() {
        val name = binding.etName.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(this, "请输入路段名称", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedGroupId == -1L) {
            Toast.makeText(this, "请选择所属行程", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            if (isEditMode && currentLine != null) {
                val hasChanges = name != currentLine!!.name ||
                        selectedGroupId != currentLine!!.groupId ||
                        selectedMode.name != currentLine!!.transportMode

                if (!hasChanges) {
                    Toast.makeText(this@LineEditActivity, "未有任何变化", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val line = currentLine!!.copy(
                    name = name,
                    groupId = selectedGroupId,
                    transportMode = selectedMode.name
                )
                app.repository.updateLine(line)
            } else {
                val line = LineEntity(
                    id = 0,
                    name = name,
                    groupId = selectedGroupId,
                    sortOrder = 0,
                    transportMode = selectedMode.name,
                    createTime = System.currentTimeMillis()
                )
                app.repository.insertLine(line)
            }

            Toast.makeText(this@LineEditActivity, "已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun formatTime(time: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(time))
    }
}
