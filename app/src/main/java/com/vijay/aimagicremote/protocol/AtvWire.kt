package com.vijay.aimagicremote.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Minimal bounded protobuf wire codec used by Android TV Remote v2.
 *
 * Adapted from OpenTV-Remote's Wire.kt (MIT), with bounds and API adjusted
 * for this project. See docs/THIRD_PARTY_NOTICES.md.
 */
internal object AtvWire {
    data class Field(val number: Int, val numberValue: Long = 0, val bytes: ByteArray? = null)

    fun varint(value: Long): ByteArray {
        require(value >= 0)
        var n = value
        val out = ByteArrayOutputStream()
        while (n > 127) {
            out.write(((n and 127) or 128).toInt())
            n = n ushr 7
        }
        out.write(n.toInt())
        return out.toByteArray()
    }

    fun readVarint(input: InputStream): Long {
        var result = 0L
        for (i in 0..9) {
            val b = input.read()
            require(b >= 0) { "TV closed the connection" }
            require(i < 9 || b <= 1) { "Invalid protobuf varint" }
            result = result or ((b and 127).toLong() shl (7 * i))
            if (b and 128 == 0) return result
        }
        throw IllegalArgumentException("Invalid protobuf varint")
    }

    fun int(field: Int, value: Int): ByteArray =
        varint((field * 8).toLong()) + varint(value.toLong())

    fun bytes(field: Int, value: ByteArray): ByteArray =
        varint((field * 8 + 2).toLong()) + varint(value.size.toLong()) + value

    fun string(field: Int, value: String): ByteArray =
        bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(vararg fields: ByteArray): ByteArray =
        fields.fold(byteArrayOf()) { result, field -> result + field }

    fun parse(data: ByteArray): List<Field> {
        val input = ByteArrayInputStream(data)
        val fields = mutableListOf<Field>()
        while (input.available() > 0) {
            val tag = readVarint(input).toInt()
            require(tag ushr 3 > 0) { "Invalid protobuf field tag" }
            when (tag and 7) {
                0 -> fields += Field(tag ushr 3, readVarint(input))
                2 -> {
                    val size = readVarint(input)
                    require(size in 0..1_048_576 && size <= input.available().toLong()) {
                        "Invalid protobuf field size"
                    }
                    val value = ByteArray(size.toInt())
                    require(value.isEmpty() || input.read(value) == value.size) {
                        "Truncated protobuf field"
                    }
                    fields += Field(tag ushr 3, bytes = value)
                }
                1 -> require(input.skip(8) == 8L) { "Truncated fixed64 field" }
                5 -> require(input.skip(4) == 4L) { "Truncated fixed32 field" }
                else -> throw IllegalArgumentException("Unsupported protobuf wire type")
            }
        }
        return fields
    }

    fun readFrame(input: InputStream): ByteArray {
        val size = readVarint(input)
        require(size in 1..1_048_576) { "Invalid protobuf frame length: $size" }
        val data = ByteArray(size.toInt())
        var offset = 0
        while (offset < data.size) {
            val count = input.read(data, offset, data.size - offset)
            require(count > 0) { "TV closed the connection during a frame" }
            offset += count
        }
        return data
    }

    fun writeFrame(output: OutputStream, message: ByteArray) {
        output.write(varint(message.size.toLong()))
        output.write(message)
        output.flush()
    }
}

internal fun List<AtvWire.Field>.number(field: Int): Int =
    firstOrNull { it.number == field }?.numberValue?.toInt() ?: 0

internal fun List<AtvWire.Field>.nested(field: Int): List<AtvWire.Field> =
    firstOrNull { it.number == field }?.bytes?.let(AtvWire::parse) ?: emptyList()
