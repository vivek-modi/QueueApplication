package com.example.queueapplication

import java.nio.ByteOrder
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.util.*
import kotlin.math.pow

class BluetoothBytesParser(
    private var value: ByteArray,
    var offset: Int = 0,
    var byteOrder: ByteOrder = LITTLE_ENDIAN
) {

    constructor(value: ByteArray, byteOrder: ByteOrder) : this(value, 0, byteOrder)

    fun getIntValue(formatType: Int): Int {
        val result = getIntValue(formatType, offset)
        offset += getTypeLen(formatType)
        return result
    }

    private fun getIntValue(formatType: Int, offset: Int): Int {
        require(offset + getTypeLen(formatType) <= value.size)

        when (formatType) {
            FORMAT_UINT8 -> return unsignedByteToInt(value[offset])
            FORMAT_UINT16 -> return unsignedBytesToInt(
                value[offset],
                value[offset + 1]
            )
        }
        throw IllegalArgumentException()
    }

    fun getFloatValue(formatType: Int): Float {
        val result = getFloatValue(formatType, offset, byteOrder)
        offset += getTypeLen(formatType)
        return result
    }

    private fun getFloatValue(formatType: Int, offset: Int, byteOrder: ByteOrder): Float {
        require(offset + getTypeLen(formatType) <= value.size)

        when (formatType) {
            FORMAT_SFLOAT -> return if (byteOrder == LITTLE_ENDIAN) bytesToFloat(
                value[offset],
                value[offset + 1]
            ) else bytesToFloat(
                value[offset + 1], value[offset]
            )
        }
        throw IllegalArgumentException()
    }

    val dateTime: Date
        get() {
            val result = getDateTime(offset)
            offset += 7
            return result
        }

    /**
     * Get Date from characteristic with offset
     *
     * @param offset Offset of value
     * @return Parsed date from value
     */
    private fun getDateTime(offset: Int): Date {
        // DateTime is always in little endian
        var localOffset = offset
        val year = getIntValue(FORMAT_UINT16, localOffset)
        localOffset += getTypeLen(FORMAT_UINT16)
        val month = getIntValue(FORMAT_UINT8, localOffset)
        localOffset += getTypeLen(FORMAT_UINT8)
        val day = getIntValue(FORMAT_UINT8, localOffset)
        localOffset += getTypeLen(FORMAT_UINT8)
        val hour = getIntValue(FORMAT_UINT8, localOffset)
        localOffset += getTypeLen(FORMAT_UINT8)
        val min = getIntValue(FORMAT_UINT8, localOffset)
        localOffset += getTypeLen(FORMAT_UINT8)
        val sec = getIntValue(FORMAT_UINT8, localOffset)
        val calendar = GregorianCalendar(year, month - 1, day, hour, min, sec)
        return calendar.time
    }

    private fun getTypeLen(formatType: Int): Int {
        return formatType and 0xF
    }

    /**
     * Convert a signed byte to an unsigned int.
     */
    private fun unsignedByteToInt(b: Byte): Int {
        return b.toInt() and 0xFF
    }

    /**
     * Convert signed bytes to a 16-bit unsigned int.
     */
    private fun unsignedBytesToInt(b0: Byte, b1: Byte): Int {
        return unsignedByteToInt(b0) + (unsignedByteToInt(b1) shl 8)
    }

    /**
     * Convert signed bytes to a 16-bit short float value.
     */
    private fun bytesToFloat(b0: Byte, b1: Byte): Float {
        val mantissa = unsignedToSigned(
            unsignedByteToInt(b0)
                    + (unsignedByteToInt(b1) and 0x0F shl 8), 12
        )
        val exponent = unsignedToSigned(unsignedByteToInt(b1) shr 4, 4)
        return (mantissa * 10.toDouble().pow(exponent.toDouble())).toFloat()
    }


    /**
     * Convert an unsigned integer value to a two's-complement encoded
     * signed value.
     */
    private fun unsignedToSigned(unsignedValue: Int, size: Int): Int {
        var unsigned = unsignedValue
        if (unsigned and (1 shl size - 1) != 0) {
            unsigned = -1 * ((1 shl size - 1) - (unsigned and (1 shl size - 1) - 1))
        }
        return unsigned
    }

    override fun toString(): String {
        return bytes2String(value)
    }

    companion object {
        /**
         * Characteristic value format type uint8
         */
        const val FORMAT_UINT8 = 0x11

        /**
         * Characteristic value format type uint16
         */
        const val FORMAT_UINT16 = 0x12

        /**
         * Characteristic value format type sfloat (16-bit float)
         */
        const val FORMAT_SFLOAT = 0x32

        /**
         * Convert a byte array to a string
         *
         * @param bytes the bytes to convert
         * @return String object that represents the byte array
         */
        fun bytes2String(bytes: ByteArray?): String {
            if (bytes == null) return ""
            val sb = StringBuilder()
            for (b in bytes) {
                sb.append(String.format("%02x", b.toInt() and 0xff))
            }
            return sb.toString()
        }
    }
}