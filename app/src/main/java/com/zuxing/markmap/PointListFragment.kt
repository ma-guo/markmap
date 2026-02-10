package com.zuxing.markmap

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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PointListFragment : Fragment() {

    private var _binding: FragmentPointListBinding? = null
    private val binding get() = _binding!!

    private val args: PointListFragmentArgs by navArgs()

    private lateinit var app: MarkMapApplication
    private lateinit var adapter: PointAdapter

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

        setupRecyclerView()
        loadPoints()
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
                }
            }
        }
    }

    private fun navigateToEdit(pointId: Long) {
        val action = PointListFragmentDirections.actionPointListFragmentToPointEditFragment(
            lineId = args.lineId,
            pointId = pointId
        )
        findNavController().navigate(action)
    }

    private fun navigateToMap() {
        val action = PointListFragmentDirections.actionPointListFragmentToMapFragment(
            lineId = args.lineId
        )
        findNavController().navigate(action)
    }

    private fun navigateToLineMap() {
        val action = PointListFragmentDirections.actionPointListFragmentToLineMapFragment(
            lineId = args.lineId
        )
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
