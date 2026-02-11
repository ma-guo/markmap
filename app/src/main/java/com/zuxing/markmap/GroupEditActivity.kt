package com.zuxing.markmap

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.zuxing.markmap.data.entity.GroupEntity
import com.zuxing.markmap.databinding.FragmentGroupEditBinding
import kotlinx.coroutines.launch

class GroupEditActivity : AppCompatActivity() {

    private lateinit var binding: FragmentGroupEditBinding
    private lateinit var app: MarkMapApplication
    private var currentGroup: GroupEntity? = null
    private var isEditMode: Boolean = false
    private var groupId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentGroupEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as MarkMapApplication

        groupId = intent.getLongExtra("groupId", -1L)
        isEditMode = groupId != -1L

        setupViews()

        if (isEditMode) {
            loadGroup()
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
            Toast.makeText(this, "请输入组名", Toast.LENGTH_SHORT).show()
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
                val group = GroupEntity(
                    id = if (isEditMode) groupId else 0,
                    name = name,
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
