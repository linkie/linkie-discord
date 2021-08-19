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

import kotlin.properties.Delegates

fun interface Provider<T : Any> {
    fun get(): T
}

val <T : Any> T.provider: Provider<T>
    get() = Provider { this }

val <T : Any> Provider<T>.lazy: Provider<T>
    get() {
        val value by lazy { this.get() }
        return Provider { value }
    }

fun <T : Any, R : Any> Provider<T>.map(mapper: (T) -> R): Provider<R> =
    Provider { mapper(get()) }

interface Property<T : Any> : Provider<T> {
    fun set(value: T): Property<T> = set(value.provider)
    fun set(value: Provider<T>): Property<T>
    fun listen(listener: (Provider<T>) -> Unit)

    companion object {
        fun <T : Any> create(): Property<T> = object : Property<T> {
            var value by Delegates.notNull<Provider<T>>()
            val listeners = mutableListOf<(Provider<T>) -> Unit>()
            override fun set(value: Provider<T>): Property<T> = apply {
                this.value = value
                this.listeners.forEach { listener ->
                    listener(this.value)
                }
            }

            override fun get(): T = this.value.get()
            override fun listen(listener: (Provider<T>) -> Unit) {
                this.listeners.add(listener)
            }
        }

        fun <T : Any> create(initialValue: T): Property<T> = create(initialValue.provider)
        fun <T : Any> create(initialValue: Provider<T>): Property<T> = create<T>().set(initialValue)
    }
}

val <T : Any> T.property: Property<T>
    get() = Property.create(this)

fun <T : Any, R : Any> Property<T>.map(mapper: (T) -> R): Property<R> {
    val property: Property<R> = Property.create(Provider { get() }.map(mapper).lazy)
    listen { property.set(it.map(mapper).lazy) }
    return property
}
