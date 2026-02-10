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
import com.zuxing.markmap.data.entity.LineEntity
import com.zuxing.markmap.databinding.FragmentLineEditBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LineEditFragment : Fragment() {

    private var _binding: FragmentLineEditBinding? = null
    private val binding get() = _binding!!

    private val args: LineEditFragmentArgs by navArgs()

    private lateinit var app: MarkMapApplication
    private var currentLine: LineEntity? = null
    private var isEditMode: Boolean = false

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

        isEditMode = args.lineId != -1L
        setupToolbar()
        setupViews()

        if (isEditMode) {
            loadLine()
        } else {
            loadGroupName()
        }
    }

    private fun setupToolbar() {
//        binding.toolbar.title = if (isEditMode) "编辑线" else "新增线"
//        binding.toolbar.setNavigationOnClickListener {
//            findNavController().navigateUp()
//        }
//
//        if (isEditMode) {
//            binding.toolbar.inflateMenu(R.menu.menu_line_edit)
//            binding.toolbar.setOnMenuItemClickListener { menuItem ->
//                when (menuItem.itemId) {
//                    R.id.action_delete -> {
//                        showDeleteConfirmDialog()
//                        true
//                    }
//                    else -> false
//                }
//            }
//        }
    }

    private fun setupViews() {
        binding.etName.addTextChangedListener {
            updateSaveButtonState()
        }

        binding.btnSave.setOnClickListener {
            saveLine()
        }
    }

    private fun updateSaveButtonState() {
        binding.btnSave.isEnabled = binding.etName.text!!.isNotBlank()
    }

    private fun loadGroupName() {
        CoroutineScope(Dispatchers.IO).launch {
            val group = app.repository.getGroupById(args.groupId)
            withContext(Dispatchers.Main) {
                group?.let {
                    binding.tvGroupName.text = "所属组: ${it.name}"
                    binding.tvGroupName.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun loadLine() {
        CoroutineScope(Dispatchers.IO).launch {
            currentLine = app.repository.getLineById(args.lineId)
            val group = app.repository.getGroupById(args.groupId)
            withContext(Dispatchers.Main) {
                currentLine?.let { line ->
                    binding.etName.setText(line.name)
                    binding.etSortOrder.setText(line.sortOrder.toString())
                    binding.tvGroupName.text = "所属组: ${group?.name ?: ""}"
                    binding.tvGroupName.visibility = View.VISIBLE
                    binding.tvCreateTime.text = "创建时间: ${formatTime(line.createTime)}"
                    binding.tvCreateTime.visibility = View.VISIBLE
                } ?: run {
                    loadGroupName()
                }
            }
        }
    }

    private fun saveLine() {
        val name = binding.etName.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(requireContext(), "请输入线名", Toast.LENGTH_SHORT).show()
            return
        }

        val sortOrder = binding.etSortOrder.text.toString().toIntOrNull() ?: 0

        CoroutineScope(Dispatchers.IO).launch {
            val line = LineEntity(
                id = if (isEditMode) args.lineId else 0,
                name = name,
                groupId = args.groupId,
                sortOrder = sortOrder,
                createTime = currentLine?.createTime ?: System.currentTimeMillis()
            )

            if (isEditMode) {
                app.repository.updateLine(line)
            } else {
                app.repository.insertLine(line)
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
            .setMessage("确定要删除该线吗？删除后可在回收站恢复，线下的点也会被标记删除。")
            .setPositiveButton("删除") { _, _ ->
                deleteLine()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteLine() {
        CoroutineScope(Dispatchers.IO).launch {
            app.repository.softDeleteLine(args.lineId)
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