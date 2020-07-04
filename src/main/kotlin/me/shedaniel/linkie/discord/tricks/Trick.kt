package me.shedaniel.linkie.discord.tricks

import discord4j.core.`object`.entity.Member
import discord4j.rest.util.Permission
import kotlinx.serialization.*
import java.util.*

@Serializable
data class Trick(
        @Serializable(with = UUIDSerializer::class)
        val id: UUID,
        val name: String,
        val guildId: Long,
        val author: Long,
        val creation: Long,
        val modified: Long,
        val contentType: ContentType,
        val content: String
)

@Serializer(forClass = UUID::class)
object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = SerialDescriptor("UUIDSerializer", PolymorphicKind.OPEN)
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
}

fun Member.canManageTrick(trick: Trick): Boolean =
        id.asLong() == trick.author || basePermissions.block()?.contains(Permission.MANAGE_CHANNELS) == true

enum class ContentType {
    UNKNOWN,
    TEXT,
    SCRIPT
}