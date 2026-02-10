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
import com.zuxing.markmap.data.adapter.LineAdapter
import com.zuxing.markmap.data.entity.LineEntity
import com.zuxing.markmap.databinding.FragmentLineListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LineListFragment : Fragment() {

    private var _binding: FragmentLineListBinding? = null
    private val binding get() = _binding!!

    private val args: LineListFragmentArgs by navArgs()

    private lateinit var app: MarkMapApplication
    private lateinit var adapter: LineAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLineListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        app = requireActivity().application as MarkMapApplication

        setupRecyclerView()
        loadLines()
    }

    private fun setupRecyclerView() {
        adapter = LineAdapter(
            onItemClick = { line ->
                navigateToPointList(line.id)
            },
            onItemLongClick = { line ->
                navigateToEdit(line.id)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.fabAdd.setOnClickListener {
            navigateToEdit(null)
        }

        binding.fabMap.setOnClickListener {
            navigateToLineMap()
        }
    }

    private fun loadLines() {
        binding.progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            app.repository.getLinesByGroupId(args.groupId).collectLatest { lines ->
                binding.progressBar.visibility = View.GONE
                if (lines.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                    adapter.submitList(lines)
                }
            }
        }
    }

    private fun navigateToEdit(lineId: Long?) {
        val action = LineListFragmentDirections.actionLineListFragmentToLineEditFragment(
            groupId = args.groupId,
            lineId = lineId ?: -1L
        )
        findNavController().navigate(action)
    }

    private fun navigateToPointList(lineId: Long) {
        val action = LineListFragmentDirections.actionLineListFragmentToPointListFragment(lineId)
        findNavController().navigate(action)
    }

    private fun navigateToLineMap() {
        val action = LineListFragmentDirections.actionLineListFragmentToLineMapFragment(
            lineId = -1L,
            groupId = args.groupId
        )
        findNavController().navigate(action)
    }

    fun navigateToAdd() {
        navigateToEdit(null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}