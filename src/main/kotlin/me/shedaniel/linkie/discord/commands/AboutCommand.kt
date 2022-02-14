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

package me.shedaniel.linkie.discord.commands

import discord4j.core.`object`.entity.User
import me.shedaniel.linkie.discord.OptionlessCommand
import me.shedaniel.linkie.discord.gateway
import me.shedaniel.linkie.discord.lang.i18n
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.addField
import me.shedaniel.linkie.discord.utils.basicEmbed
import me.shedaniel.linkie.discord.utils.description
import me.shedaniel.linkie.discord.utils.linkButton
import me.shedaniel.linkie.discord.utils.replyComplex

object AboutCommand : OptionlessCommand {
    override suspend fun execute(ctx: CommandContext) {
        ctx.message.replyComplex {
            embed {
                title("text.about.title".i18n(ctx))
                gateway.self.map(User::getAvatarUrl).block()?.also { url -> thumbnail(url) }
                description = "text.about.description".i18n(ctx)
                addField("text.about.license".i18n(ctx), "Apache 2.0")
                basicEmbed(ctx.user)
            }
            layout {
                row {
                    linkButton("text.about.links.core".i18n(ctx), "https://github.com/linkie/linkie-core/")
                    linkButton("text.about.links.bot".i18n(ctx), "https://github.com/linkie/linkie-discord/")
                    linkButton("text.about.links.invite".i18n(ctx), "https://discord.com/api/oauth2/authorize?client_id=472081983925780490&permissions=339008&scope=bot%20applications.commands")
                }
            }
        }
    }
}