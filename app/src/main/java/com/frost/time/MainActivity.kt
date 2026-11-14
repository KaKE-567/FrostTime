package com.frost.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.frost.time.ui.screens.DashboardViewModel
import com.frost.time.ui.screens.MainDashboardScreen
import com.frost.time.ui.theme.FrostTimeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    private val screenUnlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> {
                    viewModel.onResume()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FrostTimeTheme(dynamicColor = true) {
                MainDashboardScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        try {
            registerReceiver(screenUnlockReceiver, filter)
        } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        viewModel.onPause()

        try {
            unregisterReceiver(screenUnlockReceiver)
        } catch (_: Exception) {}
    }
}
