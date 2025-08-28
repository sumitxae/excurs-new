package com.choruscoldchain.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.choruscoldchain.databinding.ActivityMainBinding
import com.choruscoldchain.ui.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Force dark status bar icons on light background across APIs
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        
        // Since EntryActivity is now the launcher, this activity is no longer needed as the main entry point
        // Navigate directly to ScanActivity
        startActivity(Intent(this, ScanActivity::class.java))
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            viewModel.cleanup()
        }
    }
}
