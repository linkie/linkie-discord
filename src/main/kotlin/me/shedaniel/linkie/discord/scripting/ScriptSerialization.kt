package me.shedaniel.linkie.discord.scripting

import me.shedaniel.linkie.ByteBuffer
import p0nki.pesl.api.`object`.PESLObject
import p0nki.pesl.api.builtins.PESLDataUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

fun ByteBuffer.writePESLObject(obj: PESLObject) {
    val stream = ByteArrayOutputStream()
    PESLDataUtils.write(obj, DataOutputStream(stream))
    val bytes = stream.toByteArray()
    writeInt(bytes.size)
    writeByteArray(bytes)
}

fun ByteBuffer.readPESLObject(): PESLObject {
    return PESLDataUtils.read(DataInputStream(ByteArrayInputStream(readByteArray(readInt()))))
}