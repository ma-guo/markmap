package com.zuxing.markmap

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.zuxing.markmap.data.entity.GroupEntity
import com.zuxing.markmap.data.entity.LineEntity
import com.zuxing.markmap.databinding.ActivityLineEditBinding
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

    private fun setupViews() {
        binding.etName.addTextChangedListener {
            updateSaveButtonState()
        }

        binding.etSortOrder.addTextChangedListener {
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
    }

    private fun updateSaveButtonState() {
        if (isEditMode && currentLine != null) {
            val name = binding.etName.text.toString().trim()
            val sortOrder = binding.etSortOrder.text.toString().toIntOrNull() ?: 0
            val hasChanges = name.isNotBlank() &&
                    selectedGroupId != -1L &&
                    (name != currentLine!!.name ||
                            selectedGroupId != currentLine!!.groupId ||
                            sortOrder != currentLine!!.sortOrder)
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
                binding.etSortOrder.setText(line.sortOrder.toString())
                binding.tvCreateTime.text = "创建时间: ${formatTime(line.createTime)}"
                binding.tvCreateTime.visibility = android.view.View.VISIBLE

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
            Toast.makeText(this, "请输入线名", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedGroupId == -1L) {
            Toast.makeText(this, "请选择所属组", Toast.LENGTH_SHORT).show()
            return
        }

        val sortOrder = binding.etSortOrder.text.toString().toIntOrNull() ?: 0

        lifecycleScope.launch {
            if (isEditMode && currentLine != null) {
                val hasChanges = name != currentLine!!.name ||
                        selectedGroupId != currentLine!!.groupId ||
                        sortOrder != currentLine!!.sortOrder

                if (!hasChanges) {
                    Toast.makeText(this@LineEditActivity, "未有任何变化", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val line = currentLine!!.copy(
                    name = name,
                    groupId = selectedGroupId,
                    sortOrder = sortOrder
                )
                app.repository.updateLine(line)
            } else {
                val line = LineEntity(
                    id = if (isEditMode) lineId else 0,
                    name = name,
                    groupId = selectedGroupId,
                    sortOrder = sortOrder,
                    createTime = currentLine?.createTime ?: System.currentTimeMillis()
                )
                if (isEditMode) {
                    app.repository.updateLine(line)
                } else {
                    app.repository.insertLine(line)
                }
            }

            Toast.makeText(this@LineEditActivity, "保存成功", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun formatTime(time: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(time))
    }
}
