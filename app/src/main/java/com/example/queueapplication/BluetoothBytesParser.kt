/*
 *   Copyright (c) 2021 Martijn van Welie
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy
 *   of this software and associated documentation files (the "Software"), to deal
 *   in the Software without restriction, including without limitation the rights
 *   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *   copies of the Software, and to permit persons to whom the Software is
 *   furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *   SOFTWARE.
 *
 */
package com.example.queueapplication

import java.nio.ByteOrder
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.util.*
import kotlin.math.pow

class BluetoothBytesParser(
    var value: ByteArray,
    var offset: Int = 0,
    var byteOrder: ByteOrder = LITTLE_ENDIAN
) {

    constructor(value: ByteArray, byteOrder: ByteOrder) : this(value, 0, byteOrder)

    fun getIntValue(formatType: Int): Int {
        val result = getIntValue(formatType, offset, byteOrder)
        offset += getTypeLen(formatType)
        return result
    }

    fun getIntValue(formatType: Int, offset: Int, byteOrder: ByteOrder): Int {
        require(offset + getTypeLen(formatType) <= value.size)

        when (formatType) {
            FORMAT_UINT8 -> return unsignedByteToInt(value[offset])
            FORMAT_UINT16 -> return if (byteOrder == LITTLE_ENDIAN) unsignedBytesToInt(
                value[offset],
                value[offset + 1]
            ) else unsignedBytesToInt(
                value[offset + 1], value[offset]
            )
            FORMAT_UINT24 -> return if (byteOrder == LITTLE_ENDIAN) unsignedBytesToInt(
                value[offset], value[offset + 1],
                value[offset + 2], 0.toByte()
            ) else unsignedBytesToInt(
                value[offset + 2], value[offset + 1],
                value[offset], 0.toByte()
            )
            FORMAT_UINT32 -> return if (byteOrder == LITTLE_ENDIAN) unsignedBytesToInt(
                value[offset], value[offset + 1],
                value[offset + 2], value[offset + 3]
            ) else unsignedBytesToInt(
                value[offset + 3], value[offset + 2],
                value[offset + 1], value[offset]
            )
            FORMAT_SINT8 -> return unsignedToSigned(unsignedByteToInt(value[offset]), 8)
            FORMAT_SINT16 -> return if (byteOrder == LITTLE_ENDIAN) unsignedToSigned(
                unsignedBytesToInt(
                    value[offset],
                    value[offset + 1]
                ), 16
            ) else unsignedToSigned(
                unsignedBytesToInt(
                    value[offset + 1],
                    value[offset]
                ), 16
            )
            FORMAT_SINT24 -> return if (byteOrder == LITTLE_ENDIAN) unsignedToSigned(
                unsignedBytesToInt(
                    value[offset],
                    value[offset + 1], value[offset + 2], 0.toByte()
                ), 24
            ) else unsignedToSigned(
                unsignedBytesToInt(
                    value[offset + 2],
                    value[offset + 1], value[offset], 0.toByte()
                ), 24
            )
            FORMAT_SINT32 -> return if (byteOrder == LITTLE_ENDIAN) unsignedToSigned(
                unsignedBytesToInt(
                    value[offset],
                    value[offset + 1], value[offset + 2], value[offset + 3]
                ), 32
            ) else unsignedToSigned(
                unsignedBytesToInt(
                    value[offset + 3],
                    value[offset + 2], value[offset + 1], value[offset]
                ), 32
            )
        }
        throw IllegalArgumentException()
    }

    fun getFloatValue(formatType: Int): Float {
        val result = getFloatValue(formatType, offset, byteOrder)
        offset += getTypeLen(formatType)
        return result
    }

    fun getFloatValue(formatType: Int, offset: Int, byteOrder: ByteOrder): Float {
        require(offset + getTypeLen(formatType) <= value.size)

        when (formatType) {
            FORMAT_SFLOAT -> return if (byteOrder == LITTLE_ENDIAN) bytesToFloat(
                value[offset],
                value[offset + 1]
            ) else bytesToFloat(
                value[offset + 1], value[offset]
            )
            FORMAT_FLOAT -> return if (byteOrder == LITTLE_ENDIAN) bytesToFloat(
                value[offset], value[offset + 1],
                value[offset + 2], value[offset + 3]
            ) else bytesToFloat(
                value[offset + 3], value[offset + 2],
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
    fun getDateTime(offset: Int): Date {
        // DateTime is always in little endian
        var localOffset = offset
        val year = getIntValue(FORMAT_UINT16, localOffset, LITTLE_ENDIAN)
        localOffset += getTypeLen(FORMAT_UINT16)
        val month = getIntValue(FORMAT_UINT8, localOffset, LITTLE_ENDIAN)
        localOffset += getTypeLen(FORMAT_UINT8)
        val day = getIntValue(FORMAT_UINT8, localOffset, LITTLE_ENDIAN)
        localOffset += getTypeLen(FORMAT_UINT8)
        val hour = getIntValue(FORMAT_UINT8, localOffset, LITTLE_ENDIAN)
        localOffset += getTypeLen(FORMAT_UINT8)
        val min = getIntValue(FORMAT_UINT8, localOffset, LITTLE_ENDIAN)
        localOffset += getTypeLen(FORMAT_UINT8)
        val sec = getIntValue(FORMAT_UINT8, localOffset, LITTLE_ENDIAN)
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
     * Convert signed bytes to a 32-bit unsigned int.
     */
    private fun unsignedBytesToInt(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Int {
        return (unsignedByteToInt(b0) + (unsignedByteToInt(b1) shl 8)
                + (unsignedByteToInt(b2) shl 16) + (unsignedByteToInt(b3) shl 24))
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
     * Convert signed bytes to a 32-bit float value.
     */
    private fun bytesToFloat(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Float {
        val mantissa = unsignedToSigned(
            unsignedByteToInt(b0)
                    + (unsignedByteToInt(b1) shl 8)
                    + (unsignedByteToInt(b2) shl 16), 24
        )
        return (mantissa * 10.toDouble().pow(b3.toDouble())).toFloat()
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
         * Characteristic value format type uint24
         */
        const val FORMAT_UINT24 = 0x13

        /**
         * Characteristic value format type uint32
         */
        const val FORMAT_UINT32 = 0x14

        /**
         * Characteristic value format type sint8
         */
        const val FORMAT_SINT8 = 0x21

        /**
         * Characteristic value format type sint16
         */
        const val FORMAT_SINT16 = 0x22

        /**
         * Characteristic value format type sint24
         */
        const val FORMAT_SINT24 = 0x23

        /**
         * Characteristic value format type sint32
         */
        const val FORMAT_SINT32 = 0x24

        /**
         * Characteristic value format type sfloat (16-bit float)
         */
        const val FORMAT_SFLOAT = 0x32

        /**
         * Characteristic value format type float (32-bit float)
         */
        const val FORMAT_FLOAT = 0x34

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