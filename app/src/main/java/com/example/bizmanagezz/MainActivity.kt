package com.example.bizmanagezz

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.bizmanagezz.data.repository.BizRepository
import com.example.bizmanagezz.ui.BizManageApp
import com.example.bizmanagezz.ui.theme.BizManagezzTheme

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        BizRepository.initialize(applicationContext)

        Log.d(
            "BizManageLifecycle",
            "Application created"
        )

        enableEdgeToEdge()

        setContent {
            BizManagezzTheme {
                BizManageApp()
            }
        }
    }
}