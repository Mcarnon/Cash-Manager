package com.cashmanager.collector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.cashmanager.collector.ui.CashManagerApp
import com.cashmanager.collector.ui.theme.CashManagerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: CashManagerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CashManagerTheme {
                CashManagerApp(viewModel)
            }
        }
    }
}
