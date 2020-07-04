package me.shedaniel.linkie.discord.scripting

import me.shedaniel.linkie.ByteBuffer
import p0nki.pesl.api.`object`.PESLObject
import p0nki.pesl.api.builtins.PESLSerializationUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

fun ByteBuffer.writePESLObject(obj: PESLObject) {
    val stream = ByteArrayOutputStream()
    PESLSerializationUtils.writeObject(obj, DataOutputStream(stream))
    val bytes = stream.toByteArray()
    writeInt(bytes.size)
    writeByteArray(bytes)
}

fun ByteBuffer.readPESLObject(): PESLObject {
    return PESLSerializationUtils.readObject(DataInputStream(ByteArrayInputStream(readByteArray(readInt()))))
}