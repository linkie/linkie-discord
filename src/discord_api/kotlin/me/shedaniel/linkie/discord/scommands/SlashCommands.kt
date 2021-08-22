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

package me.shedaniel.linkie.discord.scommands

import com.soywiz.korio.lang.InvalidArgumentException
import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.command.ApplicationCommandInteractionOption
import discord4j.core.`object`.entity.Role
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.Channel
import discord4j.core.event.domain.interaction.SlashCommandEvent
import discord4j.discordjson.json.ApplicationCommandData
import discord4j.discordjson.json.ApplicationCommandRequest
import discord4j.rest.util.ApplicationCommandOptionType.*
import discord4j.rest.util.Color
import me.shedaniel.linkie.discord.handler.ThrowableHandler
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.SlashCommandBasedContext
import me.shedaniel.linkie.discord.utils.basicEmbed
import me.shedaniel.linkie.discord.utils.dismissButton
import me.shedaniel.linkie.discord.utils.event
import me.shedaniel.linkie.discord.utils.extensions.getOrNull
import me.shedaniel.linkie.discord.utils.replyEmbed
import me.shedaniel.linkie.discord.utils.user
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class SlashCommands(
    private val client: GatewayDiscordClient,
    private val throwableHandler: ThrowableHandler,
    private val errorHandler: (String) -> Unit = { println("Error: $it") },
) {
    val applicationId: Long by lazy { client.restClient.applicationId.block() }
    val handlers = mutableMapOf<String, (event: SlashCommandEvent) -> Unit>()
    val guildHandlers = mutableMapOf<GuildCommandKey, (event: SlashCommandEvent) -> Unit>()
    val globalCommands: Flux<ApplicationCommandData> by lazy {
        client.restClient.applicationService
            .getGlobalApplicationCommands(applicationId)
            .cache()
    }
    val guildCommands = mutableMapOf<Snowflake, Flux<ApplicationCommandData>>()

    data class GuildCommandKey(val guildId: Snowflake, val commandName: String)

    fun getGuildCommands(id: Snowflake): Flux<ApplicationCommandData> {
        return guildCommands.getOrPut(id) {
            client.restClient.applicationService
                .getGuildApplicationCommands(applicationId, id.asLong())
                .cache()
        }
    }

    init {
        /*gateway.restClient.applicationService.getGuildApplicationCommands(applicationId, testingGuild).parallel().flatMap { data ->
            gateway.restClient.applicationService.deleteGuildApplicationCommand(applicationId, testingGuild, data.id().toLong())
                .doOnSuccess {
                    println(data.id())
                }
        }.then().block()*/
    }

    init {
        client.event<SlashCommandEvent> { event ->
            val handler = when {
                handlers.containsKey(event.commandName) -> handlers[event.commandName]!!
                event.interaction.guildId.isPresent && guildHandlers.containsKey(GuildCommandKey(event.interaction.guildId.get(), event.commandName)) ->
                    guildHandlers[GuildCommandKey(event.interaction.guildId.get(), event.commandName)]!!
                else -> return@event
            }
            handler(event)
        }
    }

    inline fun guildCommand(guild: Long, description: String, builder: SlashCommandBuilder.() -> Unit) =
        guildCommand(Snowflake.of(guild), description, builder)

    inline fun guildCommand(guild: Snowflake, description: String, builder: SlashCommandBuilder.() -> Unit) {
        val commandBuilder = SlashCommandBuilder(description).also(builder)
        guildCommand(guild, commandBuilder)
    }

    inline fun globalCommand(description: String, builder: SlashCommandBuilder.() -> Unit) {
        val commandBuilder = SlashCommandBuilder(description).also(builder)
        globalCommand(commandBuilder)
    }

    fun guildCommand(guild: Long, command: SlashCommand) =
        guildCommand(Snowflake.of(guild), command)

    fun guildCommand(guild: Snowflake, command: SlashCommand) {
        for (cmd in command.cmds) {
            var registered = false
            getGuildCommands(guild)
                .flatMap { data ->
                    if (data.name() == cmd) {
                        registered = true
                        val commandData = buildData(command, cmd, data.id(), data.applicationId())
                        if (data.toString() != commandData.toString()) {
                            println("not same $cmd guild ${guild.asString()}")
                            return@flatMap modifyGuildCommand(guild, command, data.id().toLong(), cmd)
                        }
                    }
                    return@flatMap Mono.empty()
                }.doOnComplete {
                    if (!registered) {
                        println("yes $cmd")
                        registered = true
                        createGuildCommand(guild, command, cmd).subscribe()
                    }
                }.subscribe()
            guildHandlers[GuildCommandKey(guild, cmd)] = buildHandler(command, cmd)
        }
    }

    private fun createGlobalCommand(command: SlashCommand, cmd: String) =
        client.restClient.applicationService
            .createGlobalApplicationCommand(applicationId, buildRequest(command, cmd))
            .doOnError { errorHandler("Unable to create global command: " + it.message) }
            .onErrorResume { Mono.empty() }

    private fun modifyGlobalCommand(command: SlashCommand, commandId: Long, cmd: String) =
        client.restClient.applicationService
            .modifyGlobalApplicationCommand(applicationId, commandId, buildRequest(command, cmd))
            .doOnError { errorHandler("Unable to create global command: " + it.message) }
            .onErrorResume { Mono.empty() }

    private fun createGuildCommand(guildId: Snowflake, command: SlashCommand, cmd: String) =
        client.restClient.applicationService
            .createGuildApplicationCommand(applicationId, guildId.asLong(), buildRequest(command, cmd))
            .doOnError { errorHandler("Unable to create guild command: " + it.message) }
            .onErrorResume { Mono.empty() }

    private fun modifyGuildCommand(guildId: Snowflake, command: SlashCommand, commandId: Long, cmd: String) =
        client.restClient.applicationService
            .modifyGuildApplicationCommand(applicationId, guildId.asLong(), commandId, buildRequest(command, cmd))
            .doOnError { errorHandler("Unable to create guild command: " + it.message) }
            .onErrorResume { Mono.empty() }

    fun globalCommand(command: SlashCommand) {
        for (cmd in command.cmds) {
            var registered = false
            globalCommands
                .flatMap { data ->
                    if (data.name() == cmd) {
                        registered = true
                        val commandData = buildData(command, cmd, data.id(), data.applicationId())
                        if (data.toString() != commandData.toString()) {
                            println("not same $cmd global")
                            return@flatMap modifyGlobalCommand(command, data.id().toLong(), cmd)
                        }
                    }
                    return@flatMap Mono.empty()
                }.doOnComplete {
                    if (!registered) {
                        println("yes $cmd")
                        registered = true
                        createGlobalCommand(command, cmd).subscribe()
                    }
                }.subscribe()
            handlers[cmd] = buildHandler(command, cmd)
        }
    }

    private fun buildHandler(command: SlashCommand, cmd: String): (event: SlashCommandEvent) -> Unit = { event ->
        var sentAny = false
        val ctx = SlashCommandBasedContext(command, cmd, event) {
            it.subscribe()
            sentAny = true
        }
        val optionsGetter = OptionsGetter.of(command, ctx, event)
        runCatching {
            if (!executeOptions(command, ctx, optionsGetter, command.options, event.options) && !command.execute(command, ctx, optionsGetter)) {
            }
        }.exceptionOrNull()?.also { throwable ->
            try {
                ctx.message.reply(ctx, {
                    dismissButton()
                }) {
                    throwableHandler.generateThrowable(this, throwable, ctx.user)
                }
            } catch (throwable2: Exception) {
                throwable2.addSuppressed(throwable)
                throwable2.printStackTrace()
            }
        }
        if (!sentAny) {
            event.replyEmbed {
                title("Linkie Error")
                color(Color.RED)
                basicEmbed(event.user)
                addField("Error occurred while processing the command", "```Command was not resolved!```", false)
            }.subscribe()
        }
    }

    private fun buildRequest(command: SlashCommand, cmd: String): ApplicationCommandRequest =
        ApplicationCommandRequest.builder()
            .name(cmd.also { require(it.toLowerCase() == it && "^[\\w-_]+\$".toRegex().matchEntire(it) != null) { "$it is not a valid name" } })
            .description(command.description(cmd))
            .addAllOptions(command.options.map(SlashCommandOption<*>::toData))
            .build()

    private fun buildData(command: SlashCommand, cmd: String, id: String, applicationId: String): ApplicationCommandData =
        ApplicationCommandData.builder()
            .id(id)
            .applicationId(applicationId)
            .name(cmd.also { require(it.toLowerCase() == it && "^[\\w-_]+\$".toRegex().matchEntire(it) != null) { "$it is not a valid name" } })
            .description(command.description(cmd))
            .apply {
                if (command.options.isNotEmpty()) {
                    addAllOptions(command.options.map(SlashCommandOption<*>::toData))
                }
            }
            .defaultPermission(true)
            .build()

    private inline fun <T, R> Iterable<T>.mapFirstNotNull(mapper: (T) -> R?): R? {
        for (element in this) {
            val mapped = mapper(element)
            if (mapped != null) {
                return mapped
            }
        }
        return null
    }

    private fun executeOptions(
        command: SlashCommand,
        ctx: CommandContext,
        optionsGetter: OptionsGetter,
        options: List<SlashCommandOption<*>>,
        received: List<ApplicationCommandInteractionOption>,
    ): Boolean {
        return received.any { receivedOption ->
            options.filter { it.name(ctx) == receivedOption.name }.any { applicableOption ->
                if (applicableOption is NestedSlashCommandOption) {
                    executeOptions(command, ctx, optionsGetter, applicableOption.options, receivedOption.options)
                            || applicableOption.execute(command, ctx, optionsGetter)
                } else {
                    applicableOption.execute(command, ctx, optionsGetter)
                }
            }
        }
    }
}

