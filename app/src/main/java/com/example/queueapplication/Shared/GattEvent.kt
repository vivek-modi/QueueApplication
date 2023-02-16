package com.example.queueapplication

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor

sealed class GattEvent

data class ConnectionStateChanged(val status: Int, val newState: Int) : GattEvent()
data class ServicesDiscovered(val status: Int) : GattEvent()
data class CharacteristicRead(
    val characteristic: BluetoothGattCharacteristic,
    val status: Int,
    val value: ByteArray = ByteArray(0),
) : GattEvent()

data class DescriptorWritten(val descriptor: BluetoothGattDescriptor, val status: Int) : GattEvent()