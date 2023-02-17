package com.example.queueapplication.shared

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelUuid
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.queueapplication.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.charset.StandardCharsets
import java.util.*


@SuppressLint("MissingPermission")
class SharedViewModel(private val application: Application) : AndroidViewModel(application) {

    companion object {
        private const val SCAN_START_DELAY = 1000L
        private const val DIRECT_CONNECTION_DELAY_IN_MS = 100L
        private const val UUID_MASK_STRING = "FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF"
        private const val BLUETOOTH_PRESSURE_128_STRING = "00001810-0000-1000-8000-00805f9b34fb"
    }

    private val deviceSet = HashSet<ScanResult>()
    private var bluetoothGatt: BluetoothGatt? = null
    private val bluetoothManager: BluetoothManager by lazy {
        ContextCompat.getSystemService(
            application.applicationContext, BluetoothManager::class.java
        ) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter by lazy { bluetoothManager.adapter }
    private var bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    val watchGattCallback = WatchGattCallback()
    val scanDeviceList by lazy { mutableStateListOf<ScanResult>() }
    val eventFlowInViewModel = watchGattCallback.eventFlow
    private val mutex = Mutex()
    private val defaultScanCallback: ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (deviceSet.contains(result))
            // dump result
                return

            registerBondingBroadcastReceivers()
            scanDeviceList.add(result)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            logE("result coming on fail $errorCode")
        }
    }

    private fun registerBondingBroadcastReceivers() {
        val context = application.applicationContext
        context.registerReceiver(
            bondStateReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        )
        context.registerReceiver(
            pairingRequestBroadcastReceiver, IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST)
        )
    }

    private val bondStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val receivedDevice =
                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return

            // Ignore updates for other devices
            if (!receivedDevice.address.equals(receivedDevice.address, ignoreCase = true)) return
            if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val bondState =
                    intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                val previousBondState =
                    intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
                logE("handleBondStateChange --- $bondState ----$previousBondState")

                if (bondState == BluetoothDevice.BOND_BONDED) {
                    viewModelScope.launch {
                        withContext(Dispatchers.IO) {
                            delay(500)
                            enableDiscovery()
                        }
                    }
                }
            }
        }
    }

    private val pairingRequestBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val receivedDevice =
                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            if (!receivedDevice.address.equals(receivedDevice.address, ignoreCase = true)) return
            val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR)

            logE("pairing request received: $variant")

        }
    }

    fun startScan() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                delay(SCAN_START_DELAY)
                bluetoothLeScanner?.startScan(
                    scanFilters(),
                    ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(),
                    defaultScanCallback
                )
            }
        }
    }

    private fun scanFilters(): MutableList<ScanFilter> {
        val scanFilterList = mutableListOf<ScanFilter>()
        val parcelUuidMask = ParcelUuid.fromString(UUID_MASK_STRING)
        val listItem = listOf(BLUETOOTH_PRESSURE_128_STRING)
        listItem.forEach {
            val scanFilter =
                ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(it), parcelUuidMask).build()
            scanFilterList.add(scanFilter)
        }
        return scanFilterList
    }

    suspend fun connectDevice() {
        val connectionStateChanged =
            mutex.queueWithTimeout("Connect") {
                connectGatt()
            }
        logE("connectionStateChanged on connectDevice --> ${connectionStateChanged.newState}")
        handleStateConnection(connectionStateChanged.status, connectionStateChanged.newState)
    }

    suspend fun reconnect(): ConnectionStateChanged {
        val device = scanDeviceList.first().device
        return mutex.queueWithTimeout("reconnect") {
            val state = bluetoothManager.getConnectionState(device, BluetoothProfile.GATT)
            if (state == BluetoothProfile.STATE_CONNECTED) {
                ConnectionStateChanged(BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
            } else {
                eventFlowInViewModel
                    .onSubscription { requireGatt().connect() }
                    .firstOrNull {
                        it is ConnectionStateChanged &&
                                it.status == BluetoothGatt.GATT_SUCCESS &&
                                it.newState == BluetoothProfile.STATE_CONNECTED
                    } as ConnectionStateChanged?
                    ?: ConnectionStateChanged(BluetoothGatt.GATT_FAILURE, BluetoothProfile.STATE_DISCONNECTED)
            }
        }
    }

    private suspend fun connectGatt(): ConnectionStateChanged {
        return eventFlowInViewModel.onSubscription {
            logE("eventFlowInViewModel connectGatt trigger")
            bluetoothGatt =
                scanDeviceList.first().device.connectGatt(
                    application,
                    true,
                    watchGattCallback,
                    BluetoothDevice.TRANSPORT_LE,
                )
        }.firstOrNull {
            it is ConnectionStateChanged && it.status == BluetoothGatt.GATT_SUCCESS && it.newState == BluetoothProfile.STATE_CONNECTED
        } as ConnectionStateChanged? ?: ConnectionStateChanged(
            BluetoothGatt.GATT_FAILURE,
            BluetoothProfile.STATE_DISCONNECTED
        )
    }

    private fun handleStateConnection(status: Int, newState: Int) {
        val deviceAddress = requireGatt().device?.address
        if (status == BluetoothGatt.GATT_SUCCESS) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    // After calling bluetoothGatt?.discoverServices()
                    // the outcome of service discovery will be delivered via BluetoothGattCallback’s onServicesDiscovered() method
                    logE(">> Successfully connected to $deviceAddress")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    logE(">> Successfully disconnected to $deviceAddress")
                    requireGatt().close()
                    viewModelScope.launch {
                        withContext(Dispatchers.IO) {
                            reconnect()
                        }
                    }
                }
                else -> { // Note the block
                    logE("UNKNOWN PROFILE: $newState")
                }
            }
        } else {
            logE(">> Error $status encountered for $deviceAddress! Disconnecting...")
            requireGatt().close()
        }
    }

    private suspend fun enableDiscovery() {
        val servicesDiscover = eventFlowInViewModel.onSubscription {
            discoverServices()
        }.firstOrNull {
            it is ServicesDiscovered &&
                    it.status == BluetoothGatt.GATT_SUCCESS
        } as? ServicesDiscovered ?: ServicesDiscovered(BluetoothGatt.GATT_FAILURE)
        if (servicesDiscover.status == BluetoothGatt.GATT_SUCCESS) {
            logE("servicesDiscover true")
            readDeviceCharacteristic()
            registerNotification()
        } else {
            logE("failed to discovery services")
        }
    }

    private suspend fun discoverServices() =
        mutex.queueWithTimeout("discover services") { requireGatt().discoverServices() }

    private suspend fun readDeviceCharacteristic() {
        delay(100)
        val characteristic = getService(
            UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb")
        ).getCharacteristic(
            UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb")
        )
        val result = handleDeviceCharacteristic(characteristic)
        if (result.status == BluetoothGatt.GATT_SUCCESS) {
            logE("deviceName -> ${String(result.value, StandardCharsets.ISO_8859_1)}")
        } else {
            throw IllegalStateException("Read failed!")
        }
    }

    private suspend fun handleDeviceCharacteristic(characteristic: BluetoothGattCharacteristic): CharacteristicRead =
        mutex.queueWithTimeout("read characteristic ${characteristic.uuid}") {
            eventFlowInViewModel
                .onSubscription {
                    val result = requireGatt().readCharacteristic(characteristic)
                    if (result) {
                        logE("handleDeviceCharacteristic true")
                    } else {
                        logE("handleDeviceCharacteristic false")
                    }
                }
                .firstOrNull {
                    it is CharacteristicRead
                } as CharacteristicRead?
                ?: CharacteristicRead(characteristic, BluetoothGatt.GATT_FAILURE)
        }

    private suspend fun registerNotification() {
        delay(100)
        val characteristic = getService(
            UUID.fromString("0001810-0000-1000-8000-00805f9b34fb")
        ).getCharacteristic(
            UUID.fromString("00002A35-0000-1000-8000-00805f9b34fb")
        )
        val enableNotificationStatus = enableNotification(characteristic)
        if (enableNotificationStatus) {
            logE("notification enable")
        } else {
            logE("notification disable")
        }
    }

    private suspend fun enableNotification(characteristic: BluetoothGattCharacteristic): Boolean {
        return mutex.queueWithTimeout("register notification on ${characteristic.uuid}") {
            requireGatt().setCharacteristicNotification(characteristic, true)
            val result =
                characteristic.descriptors.find { it.uuid == UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") }
                    ?.let { descriptor ->
                        descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        eventFlowInViewModel
                            .onSubscription {
                                val descriptorWrittenResult = requireGatt().writeDescriptor(descriptor)
                                logE("Descriptor ${descriptor.uuid} on ${descriptor.characteristic.uuid} written: $descriptorWrittenResult")
                                if (!descriptorWrittenResult) {
                                    emit(DescriptorWritten(descriptor, BluetoothGatt.GATT_FAILURE))
                                }
                            }
                            .firstOrNull { it is DescriptorWritten } as DescriptorWritten?
                            ?: DescriptorWritten(descriptor, BluetoothGatt.GATT_FAILURE)
                    }
            result?.status == BluetoothGatt.GATT_SUCCESS
        }
    }

    private suspend fun getService(serviceUUID: UUID): BluetoothGattService =
        mutex.queueWithTimeout("get service $serviceUUID") {
            requireGatt().getService(serviceUUID)
        }

    private fun requireGatt(): BluetoothGatt =
        bluetoothGatt ?: throw IllegalStateException("BluetoothGatt is null")

    private suspend fun <T> Mutex.queueWithTimeout(
        action: String, timeout: Long = 5000L, block: suspend CoroutineScope.() -> T
    ): T {
        return try {
            withLock {
                return@withLock withTimeout<T>(timeMillis = timeout, block = block)
            }
        } catch (e: Exception) {
            logE(" $e Timeout on BLE call: $action")
            throw e
        }
    }
}