interface SlashCommand : SlashCommandExecutor, CommandOptionProperties {
    val cmds: List<String>
    val description: (cmd: String) -> String
    val options: List<SlashCommandOption<*>>
    val executor: SlashCommandExecutor?
    override fun execute(command: SlashCommand, ctx: CommandContext, optionsGetter: OptionsGetter): Boolean =
        executor?.execute(command, ctx, optionsGetter) == true

    fun usage(ctx: CommandContext): String {
        val root = SubGroupCommandOption(ctx.cmd, "", listOf())
        options.forEach(root::arg)
        executor?.also(root::execute)
        val tree = mutableListOf<SlashCommandOption<*>>()
        root.executionTree(tree::add)
        return "```${tree.joinToString("\n") { ctx.prefix + it.toReadableOption(ctx) }}```"
    }
}

private fun SlashCommandOption<*>.executionTree(submitter: (SlashCommandOption<*>) -> Unit) {
    if (this is NestedSlashCommandOption) {
        options.forEach { it.executionTree(submitter) }
    }
    if (this is AbstractSlashCommandOption && executor != null) {
        submitter(this)
    }
}

fun CommandOptionProperties.toReadableOption(ctx: CommandContext): String = buildString {
    parents.forEach { parent ->
        if (parent != this@toReadableOption) {
            append(parent.name(ctx))
            append(" ")
        }
    }
    append(name(ctx))
    if (this@toReadableOption is NestedSlashCommandOption) {
        options.forEach { option ->
            append(" ")
            append(if (option.required) "<" else "[")
            append(option.name(ctx))
            append(if (option.required) ">" else "]")
        }
    }
}

