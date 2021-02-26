/*
 * Copyright (c) 2019, 2020, 2021 shedaniel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.shedaniel.linkie.discord.tricks

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.User
import discord4j.rest.util.Permission
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import me.shedaniel.linkie.discord.scripting.ContextExtensions
import me.shedaniel.linkie.discord.scripting.EvalContext
import me.shedaniel.linkie.discord.scripting.ScriptingContext
import me.shedaniel.linkie.discord.scripting.funObj
import me.shedaniel.linkie.discord.scripting.getAsString
import me.shedaniel.linkie.discord.scripting.validateArgs
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
    val flags: List<Char> = emptyList(),
    val content: String,
)

object TrickFlags {
    val flags = mutableMapOf<Char, Flag>()

    init {
        flags['g'] = buildFlag("get user from id", Permission.MANAGE_MESSAGES) {
            this["getUser"] = funObj {
                validateArgs(1)
                val id = first().getAsString()
                var user: User? = null
                val guild = it.event.guild.block()!!
                runCatching {
                    user = guild.getMemberById(Snowflake.of(id)).block()
                }
                if (user == null && id.startsWith("<@!") && id.endsWith(">")) {
                    user = guild.getMemberById(Snowflake.of(id.substring(3, id.length - 1))).block()
                }
                if (user == null && id.startsWith("<@") && id.endsWith(">")) {
                    user = guild.getMemberById(Snowflake.of(id.substring(2, id.length - 1))).block()
                }
                if (user == null)
                    throw IllegalStateException("Failed to get user from \"$id\"")
                ContextExtensions.userObj(it, user!!)
            }
        }
        flags['p'] = buildFlag("open private channel", Permission.MANAGE_MESSAGES) {}
        flags['l'] = buildFlag("get last message", Permission.MANAGE_MESSAGES) {
            this["getLastMessage"] = funObj {
                validateArgs(0)
                val channel = it.event.message.channel.block()!!
                val message = channel.getMessagesBefore(it.event.message.id).filter { !it.author.get().isBot }.blockFirst() ?: throw IllegalStateException("Failed to get message!")
                ContextExtensions.messageObj(it, message, it.event.message.author.get())
            }
        }
    }
}

interface Flag {
    val name: String
    fun validatePermission(member: Member)
    fun extendContext(evalContext: EvalContext, context: ScriptingContext)

    fun Member.requirePermission(permission: Permission) {
        if (basePermissions.block()?.contains(permission) != true) {
            throw IllegalStateException("Using \"${name.toLowerCase(Locale.ROOT).capitalize()}\" requires `${permission.name.toLowerCase(Locale.ROOT).capitalize()}` permission!")
        }
    }

    fun Member.requirePermissions(vararg permissions: Permission) {
        val set = basePermissions.block()
        permissions.forEach {
            if (set?.contains(it) != true) {
                throw IllegalStateException("Using \"${name.toLowerCase(Locale.ROOT).capitalize()}\" requires `${it.name.toLowerCase(Locale.ROOT).capitalize()}` permission!")
            }
        }
    }
}

fun buildFlag(name: String, vararg permissions: Permission, builder: ScriptingContext.(EvalContext) -> Unit): Flag =
    object : Flag {
        override val name: String
            get() = name

        override fun validatePermission(member: Member) {
            member.requirePermissions(*permissions)
        }

        override fun extendContext(evalContext: EvalContext, context: ScriptingContext) {
            builder(context, evalContext)
        }
    }

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
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