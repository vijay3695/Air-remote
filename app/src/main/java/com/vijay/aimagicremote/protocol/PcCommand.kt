package com.vijay.aimagicremote.protocol

/**
 * Small, dependency-free encoder for the newline-delimited JSON protocol used
 * by pc_receiver.ps1. Keeping this separate makes the wire contract unit-testable.
 */
internal data class PcCommand(
    val command: String,
    val id: String? = null,
    val dx: Float? = null,
    val dy: Float? = null,
    val button: String? = null,
    val key: String? = null,
    val text: String? = null
) {
    fun encode(): String {
        val fields = mutableListOf<String>()
        fields += "\"cmd\":\"${command.jsonEscape()}\""
        id?.let { fields += "\"id\":\"${it.jsonEscape()}\"" }
        dx?.let { fields += "\"dx\":$it" }
        dy?.let { fields += "\"dy\":$it" }
        button?.let { fields += "\"button\":\"${it.jsonEscape()}\"" }
        key?.let { fields += "\"key\":\"${it.jsonEscape()}\"" }
        text?.let { fields += "\"text\":\"${it.jsonEscape()}\"" }
        return "{${fields.joinToString(",")}}"
    }

    private fun String.jsonEscape(): String = buildString(length + 8) {
        for (char in this@jsonEscape) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
            }
        }
    }
}
