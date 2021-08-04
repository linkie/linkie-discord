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

import me.shedaniel.linkie.discord.scommands.OptionsGetter

interface ArgReader {
    fun copy(): ArgReader
    fun hasNext(): Boolean
    fun next(): String?
    fun peek(): String?
    fun all(): String
}

class ArgReaderImpl(
    val args: MutableList<String>,
    var cursor: Int = 0,
) : ArgReader {
    constructor(value: String?) : this(value?.let { mutableListOf(it) } ?: mutableListOf())
    constructor(value: OptionsGetter?) : this(value?.raw)

    override fun hasNext(): Boolean = cursor < args.size

    override fun next(): String? = peek()?.also { cursor++ }

    override fun peek(): String? = if (hasNext()) args[cursor] else null

    override fun all(): String = args.asSequence().drop(cursor).joinToString(" ")

    override fun copy(): ArgReader = ArgReaderImpl(args, cursor)
}