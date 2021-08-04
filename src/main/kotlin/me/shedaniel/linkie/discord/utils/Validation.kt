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

package me.shedaniel.linkie.discord.utils

import discord4j.core.`object`.entity.Member
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.rest.util.Permission
import me.shedaniel.linkie.InvalidUsageException
import me.shedaniel.linkie.MappingsProvider
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.discord.config.ConfigManager
import java.util.*

fun MessageCreateEvent.validateInGuild() {
    if (!guildId.isPresent) {
        throw IllegalStateException("This command is only available in servers.")
    }
}

inline fun CommandContext.validateInGuild(spec: InGuildCommandContext.() -> Unit) {
    if (guildId == null) {
        throw IllegalStateException("This command is only available in servers.")
    }
    spec(inGuild)
}

fun List<String>.validateEmpty(prefix: String, usage: String) =
    validateUsage(prefix, 0, usage)

fun List<String>.validateNotEmpty(prefix: String, usage: String) =
    validateUsage(prefix, 1..Int.MAX_VALUE, usage)

fun List<String>.validateUsage(prefix: String, length: Int, usage: String) =
    validateUsage(prefix, length..length, usage)

fun List<String>.validateUsage(prefix: String, length: IntRange, usage: String) {
    if (size !in length) {
        throw InvalidUsageException("$prefix$usage")
    }
}
fun Member.validateAdmin() = validatePermissions(Permission.ADMINISTRATOR)

fun Member.validatePermissions(permission: Permission) {
    if (basePermissions.block()?.contains(permission) != true) {
        throw IllegalStateException("This command requires `${permission.name.toLowerCase(Locale.ROOT).capitalize()}` permission!")
    }
}

fun Namespace.validateNamespace() {
    if (reloading) {
        throw IllegalStateException("Namespace (ID: $id) is reloading now, please try again in 5 seconds.")
    }
}

fun Namespace.validateGuild(event: MessageCreateEvent) {
    if (event.guildId.isPresent) {
        if (!ConfigManager[event.guildId.get().asLong()].isMappingsEnabled(id)) {
            throw IllegalStateException("Namespace (ID: $id) is disabled on this server.")
        }
    }
}

fun Namespace.validateGuild(ctx: CommandContext) {
    if (ctx.guildId != null) {
        if (!ConfigManager[ctx.guildId!!.asLong()].isMappingsEnabled(id)) {
            throw IllegalStateException("Namespace (ID: $id) is disabled on this server.")
        }
    }
}

fun MappingsProvider.validateDefaultVersionNotEmpty() {
    if (isEmpty()) {
        throw IllegalStateException("Invalid Default Version! Linkie may be reloading its cache right now.")
    }
}
