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

package me.shedaniel.linkie.discord

import discord4j.core.`object`.entity.User
import discord4j.core.spec.EmbedCreateSpec
import me.shedaniel.linkie.discord.handler.SimpleThrowableHandler

object LinkieThrowableHandler : SimpleThrowableHandler() {
    override fun shouldError(throwable: Throwable): Boolean =
        throwable !is SuppressedException && super.shouldError(throwable)

    override fun generateThrowable(builder: EmbedCreateSpec.Builder, throwable: Throwable, user: User) {
        super.generateThrowable(builder, throwable, user)
        if (isDebug) {
            throwable.printStackTrace()
        }
        when {
            throwable is org.graalvm.polyglot.PolyglotException -> {
                val details = throwable.localizedMessage?.take(800) ?: ""
                builder.fields(mutableListOf())
                builder.addField("Error occurred while processing the command", "```$details```", false)
            }
            throwable.javaClass.name.startsWith("org.graalvm") -> {
                val details = throwable.localizedMessage?.take(800) ?: ""
                builder.fields(mutableListOf())
                builder.addField("Error occurred while processing the command", "```" + throwable.javaClass.name + (if (details.isEmpty()) "" else ":\n") + details + "```", false)
            }
        }
    }
}
