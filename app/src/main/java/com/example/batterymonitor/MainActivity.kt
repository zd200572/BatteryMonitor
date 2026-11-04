package com.example.batterymonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.batterymonitor.ui.BatteryDashboardScreen
import com.example.batterymonitor.ui.theme.BatteryMonitorTheme

class MainActivity : ComponentActivity() {

    private val viewModel: BatteryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BatteryMonitorTheme {
                BatteryDashboardScreen(viewModel = viewModel)
            }
        }
    }
}
