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
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.nio.charset.StandardCharsets
import java.util.*

class QueueViewModel(private val application: Application) : AndroidViewModel(application) {

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
            application.applicationContext,
            BluetoothManager::class.java
        ) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter by lazy { bluetoothManager.adapter }
    private var bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    val bloodPressureChannel = Channel<BloodPressureMeasurement>(Channel.UNLIMITED)

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
                        viewModelScope.launch {
                            bluetoothGatt?.discoverServices()
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        logE(">> Successfully disconnected to $deviceAddress")
                        gatt?.close()
                    }
                    else -> { // Note the block
                        logE("UNKNOWN PROFILE: $newState")
                    }
                }
            } else {
                logE(">> Error $status encountered for $deviceAddress! Disconnecting...")
                gatt?.close()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            logE(">> BluetoothGattCallback Discovered ${gatt.services.size} services for ${gatt.device.address}")

            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    enableNotification(gatt)
                }
            }

//            submit {
//                withContext(Dispatchers.IO) {
//                    readDeviceName()
//                }
//            }
        }

        @SuppressLint("MissingPermission")
        private fun readDeviceName() {
            getCharacteristic(
                UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb"),
                UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb")
            )?.let { readCharacteristic ->
                bluetoothGatt?.readCharacteristic(readCharacteristic)
            }
        }

        @SuppressLint("MissingPermission")
        private fun enableNotification(gatt: BluetoothGatt) {
            getCharacteristic(
                UUID.fromString("00001810-0000-1000-8000-00805f9b34fb"),
                UUID.fromString("00002A35-0000-1000-8000-00805f9b34fb")
            )?.let { characteristic ->
                gatt.setCharacteristicNotification(characteristic, true)
                val descriptor =
                    characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                val statusOfNotification = gatt.writeDescriptor(descriptor)
                logE("enable Notification $statusOfNotification")
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

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            viewModelScope.launch {
                bloodPressureChannel.trySend(BloodPressureMeasurement.fromBytes(value))
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
            viewModelScope.launch {
                delay(DIRECT_CONNECTION_DELAY_IN_MS)
                bluetoothGatt = result.device.connectGatt(application, false, bluetoothGattCallback)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            logE("result coming on fail $errorCode")
        }
    }

    private fun registerBondingBroadcastReceivers() {
        val context = application.applicationContext
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
                val bondState =
                    intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
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

    @SuppressLint("MissingPermission")
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

    fun getCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): BluetoothGattCharacteristic? {
        return bluetoothGatt?.getService(serviceUUID)?.getCharacteristic(characteristicUUID)
    }

    fun setupRange() {
        val rangeComposition = RangeComposition()
        val findReadingList = listOf(
            Pair(89, 59),

            Pair(90, 60),
            Pair(119, 80),

            Pair(120, 79),
            Pair(129, 79),

            Pair(130, 80),
            Pair(139, 89),

            Pair(140, 90),
            Pair(179, 119),

            Pair(180, 120),
            Pair(200, 200),
        )
        for (item in findReadingList) {
            val (systolic, diastolic) = item
            val result = rangeComposition.findReadingWithPointer(systolic, diastolic)
            logE("systolic $systolic --+-- diastolic $diastolic --->> $result")
        }
    }
}