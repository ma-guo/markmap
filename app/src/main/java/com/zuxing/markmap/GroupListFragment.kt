package com.zuxing.markmap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.zuxing.markmap.data.adapter.GroupAdapter
import com.zuxing.markmap.data.entity.GroupEntity
import com.zuxing.markmap.databinding.FragmentGroupListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GroupListFragment : Fragment() {

    private var _binding: FragmentGroupListBinding? = null
    private val binding get() = _binding!!

    private lateinit var app: MarkMapApplication
    private lateinit var adapter: GroupAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        app = requireActivity().application as MarkMapApplication

        setupRecyclerView()
        loadGroups()
    }

    private fun setupRecyclerView() {
        adapter = GroupAdapter { group ->
            navigateToLineList(group.id)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.fabAdd.setOnClickListener {
            navigateToEdit(null)
        }
    }

    private fun loadGroups() {
        binding.progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            app.repository.getAllGroups().collectLatest { groups ->
                binding.progressBar.visibility = View.GONE
                if (groups.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                    adapter.submitList(groups)
                }
            }
        }
    }

    private fun navigateToEdit(groupId: Long? = null) {
        val action = GroupListFragmentDirections.actionGroupListFragmentToGroupEditFragment(groupId ?: -1L)
        findNavController().navigate(action)
    }

    private fun navigateToLineList(groupId: Long) {
        val action = GroupListFragmentDirections.actionGroupListFragmentToLineListFragment(groupId)
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