package com.zuxing.markmap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zuxing.markmap.data.entity.GroupEntity
import com.zuxing.markmap.databinding.FragmentGroupEditBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GroupEditFragment : Fragment() {

    private var _binding: FragmentGroupEditBinding? = null
    private val binding get() = _binding!!

    private val args: GroupEditFragmentArgs by navArgs()

    private lateinit var app: MarkMapApplication
    private var currentGroup: GroupEntity? = null
    private var isEditMode: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        app = requireActivity().application as MarkMapApplication

        isEditMode = args.groupId != -1L
        setupToolbar()
        setupViews()

        if (isEditMode) {
            loadGroup()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.title = if (isEditMode) "编辑组" else "新增组"
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        if (isEditMode) {
            binding.toolbar.inflateMenu(R.menu.menu_group_edit)
            binding.toolbar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_delete -> {
                        showDeleteConfirmDialog()
                        true
                    }

                    else -> false
                }
            }
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
        binding.btnSave.isEnabled = binding.etName.text?.isNotBlank() ?: false
    }

    private fun loadGroup() {
        CoroutineScope(Dispatchers.IO).launch {
            currentGroup = app.repository.getGroupById(args.groupId)
            withContext(Dispatchers.Main) {
                currentGroup?.let { group ->
                    binding.etName.setText(group.name)
                    binding.etSortOrder.setText(group.sortOrder.toString())
                    binding.tvCreateTime.text = "创建时间: ${formatTime(group.createTime)}"
                    binding.tvCreateTime.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun saveGroup() {
        val name = binding.etName.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(requireContext(), "请输入组名", Toast.LENGTH_SHORT).show()
            return
        }

        val sortOrder = binding.etSortOrder.text.toString().toIntOrNull() ?: 0

        CoroutineScope(Dispatchers.IO).launch {
            val group = GroupEntity(
                id = if (isEditMode) args.groupId else 0,
                name = name,
                sortOrder = sortOrder,
                createTime = currentGroup?.createTime ?: System.currentTimeMillis(),
                modifyTime = System.currentTimeMillis()
            )

            if (isEditMode) {
                app.repository.updateGroup(group)
            } else {
                app.repository.insertGroup(group)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "保存成功", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        }
    }

    private fun showDeleteConfirmDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("确认删除")
            .setMessage("确定要删除该组吗？删除后可在回收站恢复。")
            .setPositiveButton("删除") { _, _ ->
                deleteGroup()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteGroup() {
        CoroutineScope(Dispatchers.IO).launch {
            app.repository.softDeleteGroup(args.groupId)
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
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