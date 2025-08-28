package com.minew.sensormanager.ui.activities

import android.Manifest
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.minew.sensormanager.R
import com.minew.sensormanager.data.models.ConnectionState
import com.minew.sensormanager.databinding.ActivityDeviceDetailsBinding
import com.minew.sensormanager.permissions.PermissionCoordinator
import com.minew.sensormanager.permissions.PermissionType
import com.minew.sensormanager.ui.viewmodels.DeviceDetailsViewModel
import com.minew.sensormanager.utils.AppIdUtils
import com.minew.sensormanager.utils.CsvGenerator
import com.permissionx.guolindev.PermissionX
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import android.widget.Toast

@AndroidEntryPoint
class DeviceDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceDetailsBinding
    private val viewModel: DeviceDetailsViewModel by viewModels()
    private var latestConnectionState: ConnectionState = ConnectionState.DISCONNECTED
    private var isAnalyzingTemperatureFlag: Boolean = false
    private var isAnalysisCompleted: Boolean = false
    private lateinit var appIdTextView: android.widget.TextView
    @Inject lateinit var permissionCoordinator: PermissionCoordinator

    companion object {
        const val EXTRA_DEVICE_MAC = "device_mac"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize permission coordinator for requesting non-critical permissions like Storage
        permissionCoordinator.initialize(this, this)

        // Force dark status bar icons on light background across APIs
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        setupUI()
        observeViewModel()

        // Handle system back press to disconnect the device before leaving
        onBackPressedDispatcher.addCallback(
                this,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        viewModel.disconnectDevice()
                        // Finish this activity and go back
                        finish()
                    }
                }
        )

        // Get device MAC from intent
        val deviceMac = intent.getStringExtra(EXTRA_DEVICE_MAC)
        if (deviceMac != null) {
            viewModel.loadDeviceDetails(this, deviceMac)
        } else {
            showError("No device MAC provided")
            finish()
        }
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            // Ensure device is disconnected when navigating back to scan screen
            viewModel.disconnectDevice()
            onBackPressedDispatcher.onBackPressed()
        }

        // Add long press to connection status for diagnostics
        binding.tvConnectionStatus.setOnLongClickListener {
            val diagnostics = viewModel.getConnectionDiagnostics(this)
            showDiagnosticsDialog(diagnostics)
            true
        }

        // Setup the temperature graph
        setupTemperatureGraph()

        // Setup app ID footer
        val footerLayout = findViewById<android.view.View>(R.id.app_id_footer)
        if (footerLayout != null) {
            appIdTextView = footerLayout.findViewById(R.id.tv_scan_app_id)
            if (appIdTextView != null) {
                android.util.Log.d("DeviceDetailsActivity", "App ID TextView found successfully")
                updateAppIdFooter()
            } else {
                android.util.Log.e(
                        "DeviceDetailsActivity",
                        "Could not find tv_scan_app_id TextView"
                )
            }
        } else {
            android.util.Log.e("DeviceDetailsActivity", "Could not find scan_app_id_footer layout")
        }
    }

    private fun setupTemperatureGraph() {
        val chart = binding.graphContainer

        // Clear any existing data
        chart.clear()

        // Configure chart appearance
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.setDrawGridBackground(false)
        chart.setAutoScaleMinMaxEnabled(false)
        chart.setViewPortOffsets(50f, 20f, 50f, 50f)

        // Enable hardware acceleration for better rendering
        chart.setHardwareAccelerationEnabled(true)

        // Configure X-axis
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(true)
        xAxis.granularity = 0f
        xAxis.textColor = getColor(R.color.text_primary)
        xAxis.setDrawAxisLine(true)
        xAxis.axisLineWidth = 1f
        xAxis.valueFormatter =
                object : ValueFormatter() {
                    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    override fun getFormattedValue(value: Float): String {
                        return dateFormat.format(Date(value.toLong()))
                    }
                }

        // Configure Y-axis (left)
        val leftAxis = chart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.granularity = 0f
        leftAxis.setDrawZeroLine(false)
        leftAxis.textColor = getColor(R.color.text_primary)
        leftAxis.setDrawAxisLine(true)
        leftAxis.axisLineWidth = 1f
        leftAxis.setDrawTopYLabelEntry(false)

        // Configure Y-axis (right)
        val rightAxis = chart.axisRight
        rightAxis.isEnabled = false

        // Disable animations initially for better performance
        chart.animateX(0)
        chart.animateY(0)

        // Attach marker for value selection
        val marker = TemperatureMarkerView(this, R.layout.layout_chart_marker, 2f, 8f)
        chart.marker = marker

        // Legend disabled above; no legend styling needed
    }

    private fun updateLoadingOverlay() {
        val shouldShow =
                (latestConnectionState == ConnectionState.CONNECTING ||
                        latestConnectionState == ConnectionState.CONNECTED ||
                        latestConnectionState == ConnectionState.AUTHENTICATED) ||
                        !isAnalysisCompleted ||
                        isAnalyzingTemperatureFlag
        binding.loadingOverlay.visibility = if (shouldShow) View.VISIBLE else View.GONE
    }

    private fun plotTemperatureGraph(
            analysis: com.minew.sensormanager.data.models.TemperatureHistoryAnalysis
    ) {
        val chart = binding.graphContainer
        val dataPoints = analysis.excursionAnalysis.excursionDataPoints

        if (dataPoints.isEmpty()) {
            chart.visibility = View.GONE
            binding.tvGraphPlaceholder.visibility = View.VISIBLE
            binding.tvTemperatureHistory.visibility = View.VISIBLE
            return
        }

        // Show axis titles
        binding.tvYAxisTitle.visibility = View.VISIBLE
        binding.tvXAxisTitle.visibility = View.VISIBLE

        // Build temperature entries
        val temperatureEntries =
                dataPoints.map { point ->
                    Entry(point.timestamps.toFloat(), point.temperature.toFloat())
                }

        // Clear chart before adding new data
        chart.clear()

        // Dynamic Y-axis based on data and thresholds
        var minTemp = Float.MAX_VALUE
        var maxTemp = Float.MIN_VALUE
        temperatureEntries.forEach { entry ->
            minTemp = kotlin.math.min(minTemp, entry.y)
            maxTemp = kotlin.math.max(maxTemp, entry.y)
        }
        var yMin = kotlin.math.min(minTemp, 2.0f)
        var yMax = kotlin.math.max(maxTemp, 8.0f)
        val padding = kotlin.math.max(0.5f, (yMax - yMin) * 0.1f)
        yMin -= padding
        yMax += padding
        val leftAxis = chart.axisLeft
        leftAxis.axisMinimum = yMin
        leftAxis.axisMaximum = yMax
        leftAxis.granularity = 2f // 2°C intervals as specified

        // Configure X-axis range
        val xAxis = chart.xAxis
        val firstTimestamp = temperatureEntries.first().x
        val lastTimestamp = temperatureEntries.last().x

        // Calculate time difference between consecutive points to extend X-axis one point before
        val timeDiff =
                if (temperatureEntries.size > 1) {
                    temperatureEntries[1].x - temperatureEntries[0].x
                } else {
                    // If only one point, use a reasonable default time interval (1 hour)
                    3600000f // 1 hour in milliseconds
                }

        xAxis.axisMinimum = firstTimestamp - timeDiff
        xAxis.axisMaximum = lastTimestamp
        xAxis.granularity = 1f

        val dataSets = mutableListOf<LineDataSet>()

        // Define color constants based on your specifications
        val normalBlue = getColor(R.color.blue)
        val excursionRed = getColor(R.color.error)
        val primaryOrange = getColor(R.color.primary)

        if (temperatureEntries.size >= 2) {
            // Analyze temperature data to determine segmentation
            val allNormal = temperatureEntries.all { it.y in 2.0f..8.0f }
            val allExcursion = temperatureEntries.all { it.y < 2.0f || it.y > 8.0f }

            when {
                allNormal -> {
                    // Scenario 1: All Normal Temperatures
                    val normalDataSet =
                            LineDataSet(temperatureEntries, "Normal Temperature").apply {
                                color = normalBlue
                                lineWidth = 2f
                                mode = LineDataSet.Mode.LINEAR
                                setDrawValues(false)
                                setDrawCircles(false)
                                setDrawFilled(false)
                                setDrawHighlightIndicators(true)
                            }
                    dataSets.add(normalDataSet)
                }
                allExcursion -> {
                    // Scenario 3: All Excursion Temperatures
                    val excursionDataSet =
                            LineDataSet(temperatureEntries, "Excursion Temperature").apply {
                                color = excursionRed
                                lineWidth = 2f
                                mode = LineDataSet.Mode.LINEAR
                                setDrawValues(false)
                                setDrawCircles(false)
                                setDrawFilled(false)
                                setDrawHighlightIndicators(true)
                            }
                    dataSets.add(excursionDataSet)
                }
                else -> {
                    // Scenario 2: Mixed Normal and Excursion
                    // Find first excursion point
                    var firstExcursionIndex = -1
                    for (i in temperatureEntries.indices) {
                        val temp = temperatureEntries[i].y
                        if (temp < 2.0f || temp > 8.0f) {
                            firstExcursionIndex = i
                            break
                        }
                    }

                    if (firstExcursionIndex != -1 && firstExcursionIndex > 0) {
                        // Create normal segment (before first excursion)
                        val normalSegment = temperatureEntries.subList(0, firstExcursionIndex + 1)
                        val normalDataSet =
                                LineDataSet(normalSegment, "Normal Temperature").apply {
                                    color = normalBlue
                                    lineWidth = 2f
                                    mode = LineDataSet.Mode.LINEAR
                                    setDrawValues(false)
                                    setDrawCircles(false)
                                    setDrawFilled(false)
                                    setDrawHighlightIndicators(true)
                                }
                        dataSets.add(normalDataSet)

                        // Create excursion segment (from first excursion onwards)
                        val excursionSegment =
                                temperatureEntries.subList(
                                        firstExcursionIndex,
                                        temperatureEntries.size
                                )
                        val excursionDataSet =
                                LineDataSet(excursionSegment, "Excursion").apply {
                                    color = excursionRed
                                    lineWidth = 2f
                                    mode = LineDataSet.Mode.LINEAR
                                    setDrawValues(false)
                                    setDrawCircles(false)
                                    setDrawFilled(false)
                                    setDrawHighlightIndicators(true)
                                }
                        dataSets.add(excursionDataSet)
                    } else {
                        // Fallback: single line with primary color
                        val fallbackDataSet =
                                LineDataSet(temperatureEntries, "Temperature").apply {
                                    color = primaryOrange
                                    lineWidth = 2f
                                    mode = LineDataSet.Mode.LINEAR
                                    setDrawValues(false)
                                    setDrawCircles(false)
                                    setDrawFilled(false)
                                    setDrawHighlightIndicators(true)
                                }
                        dataSets.add(fallbackDataSet)
                    }
                }
            }
        } else {
            // Single point: use primary color
            val singleDataSet =
                    LineDataSet(temperatureEntries, "Temperature").apply {
                        color = primaryOrange
                        lineWidth = 2f
                        mode = LineDataSet.Mode.LINEAR
                        setDrawValues(false)
                        setDrawCircles(true)
                        circleRadius = 6f
                        setDrawFilled(false)
                        setDrawHighlightIndicators(true)
                        setCircleColor(primaryOrange)
                    }
            dataSets.add(singleDataSet)
        }

        // Add circles only for: (1) first normal point, (2) first excursion after a normal, (3)
        // last point
        var firstNormalIndex = -1
        for (i in temperatureEntries.indices) {
            if (temperatureEntries[i].y in 2.0f..8.0f) {
                firstNormalIndex = i
                break
            }
        }

        var firstExcursionAfterNormalIndex = -1
        if (firstNormalIndex != -1) {
            for (i in (firstNormalIndex + 1) until temperatureEntries.size) {
                val y = temperatureEntries[i].y
                if (y < 2.0f || y > 8.0f) {
                    firstExcursionAfterNormalIndex = i
                    break
                }
            }
        }

        val lastIndex = temperatureEntries.size - 1
        val indicesToMark = linkedSetOf<Int>()
        if (firstNormalIndex != -1) indicesToMark.add(firstNormalIndex)
        if (firstExcursionAfterNormalIndex != -1) indicesToMark.add(firstExcursionAfterNormalIndex)
        if (lastIndex >= 0) indicesToMark.add(lastIndex)

        val circleEntries = indicesToMark.map { temperatureEntries[it] }
        val circleColors =
                indicesToMark.map { idx ->
                    val isNormal = temperatureEntries[idx].y in 2.0f..8.0f
                    if (isNormal) normalBlue else excursionRed
                }

        if (circleEntries.isNotEmpty()) {
            val circleDataSet =
                    LineDataSet(circleEntries, "Data Points").apply {
                        color = Color.TRANSPARENT
                        lineWidth = 0f
                        mode = LineDataSet.Mode.LINEAR
                        setDrawValues(false)
                        setDrawCircles(true)
                        circleRadius = 6f
                        setDrawCircleHole(false)
                        setDrawFilled(false)
                        setDrawHighlightIndicators(false)
                        // Apply per-entry colors by aligning list size with entries
                        setCircleColors(circleColors)
                    }
            dataSets.add(circleDataSet)
        }

        // Add threshold lines with correct colors
        val minTime = temperatureEntries.first().x
        val maxTime = temperatureEntries.last().x

        // High threshold line (8°C) - RED dashed line
        val upperAlertEntries = listOf(Entry(minTime, 8f), Entry(maxTime, 8f))
        val upperAlertDataSet =
                LineDataSet(upperAlertEntries, "High Threshold (8°C)").apply {
                    color = excursionRed // RED for high threshold
                    lineWidth = 2f
                    setDrawCircles(false)
                    setDrawValues(false)
                    mode = LineDataSet.Mode.LINEAR
                    enableDashedLine(10f, 5f, 0f)
                    setDrawFilled(false)
                    setDrawHighlightIndicators(false)
                }

        // Low threshold line (2°C) - BLUE dashed line
        val lowerAlertEntries = listOf(Entry(minTime, 2f), Entry(maxTime, 2f))
        val lowerAlertDataSet =
                LineDataSet(lowerAlertEntries, "Low Threshold (2°C)").apply {
                    color = normalBlue // BLUE for low threshold
                    lineWidth = 2f
                    setDrawCircles(false)
                    setDrawValues(false)
                    mode = LineDataSet.Mode.LINEAR
                    enableDashedLine(10f, 5f, 0f)
                    setDrawFilled(false)
                    setDrawHighlightIndicators(false)
                }

        // Compose LineData with all datasets
        val lineData =
                LineData().apply {
                    dataSets.forEach { addDataSet(it) }
                    addDataSet(upperAlertDataSet)
                    addDataSet(lowerAlertDataSet)
                }
        chart.data = lineData

        // Set label count based on data points for better readability
        val optimalLabelCount = kotlin.math.min(8, kotlin.math.max(3, temperatureEntries.size))
        xAxis.setLabelCount(optimalLabelCount, false)

        // Finalize visibility
        chart.visibility = View.VISIBLE
        binding.tvGraphPlaceholder.visibility = View.GONE
        binding.tvTemperatureHistory.visibility = View.GONE

        // Force chart refresh
        chart.invalidate()
        chart.notifyDataSetChanged()
    }

    private fun observeViewModel() {
        viewModel.deviceInfo.observe(this) { deviceInfo ->
            deviceInfo?.let {
                binding.tvDeviceMac.text = it.macAddress
                binding.tvBatteryLevel.text = "${it.battery}%"
                binding.tvFirmwareVersion.text = it.firmwareVersion
                binding.tvCurrentTemperature.text =
                        it.temperature?.let { temp -> "${String.format("%.1f", temp)}°C" } ?: "N/A"

                // Set excursion event time if available
                binding.tvExcursionEventAt.text =
                        "Processing..." // Will be updated by excursionEventTime observer

                // Update connection status
                updateConnectionStatus(it.connectionState)
            }
        }

        viewModel.connectionState.observe(this) { connectionState ->
            latestConnectionState = connectionState
            updateConnectionStatus(connectionState)
            updateLoadingOverlay()
        }

        viewModel.excursionEventTime.observe(this) { excursionTime ->
            binding.tvExcursionEventAt.text = excursionTime
        }

        viewModel.isAnalyzingTemperature.observe(this) { isAnalyzing ->
            isAnalyzingTemperatureFlag = isAnalyzing
            if (isAnalyzing) {
                binding.tvExcursionEventAt.text = "Processing..."
                isAnalysisCompleted = false
            }
            updateLoadingOverlay()
        }

        viewModel.excursionDuration.observe(this) { duration ->
            binding.tvExcursionDuration.text = duration
        }

        viewModel.temperatureHistoryAnalysis.observe(this) { analysis ->
            // Plot the temperature graph
            plotTemperatureGraph(analysis)

            // Generate CSV file with historical data
            generateCsvFromHistoricalData(analysis)

            binding.tvTemperatureHistory.visibility = View.VISIBLE
            binding.tvGraphPlaceholder.visibility = View.GONE
            isAnalysisCompleted = true
            updateLoadingOverlay()
        }

        viewModel.isLoading.observe(this) { _ ->
            // Graph loader is driven by connection + analysis states
            updateLoadingOverlay()
        }

        viewModel.errorMessage.observe(this) { errorMessage ->
            errorMessage?.let {
                showError(it)
                viewModel.clearError()
            }
        }
    }

    private fun updateConnectionStatus(connectionState: ConnectionState) {
        val statusText =
                when (connectionState) {
                    ConnectionState.CONNECTING -> "Connecting..."
                    ConnectionState.CONNECTED -> "Connected"
                    ConnectionState.AUTHENTICATED -> "Authenticated"
                    ConnectionState.READY -> "Ready"
                    ConnectionState.DISCONNECTED -> "Disconnected"
                    ConnectionState.ERROR -> "Connection Error"
                    else -> "Unknown"
                }

        binding.tvConnectionStatus.text = statusText

        // No buttons to show/hide anymore

        // Loader visibility is handled centrally
        updateLoadingOverlay()
    }

    private fun showError(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }

    private fun showDiagnosticsDialog(diagnostics: String) {
        val dialog =
                androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Connection Diagnostics")
                        .setMessage(diagnostics)
                        .setPositiveButton("Copy to Clipboard") { _, _ ->
                            val clipboard =
                                    getSystemService(Context.CLIPBOARD_SERVICE) as
                                            android.content.ClipboardManager
                            val clip =
                                    android.content.ClipData.newPlainText(
                                            "Connection Diagnostics",
                                            diagnostics
                                    )
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(
                                            this,
                                            "Diagnostics copied to clipboard",
                                            android.widget.Toast.LENGTH_SHORT
                                    )
                                    .show()
                        }
                        .setNegativeButton("Close", null)
                        .create()
        dialog.show()
    }

    /** Updates the app ID footer with the actual installation ID. */
    private fun updateAppIdFooter() {
        try {
            val installationId = AppIdUtils.getInstallationId(this)
            appIdTextView.text = "App ID: $installationId"
        } catch (e: Exception) {
            android.util.Log.e("DeviceDetailsActivity", "Error updating app ID footer", e)
            appIdTextView.text = "App ID: Error"
        }
    }

    /** Generates CSV file from temperature historical data */
    private fun generateCsvFromHistoricalData(
            analysis: com.minew.sensormanager.data.models.TemperatureHistoryAnalysis
    ) {
        // If storage permission not granted, request it and proceed on success
        if (!hasStoragePermission()) {
            requestStoragePermission { performCsvGeneration(analysis) }
            return
        }
        performCsvGeneration(analysis)
    }

    private fun hasStoragePermission(): Boolean {
        val state = permissionCoordinator.getCurrentPermissionState()
        return state.grantedPermissions.contains(PermissionType.STORAGE)
    }

    private fun requestStoragePermission(onGranted: () -> Unit) {
        val storagePermissions =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(
                            Manifest.permission.READ_MEDIA_AUDIO,
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO
                    )
                } else {
                    arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                }
        PermissionX.init(this)
                .permissions(storagePermissions.asList())
                .explainReasonBeforeRequest()
                .request { allGranted, _, _ ->
                    if (allGranted) {
                        onGranted()
                    } else {
                        android.widget.Toast.makeText(
                                        this,
                                        "Storage permission required to save CSV",
                                        android.widget.Toast.LENGTH_SHORT
                                )
                                .show()
                    }
                }
    }

    private fun performCsvGeneration(
            analysis: com.minew.sensormanager.data.models.TemperatureHistoryAnalysis
    ) {
        try {
            android.util.Log.d(
                    "DeviceDetailsActivity",
                    "Starting CSV generation with ${analysis.allDataPoints.size} data points"
            )

            val deviceMac = intent.getStringExtra(EXTRA_DEVICE_MAC)
            if (deviceMac != null && analysis.allDataPoints.isNotEmpty()) {
                android.util.Log.d("DeviceDetailsActivity", "Device MAC: $deviceMac")

                val csvUri =
                        CsvGenerator.generateTemperatureCsv(this, deviceMac, analysis.allDataPoints)
                csvUri?.let {
                    android.util.Log.d(
                            "DeviceDetailsActivity",
                            "CSV file generated successfully: $it"
                    )
                    Toast.makeText(this, "CSV file saved to Downloads", Toast.LENGTH_LONG).show()
                }
                        ?: run {
                            android.util.Log.e(
                                    "DeviceDetailsActivity",
                                    "Failed to generate CSV file"
                            )
                            Toast.makeText(
                                            this,
                                            "Failed to generate CSV file. Check storage permissions.",
                                            Toast.LENGTH_LONG
                                    )
                                    .show()
                        }
            } else {
                android.util.Log.w(
                        "DeviceDetailsActivity",
                        "No device MAC or data points available for CSV generation"
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("DeviceDetailsActivity", "Error generating CSV file", e)
            android.widget.Toast.makeText(
                            this,
                            "Error generating CSV file: ${e.message}",
                            android.widget.Toast.LENGTH_LONG
                    )
                    .show()
        }
    }

    // ensureStoragePermission removed; use requestStoragePermission instead

}
