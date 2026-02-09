package com.zuxing.markmap

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zuxing.markmap.data.entity.PointEntity
import com.zuxing.markmap.databinding.FragmentPointEditBinding
import kotlinx.coroutines.CoroutineScope
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

    private lateinit var locationClient: LocationClient

    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            getCurrentLocation()
        } else {
            Toast.makeText(requireContext(), "需要定位权限", Toast.LENGTH_SHORT).show()
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
        setupLocationClient()

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

        binding.btnGetCurrentLocation.setOnClickListener {
            requestLocationPermission()
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

    private fun setupLocationClient() {
        locationClient = LocationClient(requireContext())
        locationClient.registerLocationListener(object : BDAbstractLocationListener() {
            override fun onReceiveLocation(location: BDLocation?) {
                location?.let {
                    activity?.runOnUiThread {
                        currentLatitude = it.latitude
                        currentLongitude = it.longitude
                        currentAddress = it.addrStr ?: ""
                        currentAltitude = if (it.hasAltitude()) it.altitude else null

                        binding.etLongitude.setText(String.format("%.6f", currentLongitude))
                        binding.etLatitude.setText(String.format("%.6f", currentLatitude))
                        binding.etAddress.setText(currentAddress)
                        currentAltitude?.let { alt ->
                            binding.etAltitude.setText(String.format("%.1f", alt))
                        }
                        Toast.makeText(requireContext(), "定位成功", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })

        val option = LocationClientOption().apply {
            locationMode = LocationClientOption.LocationMode.Hight_Accuracy
            setCoorType("bd09ll")
            setScanSpan(5000)
            setOpenGps(true)
            setIsNeedAddress(true)
            setIsNeedAltitude(true)
        }
        locationClient.locOption = option
    }

    private fun requestLocationPermission() {
        if (checkPermissions()) {
            getCurrentLocation()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun checkPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getCurrentLocation() {
        if (!locationClient.isStarted) {
            locationClient.start()
        }
    }

    private fun loadPoint() {
        CoroutineScope(Dispatchers.IO).launch {
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

        CoroutineScope(Dispatchers.IO).launch {
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
        CoroutineScope(Dispatchers.IO).launch {
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
        locationClient.stop()
        _binding = null
    }
}