/*
 * Copyright (c) 2019, 2020 shedaniel
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

import com.soywiz.korio.async.runBlockingNoJs
import me.shedaniel.linkie.discord.utils.getOrNull
import java.time.Duration
import java.util.*
import kotlin.concurrent.timerTask
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

@Suppress("MemberVisibilityCanBePrivate", "unused")
class ValueKeeper<T> constructor(val timeToKeep: Duration, var valueBackend: Optional<T>, val getter: suspend () -> T) : Lazy<T> {
    companion object {
        private val timer = Timer()
    }

    private var task: TimerTask? = null

    constructor(timeToKeep: Duration, value: T, getter: suspend () -> T) : this(timeToKeep, Optional.of(value), getter)
    constructor(timeToKeep: Duration, getter: suspend () -> T) : this(timeToKeep, runBlockingNoJs { getter() }, getter)

    init {
        runBlockingNoJs {
            schedule()
        }
    }

    suspend fun get(): T = valueBackend.getOrNull() ?: getter().also { valueBackend = Optional.of(it); schedule() }

    suspend fun clear() {
        valueBackend = Optional.empty()
        System.gc()
    }

    suspend fun schedule() {
        task?.cancel()
        task = timerTask { runBlockingNoJs { clear() } }
        timer.schedule(task, timeToKeep.toMillis())
    }

    override val value: T
        get() = runBlockingNoJs { get() }

    override fun isInitialized(): Boolean = valueBackend.isPresent
}

fun <T> valueKeeper(timeToKeep: Duration = Duration.ofMinutes(2), getter: suspend () -> T): ValueKeeperProperty<T> =
    ValueKeeperProperty(timeToKeep, getter)

class ValueKeeperProperty<T>(
    timeToKeep: Duration,
    getter: suspend () -> T,
) : ReadOnlyProperty<Any?, T>, Lazy<T> {
    val keeperLazy = lazy { ValueKeeper(timeToKeep, getter) }
    val keeper by keeperLazy
    val property = ReadOnlyProperty<Any?, T> { _, _ -> runBlockingNoJs { keeper.get() } }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return this.property.getValue(thisRef, property)
    }

    override fun isInitialized(): Boolean = keeperLazy.isInitialized() && keeper.isInitialized()
    override val value: T
        get() = runBlockingNoJs { keeper.get() }
}