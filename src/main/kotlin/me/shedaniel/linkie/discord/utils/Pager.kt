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

import discord4j.core.spec.EmbedCreateSpec

fun MessageCreator.sendPages(
    ctx: CommandContext,
    initialPage: Int,
    maxPages: Int,
    creator: suspend EmbedCreateSpec.Builder.(page: Int) -> Unit,
) {
    var page = initialPage
    val builder = embedCreator { creator(this, page) }
    reply(ctx, {
        row {
            secondaryButton("⬅".discordEmote, disabled = page == 0) {
                if (page > 0) {
                    page--
                    reply(builder)
                }
            }
            dismissButton()
            secondaryButton("➡".discordEmote, disabled = page >= maxPages - 1) {
                if (page < maxPages - 1) {
                    page++
                    reply(builder)
                }
            }
        }
    }, builder)
}

//fun MessageCreator.sendPages(
//    initialPage: Int,
//    maxPages: Int,
//    creator: suspend EmbedCreateSpec.Builder.(page: Int) -> Unit,
//) = sendPages(initialPage, maxPages, executorId, creator)
//
//fun MessageCreator.sendPages(
//    initialPage: Int,
//    maxPages: Int,
//    user: User?,
//    creator: suspend EmbedCreateSpec.Builder.(page: Int) -> Unit,
//) = sendPages(initialPage, maxPages, user?.id, creator)
//
//fun MessageCreator.sendPages(
//    initialPage: Int,
//    maxPages: Int,
//    userId: Snowflake?,
//    creator: suspend EmbedCreateSpec.Builder.(page: Int) -> Unit,
//) {
//    var page = initialPage
//    val builder = embedCreator { creator(this, page) }
//    reply(builder).subscribe { msg ->
//        msg.tryRemoveAllReactions().block()
//        buildReactions(Duration.ofMinutes(2)) {
//            if (maxPages > 1) register("⬅") {
//                if (page > 0) {
//                    page--
//                    reply(builder).subscribe()
//                }
//            }
//            registerB("❌") {
//                msg.delete().subscribe()
//                executorMessage?.delete()?.subscribe()
//                false
//            }
//            if (maxPages > 1) register("➡") {
//                if (page < maxPages - 1) {
//                    page++
//                    reply(builder).subscribe()
//                }
//            }
//        }.build(msg) { it == userId }
//    }
//}