interface HasParents {
    val parents: List<CommandOptionProperties>
}

interface HasName {
    fun name(ctx: CommandContext): String
}

interface CommandOptionProperties : HasParents, HasName {
    val required: Boolean
}

interface SimpleCommandOptionMeta<T> : CommandOptionMeta<T, Unit> {
    fun mapValue(value: Any?): T? = value as? T
    override fun mapValue(value: Any?, extra: Unit): T? = mapValue(value)
}

interface CommandOptionMeta<T, R> : CommandOptionProperties {
    val description: String
    fun mapValue(value: Any?, extra: R): T?
    fun id(ctx: CommandContext): String =
        (parents.asSequence() + sequenceOf(this)).joinToString(".") { it.name(ctx) }
}

interface SlashCommandOptionAcceptor : HasParents {
    fun arg(option: SlashCommandOption<*>)
}

interface SlashCommandExecutorAcceptor {
    fun execute(executor: SlashCommandExecutor)
}

interface SlashCommandBuilderInterface : SlashCommandOptionAcceptor, SlashCommandExecutorAcceptor

class SlashCommandBuilder(
    override val description: (cmd: String) -> String,
    override val options: MutableList<SlashCommandOption<*>> = mutableListOf(),
    override val cmds: MutableList<String> = mutableListOf(),
    override var executor: SlashCommandExecutor? = null,
) : SlashCommandBuilderInterface, SlashCommand {
    constructor(
        description: String,
        options: MutableList<SlashCommandOption<*>> = mutableListOf(),
        cmds: MutableList<String> = mutableListOf(),
        executor: SlashCommandExecutor? = null,
    ) : this({ description }, options, cmds, executor)

    override fun name(ctx: CommandContext): String = ctx.cmd
    override val parents: List<CommandOptionProperties> = listOf(this)
    override val required: Boolean = false

    fun cmd(l: String) = apply { cmds.add(l) }
    fun cmd(vararg l: String) = apply { cmds.addAll(l) }

    override fun arg(option: SlashCommandOption<*>) {
        options.add(option)
    }

    override fun execute(executor: SlashCommandExecutor) {
        this.executor = executor
    }
}

interface NestedSlashCommandOption : SlashCommandOptionAcceptor, SlashCommandBuilderInterface {
    val options: MutableList<SlashCommandOption<*>>

    override fun arg(option: SlashCommandOption<*>) {
        options.add(option)
    }
}

fun interface SlashCommandExecutor {
    fun execute(command: SlashCommand, ctx: CommandContext, optionsGetter: OptionsGetter): Boolean
}

interface OptionsGetter {
    val slashCommand: SlashCommand
    val ctx: CommandContext
    fun getOption(name: String): OptionsGetter?

