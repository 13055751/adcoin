package com.adcoin.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.adcoin.app.ui.AppRoot
import com.adcoin.app.ui.theme.AdCoinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AdCoinTheme {
                AppRoot()
            }
        }
    }
}
