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

import me.shedaniel.linkie.discord.Command
import me.shedaniel.linkie.discord.scommands.SlashCommandBuilderInterface
import me.shedaniel.linkie.discord.scommands.sub
import me.shedaniel.linkie.discord.template.AbstractTemplate
import me.shedaniel.linkie.discord.template.FabricTemplate
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.ComponentActionType
import me.shedaniel.linkie.discord.utils.presentModal
import me.shedaniel.linkie.discord.utils.smallText

object GenerateTemplateCommand : Command {
    val templates = listOf<AbstractTemplate>(
        FabricTemplate
    )

    override suspend fun SlashCommandBuilderInterface.buildCommand(slash: Boolean) {
        templates.forEach { template ->
            sub(template.id, "Generate a ${template.id} template...") {
                executeCommandWithNothing {
                    it.executeWithTemplate(template)
                }
            }
        }
    }

    private fun CommandContext.executeWithTemplate(template: AbstractTemplate) {
        message.presentModal {
            title = "Generate a ${template.id} template"

            layout {
                smallText("Mod ID", required = true, minLength = 2, maxLength = 32)
                smallText("Mod Name", required = true)
                smallText("Maven Group", required = true)
                row {
                    smallText("Version", required = true, minLength = 1, maxLength = 16)
                    smallText("Author (Split by comma)")
                }
            }

            action { event, client ->
                return@action ComponentActionType.HANDLE
            }
        }
    }
}