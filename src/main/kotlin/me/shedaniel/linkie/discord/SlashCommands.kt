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

package me.shedaniel.linkie.discord

import com.soywiz.korio.async.runBlockingNoJs
import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Message
import discord4j.core.event.domain.InteractionCreateEvent
import discord4j.core.event.domain.lifecycle.ReadyEvent
import discord4j.core.interaction.GatewayInteractions
import discord4j.core.spec.EmbedCreateSpec
import discord4j.core.util.ImageUtil
import discord4j.discordjson.json.ApplicationCommandInteractionOptionData
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData
import discord4j.discordjson.json.ApplicationCommandOptionData
import discord4j.discordjson.json.ApplicationCommandRequest
import discord4j.discordjson.json.ImmutableApplicationCommandOptionData
import discord4j.discordjson.json.InteractionApplicationCommandCallbackData
import discord4j.discordjson.json.UserData
import discord4j.rest.interaction.InteractionHandler
import discord4j.rest.interaction.Interactions
import discord4j.rest.interaction.RestInteraction
import discord4j.rest.util.AllowedMentions
import discord4j.rest.util.Image
import me.shedaniel.linkie.discord.utils.event
import me.shedaniel.linkie.discord.utils.getOrNull
import reactor.core.publisher.Mono
import java.util.function.Function
import kotlin.properties.Delegates

class SlashCommands(
    val interactions: Interactions = Interactions.create(),
) {
    fun register() {
        val gatewayInteractions = GatewayInteractions.create(interactions)
        interactions.createCommands(gateway.restClient).subscribe()
        event<ReadyEvent> {
            Mono.from(gatewayInteractions.onReady(it)).subscribe()
        }
        event<InteractionCreateEvent> {
            Mono.from(gatewayInteractions.onInteractionCreate(it)).subscribe()
        }
    }

    inline fun guildCommand(guild: Long, name: String, description: String, builder: SlashCommandBuilder.() -> Unit) =
        guildCommand(Snowflake.of(guild), name, description, builder)

    inline fun guildCommand(guild: Snowflake, name: String, description: String, builder: SlashCommandBuilder.() -> Unit) {
        val commandBuilder = SlashCommandBuilder(name, description).also(builder)
        guildCommand(guild, commandBuilder.toCommand(), *commandBuilder.cmds.toTypedArray())
    }

    inline fun globalCommand(name: String, description: String, builder: SlashCommandBuilder.() -> Unit) {
        val commandBuilder = SlashCommandBuilder(name, description).also(builder)
        globalCommand(commandBuilder.toCommand(), *commandBuilder.cmds.toTypedArray())
    }

    fun guildCommand(guild: Long, command: SlashCommandBase, vararg l: String) =
        guildCommand(Snowflake.of(guild), command, *l)

    fun guildCommand(guild: Snowflake, command: SlashCommandBase, vararg l: String) {
        for (s in l) {
            interactions.onGuildCommand(buildRequest(command, s), guild, buildHandler(command, s))
        }
    }

    fun globalCommand(command: SlashCommandBase, vararg l: String) {
        for (s in l) {
            interactions.onGlobalCommand(buildRequest(command, s), buildHandler(command, s))
        }
    }

    private fun buildRequest(command: SlashCommandBase, cmd: String): ApplicationCommandRequest =
        ApplicationCommandRequest.builder()
            .name(command.name.also { require("^(?:[\\w-_])+\$".toRegex().matchEntire(it) != null) { "$it is not a valid name" } })
            .description(command.description)
            .addAllOptions(command.options.map(SlashCommandOption::toData))
            .build()

    private fun <T : RestInteraction> buildHandler(command: SlashCommandBase, cmd: String): Function<T, InteractionHandler> =
        Function { interaction ->
            val options = interaction.data.data().get().options().get()
            val interactionHandler = RestInteractionHandler(interaction)
            executeOptions(command, cmd, interactionHandler, command.options, options, options.iterator())
            interactionHandler.result ?: interaction.acknowledge()
        }

    private fun executeOptions(
        command: SlashCommandBase,
        cmd: String,
        interaction: RestInteractionHandler,
        options: List<SlashCommandOption>,
        allReceived: List<ApplicationCommandInteractionOptionData>,
        received: Iterator<ApplicationCommandInteractionOptionData>,
    ) {
        val data = received.next()
        options.firstOrNull { it.name == data.name() }?.also { applicableOption ->
            if (received.hasNext()) {
                if (applicableOption is NestedSlashCommandOption) {
                    return executeOptions(command, cmd, interaction, applicableOption.options, allReceived, received)
                }
            } else {
                applicableOption.execute(command, cmd, interaction)
                return
            }
        }
    }
}

