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

package me.shedaniel.linkie.discord.utils.extensions

import discord4j.discordjson.possible.Possible
import java.util.*

fun <T> Optional<T>.getOrNull(): T? = orElse(null)
fun OptionalInt.getOrNull(): Int? = if (isPresent) asInt else null
fun OptionalLong.getOrNull(): Long? = if (isPresent) asLong else null
fun OptionalDouble.getOrNull(): Double? = if (isPresent) asDouble else null

fun <T> Possible<T>.getOrNull(): T? = if (isAbsent) null else get()

fun <T> T?.possible(): Possible<T> =
    if (this == null) Possible.absent()
    else Possible.of(this)
