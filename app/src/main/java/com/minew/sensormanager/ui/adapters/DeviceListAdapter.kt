package com.minew.sensormanager.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.minew.sensormanager.R
import com.minew.sensormanager.data.models.DeviceInfo
import com.minew.sensormanager.data.models.ConnectionState
import com.minew.sensormanager.databinding.ItemDeviceBinding

class DeviceListAdapter(
    private val onDeviceClick: (DeviceInfo) -> Unit,
    private val onConnectClick: (DeviceInfo) -> Unit,
    private val onDisconnectClick: (DeviceInfo) -> Unit
) : ListAdapter<DeviceInfo, DeviceListAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    private val connectionStates = mutableMapOf<String, ConnectionState>()
    private val expandedItems = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun updateConnectionStates(states: Map<String, ConnectionState>) {
        connectionStates.clear()
        connectionStates.putAll(states)
        notifyDataSetChanged()
    }
    
    fun updateDevices(devices: List<DeviceInfo>) {
        submitList(devices)
    }

    inner class DeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: DeviceInfo) {
            binding.apply {
                textDeviceName.text = device.name
                textMacAddress.text = device.macAddress
                textRssi.text = "RSSI: ${device.rssi} dBm"
                textBattery.text = "Battery: ${device.battery}%"
                
                // Display temperature if available
                device.temperature?.let { temp ->
                    textTemperature.text = "${String.format("%.1f", temp)}°C"
                    textTemperature.visibility = android.view.View.VISIBLE
                } ?: run {
                    textTemperature.visibility = android.view.View.GONE
                }

                val connectionState = connectionStates[device.macAddress] ?: device.connectionState
                updateConnectionStatus(connectionState)

                // Handle expandable details
                val isExpanded = expandedItems.contains(device.macAddress)
                layoutDetails.visibility = if (isExpanded) android.view.View.VISIBLE else android.view.View.GONE
                
                // Display frame data if available
                device.staticFrameData?.let { data ->
                    textStaticFrameData.text = data
                    textStaticFrameData.visibility = android.view.View.VISIBLE
                } ?: run {
                    textStaticFrameData.visibility = android.view.View.GONE
                }
                
                device.combinationFrameData?.let { data ->
                    textCombinationFrameData.text = data
                    textCombinationFrameData.visibility = android.view.View.VISIBLE
                } ?: run {
                    textCombinationFrameData.visibility = android.view.View.GONE
                }

                buttonConnect.setOnClickListener {
                    if (connectionState == ConnectionState.READY) {
                        onDisconnectClick(device)
                    } else {
                        onConnectClick(device)
                    }
                }

                root.setOnClickListener {
                    toggleExpansion(device.macAddress)
                }
                
                // Add long click to open device details
                root.setOnLongClickListener {
                    onDeviceClick(device)
                    true
                }
            }
        }
        
        private fun toggleExpansion(macAddress: String) {
            if (expandedItems.contains(macAddress)) {
                expandedItems.remove(macAddress)
            } else {
                expandedItems.add(macAddress)
            }
            notifyDataSetChanged()
        }

        private fun updateConnectionStatus(state: ConnectionState) {
            binding.apply {
                when (state) {
                    ConnectionState.READY -> {
                        textConnectionStatus.text = "Connected"
                        textConnectionStatus.setTextColor(
                            root.context.getColor(R.color.status_connected)
                        )
                        buttonConnect.text = "Disconnect"
                    }
                    ConnectionState.CONNECTING -> {
                        textConnectionStatus.text = "Connecting"
                        textConnectionStatus.setTextColor(
                            root.context.getColor(R.color.status_connecting)
                        )
                        buttonConnect.text = "Connecting"
                        buttonConnect.isEnabled = false
                    }
                    ConnectionState.CONNECTED -> {
                        textConnectionStatus.text = "Connected"
                        textConnectionStatus.setTextColor(
                            root.context.getColor(R.color.status_connected)
                        )
                        buttonConnect.text = "Disconnect"
                    }
                    ConnectionState.AUTHENTICATING -> {
                        textConnectionStatus.text = "Authenticating"
                        textConnectionStatus.setTextColor(
                            root.context.getColor(R.color.status_connecting)
                        )
                        buttonConnect.text = "Authenticating"
                        buttonConnect.isEnabled = false
                    }
                    ConnectionState.AUTHENTICATED -> {
                        textConnectionStatus.text = "Authenticated"
                        textConnectionStatus.setTextColor(
                            root.context.getColor(R.color.status_connecting)
                        )
                        buttonConnect.text = "Disconnect"
                    }
                    ConnectionState.ERROR -> {
                        textConnectionStatus.text = "Error"
                        textConnectionStatus.setTextColor(
                            root.context.getColor(R.color.status_error)
                        )
                        buttonConnect.text = "Connect"
                        buttonConnect.isEnabled = true
                    }
                    else -> {
                        textConnectionStatus.text = "Disconnected"
                        textConnectionStatus.setTextColor(
                            root.context.getColor(R.color.status_disconnected)
                        )
                        buttonConnect.text = "Connect"
                        buttonConnect.isEnabled = true
                    }
                }
            }
        }
    }

    private class DeviceDiffCallback : DiffUtil.ItemCallback<DeviceInfo>() {
        override fun areItemsTheSame(oldItem: DeviceInfo, newItem: DeviceInfo): Boolean {
            return oldItem.macAddress == newItem.macAddress
        }

        override fun areContentsTheSame(oldItem: DeviceInfo, newItem: DeviceInfo): Boolean {
            return oldItem == newItem
        }
    }
}
