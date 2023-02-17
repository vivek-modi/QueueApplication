package com.example.queueapplication.shared

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.example.queueapplication.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class WatchGattCallback : BluetoothGattCallback() {

    val eventFlow = MutableSharedFlow<GattEvent>(extraBufferCapacity = 50)
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        logE("onConnectionStateChange status -> $status == newState -> $newState")
        eventFlow.emitEvent(ConnectionStateChanged(status, newState))
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        logE("onServicesDiscovered status -> $status")
        eventFlow.emitEvent(ServicesDiscovered(status))
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        super.onCharacteristicRead(gatt, characteristic, value, status)
        logE("onCharacteristicRead status -> $status")
        eventFlow.emitEvent(CharacteristicRead(characteristic, status, value))
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor, status: Int) {
        super.onDescriptorWrite(gatt, descriptor, status)
        logE("onDescriptorWrite status -> $status")
        eventFlow.emitEvent(DescriptorWritten(descriptor, status))
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        val data = BloodPressureMeasurement.fromBytes(value)
        logE("data received $data")
    }

    private fun <T> MutableSharedFlow<T>.emitEvent(event: T) {
        scope.launch { emit(event) }
    }
}