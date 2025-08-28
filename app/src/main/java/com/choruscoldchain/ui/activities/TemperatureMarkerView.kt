package com.choruscoldchain.ui.activities

import android.content.Context
import android.graphics.Typeface
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.choruscoldchain.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TemperatureMarkerView(
    context: Context,
    layoutResource: Int,
    private val lowerThresholdCelsius: Float = 2f,
    private val upperThresholdCelsius: Float = 8f
) : MarkerView(context, layoutResource) {

    private val tvDate: TextView = findViewById(R.id.tv_marker_date)
    private val tvTime: TextView = findViewById(R.id.tv_marker_time)
    private val tvTemperature: TextView = findViewById(R.id.tv_marker_temperature)
    private val tvStatus: TextView = findViewById(R.id.tv_marker_status)

    private val dateFormat = SimpleDateFormat("MM-dd-yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e != null) {
            val timestampMillis = e.x.toLong()
            val temperature = e.y

            tvDate.text = "Date: ${dateFormat.format(Date(timestampMillis))}"
            tvTime.text = "Time: ${timeFormat.format(Date(timestampMillis))}"
            tvTemperature.text = String.format(Locale.getDefault(), "Temperature: %.0f°C", temperature)

            val isNormal = temperature in lowerThresholdCelsius..upperThresholdCelsius
            val statusText = if (isNormal) context.getString(R.string.status_normal) else context.getString(R.string.status_alert)
            tvStatus.text = "Status: $statusText"
            tvStatus.setTextColor(context.getColor(if (isNormal) R.color.blue else R.color.error))
            tvStatus.setTypeface(tvStatus.typeface, if (isNormal) Typeface.NORMAL else Typeface.BOLD)
        }
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        // Center horizontally and place above the selected value
        return MPPointF(-(width / 2f), -height - 12f)
    }
}
