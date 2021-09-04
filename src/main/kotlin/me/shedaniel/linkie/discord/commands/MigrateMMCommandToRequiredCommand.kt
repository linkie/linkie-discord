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

import discord4j.rest.util.Color
import me.shedaniel.linkie.discord.OptionlessCommand
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.basicEmbed
import me.shedaniel.linkie.discord.utils.dismissButton

class MigrateMMCommandToRequiredCommand : OptionlessCommand {
    override suspend fun execute(ctx: CommandContext) {
        ctx.message.reply(ctx, {
            dismissButton()
        }) {
            title("Command Scheduled to be Removed")
            description("""Commands **!mm** / **!mmc** / **!mmf** / **!mmm** is scheduled to be removed,
                          |please switch to the following more explicit commands:
                          |
                          |**!mmi** / **!mmic** / **!mmif** / **!mmim** for Mojang Mappings via Intermediary **(Fabric)**
                          |**!mms** / **!mmsc** / **!mmsf** / **!mmsm** for Mojang Mappings via SRG **(Forge)**
                          |**!qh** / **!qhc** / **!qhf** / **!qhm** for Mojang Mappings via Hash **(Quilt)**""".trimMargin())
            color(Color.RED)
            basicEmbed(ctx.user)
        }
    }
}