package com.zuxing.markmap

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zuxing.markmap.data.entity.PointEntity
import com.zuxing.markmap.databinding.FragmentPointEditBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PointEditFragment : Fragment() {

    private var _binding: FragmentPointEditBinding? = null
    private val binding get() = _binding!!

    private val args: PointEditFragmentArgs by navArgs()

    private lateinit var app: MarkMapApplication
    private var currentPoint: PointEntity? = null
    private var isEditMode: Boolean = false
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0
    private var currentAddress: String = ""
    private var currentAltitude: Double? = null

    private val pickLocationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                val latitude = data.getDoubleExtra("latitude", 0.0)
                val longitude = data.getDoubleExtra("longitude", 0.0)
                val address = data.getStringExtra("address") ?: ""
                val description = data.getStringExtra("description") ?: ""

                if (latitude != 0.0 && longitude != 0.0) {
                    currentLatitude = latitude
                    currentLongitude = longitude

                    binding.etLongitude.setText(String.format("%.6f", currentLongitude))
                    binding.etLatitude.setText(String.format("%.6f", currentLatitude))
                    if (address.isNotEmpty()) {
                        binding.etAddress.setText(address)
                    }
                    if (description.isNotEmpty()) {
                        binding.etDescription.setText(description)
                    }

                    updateSaveButtonState()
                    Toast.makeText(requireContext(), "位置已选择", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPointEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        app = requireActivity().application as MarkMapApplication

        isEditMode = args.pointId != -1L
        setupToolbar()
        setupViews()

        if (isEditMode) {
            loadPoint()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.title = if (isEditMode) "编辑点" else "新增点"
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        if (isEditMode) {
            binding.toolbar.inflateMenu(R.menu.menu_point_edit)
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
        binding.etLongitude.addTextChangedListener { updateSaveButtonState() }
        binding.etLatitude.addTextChangedListener { updateSaveButtonState() }

        binding.btnSelectOnMap.setOnClickListener {
            navigateToPickLocation()
        }

        binding.btnSave.setOnClickListener {
            savePoint()
        }
    }

    private fun updateSaveButtonState() {
        val longitude = binding.etLongitude.text.toString().toDoubleOrNull()
        val latitude = binding.etLatitude.text.toString().toDoubleOrNull()
        binding.btnSave.isEnabled = longitude != null && latitude != null
    }

    private fun navigateToPickLocation() {
        val longitude = binding.etLongitude.text.toString().toDoubleOrNull() ?: currentLongitude
        val latitude = binding.etLatitude.text.toString().toDoubleOrNull() ?: currentLatitude

        val intent = Intent(requireContext(), PickLocationActivity::class.java).apply {
            putExtra("latitude", latitude)
            putExtra("longitude", longitude)
            putExtra("lineId", args.lineId)
            putExtra("pointId", args.pointId)
        }
        pickLocationLauncher.launch(intent)
    }

    private fun loadPoint() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            currentPoint = app.repository.getPointById(args.pointId)
            val line = app.repository.getLineById(args.lineId)
            withContext(Dispatchers.Main) {
                currentPoint?.let { point ->
                    binding.etLongitude.setText(String.format("%.6f", point.longitude))
                    binding.etLatitude.setText(String.format("%.6f", point.latitude))
                    point.altitude?.let { binding.etAltitude.setText(String.format("%.1f", it)) }
                    binding.etAddress.setText(point.address ?: "")
                    binding.etDescription.setText(point.description ?: "")
                    binding.etSortOrder.setText(point.sortOrder.toString())
                    binding.tvLineName.text = "所属线: ${line?.name ?: ""}"
                    binding.tvLineName.visibility = View.VISIBLE
                    binding.tvCreateTime.text = "创建时间: ${formatTime(point.createTime)}"
                    binding.tvCreateTime.visibility = View.VISIBLE

                    currentLatitude = point.latitude
                    currentLongitude = point.longitude
                    currentAddress = point.address ?: ""
                    currentAltitude = point.altitude

                    updateSaveButtonState()
                }
            }
        }
    }

    private fun savePoint() {
        val longitude = binding.etLongitude.text.toString().toDoubleOrNull()
        val latitude = binding.etLatitude.text.toString().toDoubleOrNull()

        if (longitude == null || latitude == null) {
            Toast.makeText(requireContext(), "请填写经纬度", Toast.LENGTH_SHORT).show()
            return
        }

        val altitude = binding.etAltitude.text.toString().toDoubleOrNull()
        val address = binding.etAddress.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val sortOrder = binding.etSortOrder.text.toString().toIntOrNull() ?: 0

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val point = PointEntity(
                id = if (isEditMode) args.pointId else 0,
                lineId = args.lineId,
                longitude = longitude,
                latitude = latitude,
                altitude = altitude,
                address = address.ifBlank { null },
                description = description.ifBlank { null },
                sortOrder = sortOrder,
                createTime = currentPoint?.createTime ?: System.currentTimeMillis(),
                modifyTime = System.currentTimeMillis()
            )

            if (isEditMode) {
                app.repository.updatePoint(point)
            } else {
                app.repository.insertPoint(point)
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
            .setMessage("确定要删除该点吗？")
            .setPositiveButton("删除") { _, _ ->
                deletePoint()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deletePoint() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            app.repository.softDeletePoint(args.pointId)
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
