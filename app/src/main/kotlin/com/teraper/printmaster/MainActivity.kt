package com.teraper.printmaster

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.teraper.printmaster.core.data.AppLocale
import com.teraper.printmaster.core.data.AppNames
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.ui.PrintMasterApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        // The language first, then the customer's own words for Master, Order and Client.
        super.attachBaseContext(AppNames.wrap(AppLocale.wrap(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PmTheme {
                PrintMasterApp()
            }
        }
    }
}
