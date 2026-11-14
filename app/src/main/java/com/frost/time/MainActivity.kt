package com.frost.time

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frost.time.ui.screens.DashboardViewModel
import com.frost.time.ui.screens.MainDashboardScreen
import com.frost.time.ui.theme.FrostTimeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FrostTimeTheme(dynamicColor = true) {
                val viewModel: DashboardViewModel = viewModel()
                MainDashboardScreen(viewModel = viewModel)
            }
        }
    }
}