interface SlashCommandBase {
    val name: String
    val description: String
    val options: List<SlashCommandOption>
}

class RestInteractionHandler(
    private val interaction: RestInteraction,
    internal var result: InteractionHandler? = null,
) : MessageCreator {
    val user: UserData
        get() = interaction.data.member().getOrNull()?.user() ?: interaction.data.user().get()
    val userDiscriminatedName: String
        get() = user.let { it.username() + "#" + it.discriminator() }
    val userAvatarUrl: String
        get() = user.let { it.avatar().getOrNull() ?: ImageUtil.getUrl(String.format("embed/avatars/%d", it.discriminator().toInt() % 5), Image.Format.PNG) }

    fun use(result: InteractionHandler) {
        this.result = result
    }

    fun acknowledge() = use(interaction.acknowledge())

    override val executorMessage: Message?
        get() = null
    override val executorId: Snowflake?
        get() = Snowflake.of(user.id())

    override fun reply(content: String): Mono<Message> {
        use(interaction.reply(InteractionApplicationCommandCallbackData.builder()
            .allowedMentions(AllowedMentions.suppressAll().toData())
            .tts(false)
            .content(content)
            .embeds(mutableListOf())
            .build()))
        return Mono.empty()
    }

    override fun reply(content: EmbedCreator): Mono<Message> {
        use(interaction.reply(InteractionApplicationCommandCallbackData.builder()
            .allowedMentions(AllowedMentions.suppressAll().toData())
            .tts(false)
            .embeds(mutableListOf(EmbedCreateSpec().also { runBlockingNoJs { content(it) } }.asRequest()))
            .build()))
        return Mono.empty()
    }
}

interface SlashCommandOptionAcceptor {
    fun sub(
        name: String,
        description: String,
        builder: SlashCommandOption.SubCommandOption.() -> Unit = {},
    ) = arg(SlashCommandOption.SubCommandOption(name, description).also(builder))

    fun subGroup(
        name: String,
        description: String,
        builder: SlashCommandOption.SubGroupCommandOption.() -> Unit = {},
    ) = arg(SlashCommandOption.SubGroupCommandOption(name, description).also(builder))

    fun string(
        name: String,
        description: String,
        builder: SlashCommandOption.StringCommandOption.() -> Unit = {},
    ) = arg(SlashCommandOption.StringCommandOption(name, description).also(builder))

    fun int(
        name: String,
        description: String,
        builder: SlashCommandOption.IntegerCommandOption.() -> Unit = {},
    ) = arg(SlashCommandOption.IntegerCommandOption(name, description).also(builder))

    fun bool(
        name: String,
        description: String,
        builder: SlashCommandOption.BooleanCommandOption.() -> Unit = {},
    ) = arg(SlashCommandOption.BooleanCommandOption(name, description).also(builder))

    fun user(
        name: String,
        description: String,
        builder: SlashCommandOption.UserCommandOption.() -> Unit = {},
    ) = arg(SlashCommandOption.UserCommandOption(name, description).also(builder))

    fun channel(
        name: String,
        description: String,
        builder: SlashCommandOption.ChannelCommandOption.() -> Unit = {},
    ) = arg(SlashCommandOption.ChannelCommandOption(name, description).also(builder))

    fun role(
        name: String,
        description: String,
        builder: SlashCommandOption.RoleCommandOption.() -> Unit = {},
    ) = arg(SlashCommandOption.RoleCommandOption(name, description).also(builder))

    fun <T : SlashCommandOption> arg(option: T): T
}

class SlashCommandBuilder(
    val name: String,
    val description: String,
    val options: MutableList<SlashCommandOption> = mutableListOf(),
    val cmds: MutableList<String> = mutableListOf(name),
) : SlashCommandOptionAcceptor {
    fun cmd(l: String) = cmds.add(l)
    fun cmd(vararg l: String) = cmds.addAll(l)

    override fun <T : SlashCommandOption> arg(option: T): T {
        option.adder = this
        options.add(option)
        return option
    }

    fun toCommand(): SlashCommandBase = object : SlashCommandBase {
        override val name: String = this@SlashCommandBuilder.name
        override val description: String = this@SlashCommandBuilder.description
        override val options: List<SlashCommandOption> = this@SlashCommandBuilder.options
    }
}

interface NestedSlashCommandOption : SlashCommandOptionAcceptor {
    val options: MutableList<SlashCommandOption>

    override fun <T : SlashCommandOption> arg(option: T): T {
        option.adder = this
        options.add(option)
        return option
    }
}

