package com.example.queueapplication

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val viewModel: QueueViewModel by viewModels()
    private val RECORD_REQUEST_CODE = 101

    @RequiresApi(Build.VERSION_CODES.S)
    val permissionList = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        makeRequest()
//        startWorking()

        setContent {
            Theme {
                Text(text = "Hi", color = Color.White)
            }
        }
    }

    private fun startWorking() {
        lifecycleScope.launchWhenCreated {
            viewModel.startScan()
        }
        lifecycleScope.launchWhenResumed {
            viewModel.bloodPressureChannel.consumeAsFlow().collect { measurement ->
                withContext(Dispatchers.Main) {
                    logW("onCharacteristicChanged ->>> $measurement")
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun makeRequest() {
        ActivityCompat.requestPermissions(
            this, permissionList, RECORD_REQUEST_CODE
        )
    }
}