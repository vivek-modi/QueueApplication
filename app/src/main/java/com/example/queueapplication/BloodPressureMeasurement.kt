package com.example.queueapplication

import com.example.queueapplication.BluetoothBytesParser.Companion.FORMAT_SFLOAT
import com.example.queueapplication.BluetoothBytesParser.Companion.FORMAT_UINT8
import java.nio.ByteOrder
import java.util.*

data class BloodPressureMeasurement(
    val systolic: Float,
    val diastolic: Float,
    val meanArterialPressure: Float,
    val unit: ObservationUnit,
    val timestamp: Date?,
    val pulseRate: Float?,
) {
    companion object {
        fun fromBytes(value: ByteArray): BloodPressureMeasurement {
            val parser = BluetoothBytesParser(value, ByteOrder.LITTLE_ENDIAN)
            val flags = parser.getIntValue(FORMAT_UINT8)
            val unit = if (flags and 0x01 > 0) ObservationUnit.MMHG else ObservationUnit.KPA
            val timestampPresent = flags and 0x02 > 0
            val pulseRatePresent = flags and 0x04 > 0

            val systolic = parser.getFloatValue(FORMAT_SFLOAT)
            val diastolic = parser.getFloatValue(FORMAT_SFLOAT)
            val meanArterialPressure = parser.getFloatValue(FORMAT_SFLOAT)
            val timestamp = if (timestampPresent) parser.dateTime else null
            val pulseRate = if (pulseRatePresent) parser.getFloatValue(FORMAT_SFLOAT) else null

            return BloodPressureMeasurement(
                systolic = systolic,
                diastolic = diastolic,
                meanArterialPressure = meanArterialPressure,
                unit = unit,
                timestamp = timestamp,
                pulseRate = pulseRate,
            )
        }
    }
}