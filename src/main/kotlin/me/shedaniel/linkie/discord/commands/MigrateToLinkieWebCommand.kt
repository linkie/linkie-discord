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

import me.shedaniel.linkie.discord.OptionlessCommand
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.basicEmbed
import me.shedaniel.linkie.discord.utils.reply

object MigrateToLinkieWebCommand : OptionlessCommand {
    override suspend fun execute(ctx: CommandContext) {
        ctx.message.reply {
            basicEmbed(ctx.user)
            title("Command removed")
            description("Version commands are now on the [web](https://linkie.shedaniel.me/dependencies/)!")
        }
    }
}
