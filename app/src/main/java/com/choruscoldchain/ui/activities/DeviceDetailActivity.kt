package com.choruscoldchain.ui.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.choruscoldchain.R

class DeviceDetailActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_DEVICE_MAC = "extra_device_mac"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_detail)
    }
}
