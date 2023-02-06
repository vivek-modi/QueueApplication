package com.example.queueapplication

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
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class QueueViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val TAG = "Queue Logs"
        private const val UUID_MASK_STRING = "FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF"
        private const val BLUETOOTH_PRESSURE_128_STRING = "00001810-0000-1000-8000-00805f9b34fb"
    }

    private val commandQueue: Queue<(input: String) -> Unit> = ConcurrentLinkedQueue()
    private val deviceSet = HashSet<ScanResult>()
    private var bluetoothGatt: BluetoothGatt? = null
    private val bluetoothManager: BluetoothManager by lazy {
        ContextCompat.getSystemService(
            application.applicationContext,
            BluetoothManager::class.java
        ) as BluetoothManager
    }
    val bluetoothAdapter: BluetoothAdapter by lazy { bluetoothManager.adapter }
    private var bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    val context = application.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal val bluetoothGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            val deviceAddress = gatt?.device?.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        // After calling bluetoothGatt?.discoverServices()
                        // the outcome of service discovery will be delivered via BluetoothGattCallback’s onServicesDiscovered() method
                        logE(">> Successfully connected to $deviceAddress")

                        addItemToQueue {
                            bluetoothGatt?.discoverServices()
                        }

                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        logE(">> Successfully disconnected to $deviceAddress")
                        gatt?.close()
                    }
                    else -> { // Note the block
                        logE("UNKNOWN PROFILE: ${newState}")
                    }
                }
            } else {
                logE(">> Error $status encountered for $deviceAddress! Disconnecting...")
                gatt?.close()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                logE(">> BluetoothGattCallback Discovered ${services.size} services for ${device.address}")
                scope.launch {
                    getCharacteristic(
                        UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb"),
                        UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb")
                    )?.let { readCharacteristic ->
                        addItemToQueue {
                            bluetoothGatt?.readCharacteristic(readCharacteristic)
                        }
                    }
                }
                scope.launch {
                    getCharacteristic(
                        UUID.fromString("00001810-0000-1000-8000-00805f9b34fb"),
                        UUID.fromString("00002A35-0000-1000-8000-00805f9b34fb")
                    )?.let { characteristic ->
                        addItemToQueue {
                            val descriptor =
                                characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                            bluetoothGatt?.setCharacteristicNotification(characteristic, true)
                            descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                            bluetoothGatt?.writeDescriptor(descriptor)
                        }
                    }
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            logE("CharacteristicRead ->>> ${String(value, StandardCharsets.ISO_8859_1)}")
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            logE(">> BluetoothGattCallback ATT MTU changed to $mtu, success: ${status == BluetoothGatt.GATT_SUCCESS}")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            addItemToQueue {
                val measurement = BloodPressureMeasurement.fromBytes(value)
                logW("onCharacteristicChanged , $measurement")
            }
        }
    }
    private val defaultScanCallback: ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (deviceSet.contains(result))
            // dump result
                return

            registerBondingBroadcastReceivers()
            bluetoothGatt = result.device.connectGatt(application, false, bluetoothGattCallback)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            logE("result coming on fail $errorCode")
        }
    }

    private fun registerBondingBroadcastReceivers() {
        context.registerReceiver(
            bondStateReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        )
        context.registerReceiver(
            pairingRequestBroadcastReceiver,
            IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST)
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
                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                val previousBondState =
                    intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
                logE("handleBondStateChange --- $bondState ----$previousBondState")
            }
        }
    }

    private val pairingRequestBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val receivedDevice =
                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            if (!receivedDevice.address.equals(receivedDevice.address, ignoreCase = true)) return
            val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR)

            logE("pairing request received: $variant")

        }
    }

    init {
        setupQueuePolling()
    }

    fun addItemToQueue(item: (input: String) -> Unit) {
        Log.e(TAG, "Added Item ->> $item")
        commandQueue.add(item)
    }

    private fun setupQueuePolling() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.e(TAG, "Starting Polling")
            while (true) {
                synchronized(commandQueue) {
                    if (!commandQueue.isEmpty()) {
                        commandQueue.poll()?.let { qItem ->
                            qItem("This is input")
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        scope.launch {
            bluetoothLeScanner?.startScan(
                scanFilters(),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(),
                defaultScanCallback
            )
        }
    }

    internal fun scanFilters(): MutableList<ScanFilter> {
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

    fun getCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): BluetoothGattCharacteristic? {
        return bluetoothGatt?.getService(serviceUUID)?.getCharacteristic(characteristicUUID)
    }
}