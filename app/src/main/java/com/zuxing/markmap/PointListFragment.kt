package com.zuxing.markmap

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.zuxing.markmap.data.adapter.PointAdapter
import com.zuxing.markmap.databinding.FragmentPointListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PointListFragment : Fragment() {

    private var _binding: FragmentPointListBinding? = null
    private val binding get() = _binding!!

    private val args: PointListFragmentArgs by navArgs()

    private lateinit var app: MarkMapApplication
    private lateinit var adapter: PointAdapter
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPointListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        app = requireActivity().application as MarkMapApplication
        prefs = requireActivity().getSharedPreferences("scroll_positions", 0)

        setupRecyclerView()
        loadPoints()
    }

    override fun onPause() {
        super.onPause()
        saveScrollPosition()
    }

    private fun saveScrollPosition() {
        if (_binding != null) {
            val position = (binding.recyclerView.layoutManager as? LinearLayoutManager)
                ?.findFirstCompletelyVisibleItemPosition() ?: 0
            prefs.edit().putInt("point_list_${args.lineId}", position).apply()
        }
    }

    private fun getSavedScrollPosition(): Int {
        return prefs.getInt("point_list_${args.lineId}", 0)
    }

    private fun setupRecyclerView() {
        adapter = PointAdapter { point ->
            navigateToEdit(point.id)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.fabAdd.setOnClickListener {
            navigateToMap()
        }

        binding.fabMap.setOnClickListener {
            navigateToLineMap()
        }
    }

    private fun loadPoints() {
        binding.progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            app.repository.getPointsByLineId(args.lineId).collectLatest { points ->
                binding.progressBar.visibility = View.GONE
                if (points.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                    adapter.submitList(points)
                    val savedPosition = getSavedScrollPosition()
                    if (savedPosition > 0 && savedPosition < points.size) {
                        binding.recyclerView.post {
                            binding.recyclerView.scrollToPosition(savedPosition)
                        }
                    }
                }
            }
        }
    }

    private fun navigateToEdit(pointId: Long) {
        val intent = android.content.Intent(requireContext(), PointEditActivity::class.java).apply {
            putExtra("lineId", args.lineId)
            putExtra("pointId", pointId)
        }
        startActivity(intent)
    }

    private fun navigateToMap() {
        val action = PointListFragmentDirections.actionPointListFragmentToMapFragment(
            lineId = args.lineId
        )
        findNavController().navigate(action)
    }

    private fun navigateToLineMap() {
        viewLifecycleOwner.lifecycleScope.launch {
            val line = withContext(Dispatchers.IO) {
                app.repository.getLineById(args.lineId)
            }
            val groupId = line?.groupId ?: -1L
            val intent = android.content.Intent(requireContext(), LineMapActivity::class.java).apply {
                putExtra("lineId", args.lineId)
                putExtra("groupId", groupId)
            }
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
