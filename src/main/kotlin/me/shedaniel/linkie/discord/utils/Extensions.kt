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

import discord4j.core.`object`.entity.User
import me.shedaniel.linkie.Class
import me.shedaniel.linkie.Field
import me.shedaniel.linkie.Method
import me.shedaniel.linkie.Obf
import me.shedaniel.linkie.discord.LinkieThrowableHandler
import me.shedaniel.linkie.discord.SuppressedException

fun Obf.buildString(nonEmptySuffix: String? = null): String =
    when {
        isEmpty() -> ""
        isMerged() -> merged!! + (nonEmptySuffix ?: "")
        else -> buildString {
            if (client != null) append("client=**$client**")
            if (server != null) append("server=**$server**")
            if (nonEmptySuffix != null) append(nonEmptySuffix)
        }
    }

fun String?.suffixIfNotNull(suffix: String): String? =
    mapIfNotNull { it + suffix }

inline fun String?.mapIfNotNull(mapper: (String) -> String): String? =
    when {
        isNullOrEmpty() -> this
        else -> mapper(this)
    }

inline fun String?.mapIfNotNullOrNotEquals(other: String, mapper: (String) -> String): String? =
    when {
        isNullOrEmpty() -> null
        this == other -> null
        else -> mapper(this)
    }

val Class.optimumName: String
    get() = mappedName ?: intermediaryName

val Field.optimumName: String
    get() = mappedName ?: intermediaryName

val Method.optimumName: String
    get() = mappedName ?: intermediaryName

fun String.isValidIdentifier(): Boolean {
    forEachIndexed { index, c ->
        if (index == 0) {
            if (!Character.isJavaIdentifierStart(c))
                return false
        } else {
            if (!Character.isJavaIdentifierPart(c))
                return false
        }
    }
    return isNotEmpty()
}

inline fun <T> MessageCreator.getCatching(user: User, run: () -> T): T {
    try {
        return run()
    } catch (t: Throwable) {
        try {
            if (t !is SuppressedException) reply { LinkieThrowableHandler.generateThrowable(this, t, user) }
            throw SuppressedException()
        } catch (throwable2: Throwable) {
            throwable2.addSuppressed(t)
            throw throwable2
        }
    }
}