typealias SlashCommandExecutor = (command: SlashCommandBase, cmd: String, interaction: RestInteractionHandler) -> Unit

interface SlashCommandOption : SlashCommandOptionAcceptor {
    var adder: SlashCommandOptionAcceptor
    fun toData(): ApplicationCommandOptionData
    fun execute(command: SlashCommandBase, cmd: String, interaction: RestInteractionHandler)
    val name: String
    val description: String
    val required: Boolean

    abstract class AbstractSlashCommandOption(
        override var name: String,
        override var description: String,
        var executor: SlashCommandExecutor? = null,
        override val options: MutableList<SlashCommandOption> = mutableListOf(),
    ) : SlashCommandOption, NestedSlashCommandOption {
        abstract override var required: Boolean
        override var adder: SlashCommandOptionAcceptor by Delegates.notNull<SlashCommandOptionAcceptor>()
        abstract val type: Int
        override fun toData(): ApplicationCommandOptionData = ApplicationCommandOptionData.builder()
            .name(name.also { require("^(?:[\\w-_])+\$".toRegex().matchEntire(it) != null) { "$it is not a valid name" } })
            .description(description)
            .type(type)
            .apply {
                if (options.isNotEmpty()) {
                    addAllOptions(options.map(SlashCommandOption::toData))
                }
                if (required) {
                    required(required)
                }
            }
            .also(::addExtra)
            .build()

        open fun addExtra(data: ImmutableApplicationCommandOptionData.Builder) {}

        fun execute(executor: SlashCommandExecutor) {
            this.executor = executor
        }

        fun required(required: Boolean) {
            this.required = required
        }

        override fun execute(command: SlashCommandBase, cmd: String, interaction: RestInteractionHandler) {
            executor?.invoke(command, cmd, interaction)
        }
    }

    abstract class AbstractSingleSlashCommandOption(
        name: String,
        description: String,
        override var required: Boolean = true,
        executor: SlashCommandExecutor? = null,
    ) : AbstractSlashCommandOption(name, description, executor), SlashCommandOptionAcceptor {
        override fun <T : SlashCommandOption> arg(option: T): T = adder.arg(option)
    }

    class SubCommandOption(
        name: String,
        description: String,
        override var required: Boolean = false,
    ) : AbstractSlashCommandOption(name, description), NestedSlashCommandOption {
        override val type: Int
            get() = 1
    }

    class SubGroupCommandOption(
        name: String,
        description: String,
        override var required: Boolean = false,
    ) : AbstractSlashCommandOption(name, description), NestedSlashCommandOption {
        override val type: Int
            get() = 2
    }

    class StringCommandOption(
        name: String,
        description: String,
        val choices: MutableList<ApplicationCommandOptionChoiceData> = mutableListOf(),
    ) : AbstractSingleSlashCommandOption(name, description) {
        override val type: Int
            get() = 3

        override fun addExtra(data: ImmutableApplicationCommandOptionData.Builder) {
            if (choices.isNotEmpty()) {
                data.choices(choices)
            }
        }

        fun choice(value: String) = choice(value, value)

        fun choice(name: String, value: String) {
            choices.add(ApplicationCommandOptionChoiceData.builder()
                .name(name)
                .value(value)
                .build())
        }
    }

    class IntegerCommandOption(
        name: String,
        description: String,
        val choices: MutableList<ApplicationCommandOptionChoiceData> = mutableListOf(),
    ) : AbstractSingleSlashCommandOption(name, description) {
        override val type: Int
            get() = 4

        override fun addExtra(data: ImmutableApplicationCommandOptionData.Builder) {
            if (choices.isNotEmpty()) {
                data.choices(choices)
            }
        }

        fun choice(value: Int) = choice(value.toString(), value)

        fun choice(name: String, value: Int) {
            choices.add(ApplicationCommandOptionChoiceData.builder()
                .name(name)
                .value(value.toString())
                .build())
        }
    }

    class BooleanCommandOption(
        name: String,
        description: String,
    ) : AbstractSingleSlashCommandOption(name, description) {
        override val type: Int
            get() = 5
    }

    class UserCommandOption(
        name: String,
        description: String,
    ) : AbstractSingleSlashCommandOption(name, description) {
        override val type: Int
            get() = 6
    }

    class ChannelCommandOption(
        name: String,
        description: String,
    ) : AbstractSingleSlashCommandOption(name, description) {
        override val type: Int
            get() = 7
    }

    class RoleCommandOption(
        name: String,
        description: String,
    ) : AbstractSingleSlashCommandOption(name, description) {
        override val type: Int
            get() = 8
    }
}
