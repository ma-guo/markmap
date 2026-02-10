package com.zuxing.markmap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zuxing.markmap.data.entity.GroupEntity
import com.zuxing.markmap.data.entity.LineEntity
import com.zuxing.markmap.databinding.FragmentLineEditBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LineEditFragment : Fragment() {

    private var _binding: FragmentLineEditBinding? = null
    private val binding get() = _binding!!

    private var groupId: Long = -1L
    private var lineId: Long = -1L

    private lateinit var app: MarkMapApplication
    private var currentLine: LineEntity? = null
    private var isEditMode: Boolean = false
    private var groupList: List<GroupEntity> = emptyList()
    private var groupItems: List<GroupItem> = emptyList()
    private var selectedGroupId: Long = -1L
    private var selectedGroupName: String = ""

    data class GroupItem(val id: Long, val name: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = arguments?.getLong("groupId") ?: -1L
        lineId = arguments?.getLong("lineId") ?: -1L
        isEditMode = lineId != -1L
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLineEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        app = requireActivity().application as MarkMapApplication

        setupViews()
        loadGroups()

        if (isEditMode) {
            loadLine()
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
        viewLifecycleOwner.lifecycleScope.launch {
            groupList = app.repository.getAllGroups().first()
            setupGroupDropdown()
        }
    }

    private fun setupGroupDropdown() {
        groupItems = groupList.map { GroupItem(it.id, it.name) }
        val groupNames = groupList.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, groupNames)
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
        viewLifecycleOwner.lifecycleScope.launch {
            currentLine = app.repository.getLineById(lineId)
            currentLine?.let { line ->
                binding.etName.setText(line.name)
                binding.etSortOrder.setText(line.sortOrder.toString())
                binding.tvCreateTime.text = "创建时间: ${formatTime(line.createTime)}"
                binding.tvCreateTime.visibility = View.VISIBLE

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
            Toast.makeText(requireContext(), "请输入线名", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedGroupId == -1L) {
            Toast.makeText(requireContext(), "请选择所属组", Toast.LENGTH_SHORT).show()
            return
        }

        val sortOrder = binding.etSortOrder.text.toString().toIntOrNull() ?: 0

        viewLifecycleOwner.lifecycleScope.launch {
            if (isEditMode && currentLine != null) {
                val hasChanges = name != currentLine!!.name ||
                        selectedGroupId != currentLine!!.groupId ||
                        sortOrder != currentLine!!.sortOrder

                if (!hasChanges) {
                    Toast.makeText(requireContext(), "未有任何变化", Toast.LENGTH_SHORT).show()
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

            Toast.makeText(requireContext(), "保存成功", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }

    private fun showDeleteConfirmDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("确认删除")
            .setMessage("确定要删除该线吗？删除后可在回收站恢复，线下的点也会被标记删除。")
            .setPositiveButton("删除") { _, _ ->
                deleteLine()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteLine() {
        viewLifecycleOwner.lifecycleScope.launch {
            app.repository.softDeleteLine(lineId)
            Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }

    private fun formatTime(time: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(time))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}