package com.example.queueapplication

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.queueapplication.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
//        makeRequest()
        startWorking()
    }

    private fun startWorking() {
        lifecycleScope.launchWhenCreated {
            viewModel.addItemToQueue {
                viewModel.startScan()
            }
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