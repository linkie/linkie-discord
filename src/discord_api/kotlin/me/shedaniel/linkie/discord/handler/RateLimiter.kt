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

package me.shedaniel.linkie.discord.handler

import discord4j.core.`object`.entity.User
import java.util.*

open class RateLimiter(val maxRequestPer10Sec: Int) {
    data class Entry(
        val time: Long,
        val userId: Long,
    )

    private val log: Queue<Entry> = LinkedList()

    open fun allow(user: User, cmd: String, args: Map<String, Any>): Boolean {
        val curTime = System.currentTimeMillis()
        val boundary = curTime - 10000
        synchronized(log) {
            while (!log.isEmpty() && log.element().time <= boundary) {
                log.poll()
            }
            val userId = user.id.asLong()
            log.add(Entry(curTime, userId))
            return log.count { it.userId == userId } <= maxRequestPer10Sec
        }
    }

    open fun handledCommand(user: User, cmd: String, args: Map<String, Any>) {
    }
}