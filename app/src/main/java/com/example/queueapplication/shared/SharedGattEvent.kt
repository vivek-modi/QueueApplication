package com.example.queueapplication.shared

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor

sealed class SharedGattEvent

data class ConnectionStateChanged(val status: Int, val newState: Int) : SharedGattEvent()
data class ServicesDiscovered(val status: Int) : SharedGattEvent()
data class CharacteristicRead(
    val characteristic: BluetoothGattCharacteristic,
    val status: Int,
    val value: ByteArray = ByteArray(0),
) : SharedGattEvent()

data class DescriptorWritten(val descriptor: BluetoothGattDescriptor, val status: Int) : SharedGattEvent()