    val raw: String?
    val value: Any?
    fun asString(): String = value as String
    fun asBoolean(): Boolean = value as Boolean
    fun asLong(): Long = value as Long
    fun asSnowflake(): Snowflake = value as Snowflake
    fun asUser(): User = value as User
    fun asRole(): Role = value as Role
    fun asChannel(): Channel = value as Channel

    companion object {
        fun of(slashCommand: SlashCommand, ctx: CommandContext, event: SlashCommandEvent): OptionsGetter = object : OptionsGetter {
            override val slashCommand: SlashCommand
                get() = slashCommand
            override val ctx: CommandContext
                get() = ctx

            override fun getOption(name: String): OptionsGetter? =
                event.getOption(name).getOrNull()?.let { of(slashCommand, ctx, it) }

            override val raw: String?
                get() = null
            override val value: Any?
                get() = null
        }

        fun of(slashCommand: SlashCommand, ctx: CommandContext, option: ApplicationCommandInteractionOption): OptionsGetter = object : OptionsGetter {
            override val slashCommand: SlashCommand
                get() = slashCommand

            override val ctx: CommandContext
                get() = ctx

            override fun getOption(name: String): OptionsGetter? =
                option.getOption(name).getOrNull()?.let { of(slashCommand, ctx, it) }

            override val raw: String?
                get() = option.value.getOrNull()?.raw

            override val value: Any?
                get() = when (option.type) {
                    STRING -> option.value.getOrNull()?.asString()
                    INTEGER -> option.value.getOrNull()?.asLong()
                    BOOLEAN -> option.value.getOrNull()?.asBoolean()
                    USER -> option.value.getOrNull()?.asUser()
                    CHANNEL -> option.value.getOrNull()?.asChannel()
                    ROLE -> option.value.getOrNull()?.asRole()
                    MENTIONABLE -> TODO()
                    else -> null
                }
        }

        fun of(slashCommand: SlashCommand, ctx: CommandContext, value: Any?): OptionsGetter = object : OptionsGetter {
            override val slashCommand: SlashCommand
                get() = slashCommand
            override val ctx: CommandContext
                get() = ctx

            override fun getOption(name: String): OptionsGetter? = null
            override val raw: String?
                get() = null
            override val value: Any?
                get() = value
        }
    }
}

class OptionsGetterBuilder(override val slashCommand: SlashCommand, override val ctx: CommandContext) : OptionsGetter {
    val map = mutableMapOf<String, OptionsGetter>()

    operator fun <T> set(option: SimpleCommandOptionMeta<T>, value: T?) {
        map[option.id(ctx)] = OptionsGetter.of(slashCommand, ctx, value)
    }

    override fun getOption(name: String): OptionsGetter =
        InnerOptionsGetterBuilder(name, map[name] ?: map["${ctx.cmd}.$name"])

    override val raw: String?
        get() = null
    override val value: Any?
        get() = null

    inner class InnerOptionsGetterBuilder(
        val parent: String,
        val options: OptionsGetter?,
    ) : OptionsGetter {
        override val slashCommand: SlashCommand
            get() = this@OptionsGetterBuilder.slashCommand

        override val ctx: CommandContext
            get() = this@OptionsGetterBuilder.ctx

        override fun getOption(name: String): OptionsGetter =
            InnerOptionsGetterBuilder("$parent.$name", map["$parent.$name"] ?: map["${ctx.cmd}.$parent.$name"])

        override val raw: String?
            get() = options?.raw
        override val value: Any?
            get() = options?.value
    }
}

fun <T> OptionsGetter.opt(option: SimpleCommandOptionMeta<T>): T {
    return optNullable(option) ?: throw InvalidArgumentException("Option \"${option.id(ctx)}\" is required but was not provided!\nUsage: ${slashCommand.usage(ctx)}")
}

fun <T> OptionsGetter.optNullable(option: SimpleCommandOptionMeta<T>): T? {
    return optNullable(option, Unit)
}

fun <T, R> OptionsGetter.opt(option: CommandOptionMeta<T, R>, extra: R): T {
    return optNullable(option, extra) ?: throw InvalidArgumentException("Option \"${option.id(ctx)}\" is required but was not provided!\nUsage: ${slashCommand.usage(ctx)}")
}

fun <T, R> OptionsGetter.optNullable(option: CommandOptionMeta<T, R>, extra: R): T? {
    var opt: OptionsGetter? = null
    (option.parents + option).forEach {
        if (it == slashCommand) return@forEach
        val optional = if (opt == null) getOption(it.name(ctx))
        else opt!!.getOption(it.name(ctx))
        if (optional == null) {
            if (it.required) {
                throw Exception("Failed to get required option: " + it.name(ctx))
            }
            return option.mapValue(null, extra)
        }
        opt = optional
    }
    return option.mapValue(opt?.value, extra)
}
