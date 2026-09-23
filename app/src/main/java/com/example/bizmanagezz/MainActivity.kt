package com.example.bizmanagezz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.bizmanagezz.ui.BizManageApp
import com.example.bizmanagezz.ui.theme.BizManagezzTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BizManagezzTheme {
                BizManageApp()
            }
        }
    }
}
