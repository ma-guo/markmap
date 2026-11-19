package com.zuxing.markmap

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.zuxing.markmap.data.entity.GroupEntity
import com.zuxing.markmap.databinding.ActivityGroupEditBinding
import kotlinx.coroutines.launch

class GroupEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupEditBinding
    private lateinit var app: MarkMapApplication
    private var currentGroup: GroupEntity? = null
    private var isEditMode: Boolean = false
    private var groupId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as MarkMapApplication

        groupId = intent.getLongExtra("groupId", -1L)
        isEditMode = groupId != -1L

        setupToolbar()
        setupViews()

        if (isEditMode) {
            loadGroup()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (isEditMode) {
            menuInflater.inflate(R.menu.menu_group_edit, menu)
        }
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
            .setTitle("删除行程")
            .setMessage("确定要删除行程 \"${currentGroup?.name}\" 吗？删除后可在回收站恢复。")
            .setPositiveButton("删除") { _, _ -> deleteGroup() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteGroup() {
        lifecycleScope.launch {
            app.repository.softDeleteGroup(groupId)
            Toast.makeText(this@GroupEditActivity, "行程已删除", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupViews() {
        binding.etName.addTextChangedListener {
            updateSaveButtonState()
        }

        binding.btnSave.setOnClickListener {
            saveGroup()
        }
    }

    private fun updateSaveButtonState() {
        if (isEditMode && currentGroup != null) {
            val name = binding.etName.text.toString().trim()
            val hasChanges = name.isNotBlank() && name != currentGroup!!.name
            binding.btnSave.isEnabled = hasChanges
        } else {
            binding.btnSave.isEnabled = binding.etName.text!!.isNotBlank()
        }
    }

    private fun loadGroup() {
        lifecycleScope.launch {
            currentGroup = app.repository.getGroupById(groupId)
            currentGroup?.let { group ->
                binding.etName.setText(group.name)
                binding.tvCreateTime.text = "创建时间: ${formatTime(group.createTime)}"
                binding.tvCreateTime.visibility = android.view.View.VISIBLE
                updateSaveButtonState()
            }
        }
    }

    private fun saveGroup() {
        val name = binding.etName.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(this, "请输入行程名称", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            if (isEditMode && currentGroup != null) {
                val hasChanges = name != currentGroup!!.name

                if (!hasChanges) {
                    Toast.makeText(this@GroupEditActivity, "未有任何变化", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val group = currentGroup!!.copy(name = name)
                app.repository.updateGroup(group)
            } else {
                val maxSortOrder = app.repository.getMaxGroupSortOrder()
                val group = GroupEntity(
                    id = if (isEditMode) groupId else 0,
                    name = name,
                    sortOrder = maxSortOrder + 1,
                    createTime = currentGroup?.createTime ?: System.currentTimeMillis()
                )
                if (isEditMode) {
                    app.repository.updateGroup(group)
                } else {
                    app.repository.insertGroup(group)
                }
            }

            Toast.makeText(this@GroupEditActivity, "保存成功", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun formatTime(time: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(time))
    }
}
