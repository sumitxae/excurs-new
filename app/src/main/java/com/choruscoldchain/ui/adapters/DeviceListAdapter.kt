package com.choruscoldchain.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.choruscoldchain.BuildConfig
import com.choruscoldchain.R
import com.choruscoldchain.auth.AuthManager
import com.choruscoldchain.data.models.ConnectionState
import com.choruscoldchain.data.models.DeviceInfo
import com.choruscoldchain.databinding.ItemScanDeviceBinding

class DeviceListAdapter(
        private val onDeviceClick: (DeviceInfo) -> Unit,
        private val onConnectClick: (DeviceInfo) -> Unit,
        private val onDisconnectClick: (DeviceInfo) -> Unit,
        private val onSettingsClick: (DeviceInfo) -> Unit
) : ListAdapter<DeviceInfo, DeviceListAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    private val connectionStates = mutableMapOf<String, ConnectionState>()
    private val expandedItems = mutableSetOf<String>()

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding =
                ItemScanDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getItemId(position: Int): Long {
        // Use a stable hash from MAC address to minimize view rebinding artifacts
        return getItem(position).macAddress.hashCode().toLong()
    }

    fun updateConnectionStates(states: Map<String, ConnectionState>) {
        connectionStates.clear()
        connectionStates.putAll(states)
        notifyDataSetChanged()
    }

    fun updateDevices(devices: List<DeviceInfo>) {
        submitList(devices)
    }

    inner class DeviceViewHolder(private val binding: ItemScanDeviceBinding) :
            RecyclerView.ViewHolder(binding.root) {

        fun bind(device: DeviceInfo) {
            binding.apply {
                tvScanDeviceName.text = device.macAddress
                tvSensorType.text = "Temperature Sensor"
                tvScanDeviceHt.text =
                        device.temperature?.let { t -> "${String.format("%.1f", t)}°C" } ?: "--"

                val connectionState = connectionStates[device.macAddress] ?: device.connectionState
                updateConnectionStatus(connectionState)
                
                // Set background color based on alert state
                val isInAlertState = device.temperature != null && !device.temperature.isNaN() && 
                    (device.temperature > 8.0f || device.temperature < 2.0f)
                
                if (isInAlertState) {
                    // Subtle red background for alert devices
                    root.setCardBackgroundColor(itemView.context.getColor(R.color.error_background))
                    // Increase elevation for alert devices to make them more prominent
                    root.cardElevation = 8f
                } else {
                    // Normal white background
                    root.setCardBackgroundColor(itemView.context.getColor(R.color.card_background))
                    // Normal elevation
                    root.cardElevation = 3f
                }

                // Show settings button only for admin users
                val authManager = AuthManager.getInstance(itemView.context)
                val isAdminUser = authManager.isLoggedIn() && authManager.getUserRole() == "admin"
                
                if (isAdminUser) {
                    btnDeviceSettings.visibility = View.VISIBLE
                    btnDeviceSettings.setOnClickListener {
                        onSettingsClick(device)
                    }
                } else {
                    btnDeviceSettings.visibility = View.GONE
                }

                // Show Connect button only when device is in excursion state (2°C <= temp <= 8°C)
                val isInExcursionState = device.temperature != null && !device.temperature.isNaN() && 
                    (device.temperature > 8.0f || device.temperature < 2.0f)
                
                if (isInExcursionState) {
                    btnConnect.visibility = View.VISIBLE
                    btnConnect.text = "Connect"
                    btnConnect.setOnClickListener {
                        onConnectClick(device)
                    }
                } else {
                    btnConnect.visibility = View.GONE
                }

                // Tap card to toggle frame info expansion
                root.setOnClickListener { toggleExpansion(device.macAddress, adapterPosition) }

                // Frame display controlled by BuildConfig flag
                val frameView = root.findViewById<TextView>(R.id.tv_frame_info)
                if (frameView != null) {
                    val isExpanded = expandedItems.contains(device.macAddress)
                    if (BuildConfig.DEV_MODE && isExpanded) {
                        frameView.visibility = View.VISIBLE
                        val parts = mutableListOf<String>()
                        device.staticFrameData?.let { parts.add(it) }
                        device.combinationFrameData?.let { parts.add(it) }
                        frameView.text = parts.joinToString("\n\n")
                    } else {
                        frameView.visibility = View.GONE
                        frameView.text = ""
                    }
                }
            }
        }

        private fun toggleExpansion(macAddress: String, position: Int) {
            if (expandedItems.contains(macAddress)) {
                expandedItems.remove(macAddress)
            } else {
                expandedItems.add(macAddress)
            }
            if (position != RecyclerView.NO_POSITION) {
                notifyItemChanged(position)
            } else {
                notifyDataSetChanged()
            }
        }

        private fun updateConnectionStatus(state: ConnectionState) {
            // Note: state parameter is kept for future use but currently using temperature-based status
            // First check temperature-based status
            val tempValue = getItem(adapterPosition).temperature
            if (tempValue != null && !tempValue.isNaN()) {
                if (tempValue > 8.0f || tempValue < 2.0f) {
                    binding.tvStatusBadge.text = "ALERT"
                    binding.tvStatusBadge.setTextColor(itemView.context.getColor(R.color.error))
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_badge_alert)
                } else if (tempValue <= 8.0f && tempValue >= 2.0f) {
                    binding.tvStatusBadge.text = "NORMAL"
                    binding.tvStatusBadge.setTextColor(itemView.context.getColor(R.color.success))
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_badge_normal)
                }
            } else {
                binding.tvStatusBadge.text = "--"
                binding.tvStatusBadge.setTextColor(itemView.context.getColor(R.color.primary_hover))
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_badge_alert)
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
