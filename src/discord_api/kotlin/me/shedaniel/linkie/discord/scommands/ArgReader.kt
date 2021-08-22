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

package me.shedaniel.linkie.discord.scommands

interface ArgReader {
    fun copy(): ArgReader
    fun hasNext(): Boolean
    fun next(): String?
    fun peek(): String?
    fun all(): String
}

fun ArgReader.iterator(): Iterator<String> = object : Iterator<String> {
    override fun hasNext(): Boolean = this@iterator.hasNext()
    override fun next(): String = this@iterator.next() ?: throw ArrayIndexOutOfBoundsException()
}

fun ArgReader(args: MutableList<String>): ArgReader =
    ArgReaderImpl(args)

fun ArgReader(args: String): ArgReader =
    ArgReader(args.splitArgs())

fun String.splitArgs(): MutableList<String> {
    val args = mutableListOf<String>()
    val stringBuilder = StringBuilder()
    forEach {
        val whitespace = it.isWhitespace()
        if (whitespace) {
            args.add(stringBuilder.toString())
            stringBuilder.clear()
        }
        if (it == '\n' || !whitespace) {
            stringBuilder.append(it)
        }
    }
    if (stringBuilder.isNotEmpty())
        args.add(stringBuilder.toString())
    return args.dropLastWhile(String::isEmpty).toMutableList()
}

private class ArgReaderImpl(
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
