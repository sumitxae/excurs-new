package com.choruscoldchain.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.choruscoldchain.databinding.FragmentDeviceListBinding
import com.choruscoldchain.ui.activities.ScanActivity
import com.choruscoldchain.ui.activities.DeviceDetailsActivity
import com.choruscoldchain.ui.activities.DeviceSettingsActivity
import com.choruscoldchain.R
import com.choruscoldchain.ui.adapters.DeviceListAdapter
import com.choruscoldchain.ui.viewmodels.MainViewModel
import com.choruscoldchain.data.models.DeviceInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DeviceListFragment : Fragment() {
    
    private var _binding: FragmentDeviceListBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MainViewModel by viewModels()
    private lateinit var deviceAdapter: DeviceListAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        android.util.Log.d("DeviceListFragment", "onCreateView called")
        _binding = FragmentDeviceListBinding.inflate(inflater, container, false)
        android.util.Log.d("DeviceListFragment", "Binding inflated successfully")
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        android.util.Log.d("DeviceListFragment", "onViewCreated called")
        
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        
        // Start continuous scanning for real-time device discovery
        startContinuousScanning()
    }
    
    private fun setupRecyclerView() {
        deviceAdapter = DeviceListAdapter(
            onDeviceClick = { device ->
                openDeviceDetail(device)
            },
            onConnectClick = { device ->
                connectToDevice(device)
            },
            onDisconnectClick = { device ->
                disconnectDevice(device)
            },
            onSettingsClick = { device ->
                openDeviceSettings(device)
            }
        )
        
        binding.recyclerViewDevices.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = deviceAdapter
        }
    }
    
    private fun setupClickListeners() {
        android.util.Log.d("DeviceListFragment", "Setting up click listeners")
        
        try {
            android.util.Log.d("DeviceListFragment", "Search container: ${binding.searchContainer}")
            android.util.Log.d("DeviceListFragment", "Search edit text: ${binding.etSearch}")
            android.util.Log.d("DeviceListFragment", "Search edit text visibility: ${binding.etSearch.visibility}")
            android.util.Log.d("DeviceListFragment", "Search edit text is enabled: ${binding.etSearch.isEnabled}")
            
            // Test if we can set text to the search field
            binding.etSearch.setText("test")
            android.util.Log.d("DeviceListFragment", "Successfully set test text to search field")
            
        } catch (e: Exception) {
            android.util.Log.e("DeviceListFragment", "Error accessing search elements", e)
        }
        
        binding.fabAddDevice.setOnClickListener {
            startActivity(Intent(requireContext(), ScanActivity::class.java))
        }
        
        // Setup search functionality
        setupSearchFunctionality()
        
        // SwipeRefreshLayout will be implemented later
        // binding.swipeRefreshLayout.setOnRefreshListener {
        //     refreshDeviceList()
        // }
    }
    
    private fun setupSearchFunctionality() {
        android.util.Log.d("DeviceListFragment", "Setting up search functionality")
        
        try {
            // Setup search text watcher
            binding.etSearch.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    android.util.Log.d("DeviceListFragment", "beforeTextChanged: '$s'")
                }
                
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    android.util.Log.d("DeviceListFragment", "onTextChanged: '$s'")
                }
                
                override fun afterTextChanged(s: Editable?) {
                    val query = s?.toString() ?: ""
                    android.util.Log.d("DeviceListFragment", "afterTextChanged: '$query'")
                    viewModel.setSearchQuery(query)
                    updateClearButtonVisibility(query.isNotEmpty())
                }
            })
            
            // Setup clear button
            binding.btnClearSearch.setOnClickListener {
                android.util.Log.d("DeviceListFragment", "Clear search clicked")
                binding.etSearch.setText("")
                viewModel.clearSearch()
                updateClearButtonVisibility(false)
            }
            
            android.util.Log.d("DeviceListFragment", "Search functionality setup completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("DeviceListFragment", "Error setting up search functionality", e)
        }
    }
    
    private fun updateClearButtonVisibility(show: Boolean) {
        binding.btnClearSearch.visibility = if (show) View.VISIBLE else View.GONE
    }
    
    private fun updateEmptyStateText(isSearching: Boolean) {
        if (isSearching) {
            binding.tvEmptyTitle.text = "No devices found"
            binding.tvEmptySubtitle.text = "Try adjusting your search terms"
        } else {
            binding.tvEmptyTitle.text = getString(R.string.no_devices_found)
            binding.tvEmptySubtitle.text = getString(R.string.scan_for_devices)
        }
    }
    
    private fun observeViewModel() {
        // Observe filtered devices (search results)
        viewModel.filteredDevices.observe(viewLifecycleOwner) { devices ->
            android.util.Log.d("DeviceListFragment", "Filtered devices updated: ${devices.size} devices")
            deviceAdapter.updateDevices(devices)
            
            // Show empty state if no devices
            binding.emptyStateLayout.visibility = if (devices.isEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        
        // Observe search query to update UI elements
        viewModel.searchQuery.observe(viewLifecycleOwner) { query ->
            updateEmptyStateText(query.isNotEmpty())
            updateClearButtonVisibility(query.isNotEmpty())
        }
        
        viewModel.connectionStates.observe(viewLifecycleOwner) { states ->
            deviceAdapter.updateConnectionStates(states)
        }
    }
    
    private fun openDeviceDetail(device: DeviceInfo) {
        val intent = Intent(requireContext(), DeviceDetailsActivity::class.java)
        intent.putExtra(DeviceDetailsActivity.EXTRA_DEVICE_MAC, device.macAddress)
        startActivity(intent)
    }
    
    private fun connectToDevice(device: DeviceInfo) {
        lifecycleScope.launch {
            viewModel.connectToDevice(requireContext(), device.macAddress)
        }
    }
    
    private fun disconnectDevice(device: DeviceInfo) {
        viewModel.disconnectDevice(device.macAddress)
    }
    
    private fun openDeviceSettings(device: DeviceInfo) {
        val intent = Intent(requireContext(), DeviceSettingsActivity::class.java)
        intent.putExtra(DeviceSettingsActivity.EXTRA_DEVICE_MAC, device.macAddress)
        intent.putExtra(DeviceSettingsActivity.EXTRA_DEVICE_INFO, device)
        startActivity(intent)
    }
    
    private fun startContinuousScanning() {
        viewModel.startContinuousScanning(requireContext())
    }
    
    private fun stopContinuousScanning() {
        viewModel.stopScanning(requireContext())
    }
    
    private fun refreshDeviceList() {
        lifecycleScope.launch {
            viewModel.refreshDevices()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Resume scanning when fragment becomes visible
        startContinuousScanning()
    }
    
    override fun onPause() {
        super.onPause()
        // Stop scanning when fragment is not visible
        stopContinuousScanning()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
