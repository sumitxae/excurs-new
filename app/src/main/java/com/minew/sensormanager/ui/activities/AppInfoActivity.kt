package com.minew.sensormanager.ui.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.minew.sensormanager.databinding.ActivityAppInfoBinding
import com.minew.sensormanager.utils.AppIdUtils
import com.minew.sensormanager.utils.AppDownloadTracker
import dagger.hilt.android.AndroidEntryPoint

/**
 * Example activity demonstrating how to display app ID information in the UI.
 * This activity shows how to use the app ID functionality for user-facing features.
 */
@AndroidEntryPoint
class AppInfoActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAppInfoBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        loadAppInfo()
    }
    
    private fun setupUI() {
        // Set up click listeners for buttons
        binding.btnRefreshInfo.setOnClickListener {
            loadAppInfo()
        }
        
        binding.btnCopyInstallationId.setOnClickListener {
            copyInstallationIdToClipboard()
        }
        
        binding.btnGenerateReport.setOnClickListener {
            generateAndDisplayReport()
        }
        
        binding.btnResetInstallation.setOnClickListener {
            showResetConfirmation()
        }
    }
    
    private fun loadAppInfo() {
        try {
            // Get basic app information
            val installationId = AppIdUtils.getInstallationId(this)
            val isFreshInstall = AppIdUtils.isFreshInstallation(this)
            val appVersion = AppIdUtils.getAppVersion(this)
            val trackingId = AppIdUtils.getTrackingId(this)
            val installationSummary = AppIdUtils.getInstallationSummary(this)
            
            // Update UI with app information
            binding.tvInstallationId.text = installationId
            binding.tvAppVersion.text = appVersion
            binding.tvTrackingId.text = trackingId
            binding.tvInstallationSummary.text = installationSummary
            binding.tvFreshInstallation.text = if (isFreshInstall) "Yes" else "No"
            
            // Get detailed information
            val tracker = AppDownloadTracker.getInstance(this)
            val firstInstallDate = tracker.getFirstInstallDate()
            binding.tvFirstInstallDate.text = firstInstallDate?.toString() ?: "Unknown"
            
            // Show success message
            Toast.makeText(this, "App information loaded successfully", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            // Handle errors gracefully
            binding.tvInstallationId.text = "Error loading"
            binding.tvAppVersion.text = "Error loading"
            binding.tvTrackingId.text = "Error loading"
            binding.tvInstallationSummary.text = "Error loading app information"
            binding.tvFreshInstallation.text = "Error"
            binding.tvFirstInstallDate.text = "Error"
            
            Toast.makeText(this, "Error loading app information: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun copyInstallationIdToClipboard() {
        try {
            val installationId = AppIdUtils.getInstallationId(this)
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Installation ID", installationId)
            clipboard.setPrimaryClip(clip)
            
            Toast.makeText(this, "Installation ID copied to clipboard", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error copying installation ID: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun generateAndDisplayReport() {
        try {
            val report = AppIdUtils.getInstallationReport(this)
            
            // Display report in a dialog or new activity
            val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Installation Report")
                .setMessage(report)
                .setPositiveButton("Copy") { _, _ ->
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Installation Report", report)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, "Report copied to clipboard", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Close", null)
                .create()
            
            dialog.show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error generating report: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showResetConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Reset Installation ID")
            .setMessage("This will generate a new installation ID as if the app was freshly installed. This action cannot be undone. Are you sure you want to continue?")
            .setPositiveButton("Reset") { _, _ ->
                resetInstallation()
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }
    
    private fun resetInstallation() {
        try {
            val tracker = AppDownloadTracker.getInstance(this)
            tracker.resetInstallation()
            
            // Reload app information after reset
            loadAppInfo()
            
            Toast.makeText(this, "Installation ID reset successfully", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error resetting installation ID: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
