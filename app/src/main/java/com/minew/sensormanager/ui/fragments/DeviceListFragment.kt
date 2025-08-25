package com.minew.sensormanager.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.minew.sensormanager.databinding.FragmentDeviceListBinding
import com.minew.sensormanager.ui.activities.ScanActivity
import com.minew.sensormanager.ui.activities.DeviceDetailsActivity
import com.minew.sensormanager.ui.adapters.DeviceListAdapter
import com.minew.sensormanager.ui.viewmodels.MainViewModel
import com.minew.sensormanager.data.models.DeviceInfo
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
        _binding = FragmentDeviceListBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
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
            }
        )
        
        binding.recyclerViewDevices.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = deviceAdapter
        }
    }
    
    private fun setupClickListeners() {
        binding.fabAddDevice.setOnClickListener {
            startActivity(Intent(requireContext(), ScanActivity::class.java))
        }
        
        // SwipeRefreshLayout will be implemented later
        // binding.swipeRefreshLayout.setOnRefreshListener {
        //     refreshDeviceList()
        // }
    }
    
    private fun observeViewModel() {
        // Observe real-time discovered devices
        viewModel.discoveredDevices.observe(viewLifecycleOwner) { devices ->
            deviceAdapter.updateDevices(devices)
            
            // Show empty state if no devices
            binding.emptyStateLayout.visibility = if (devices.isEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
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
