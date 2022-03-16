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