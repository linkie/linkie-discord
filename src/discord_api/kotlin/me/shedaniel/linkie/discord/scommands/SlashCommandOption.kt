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

import discord4j.common.util.Snowflake
import discord4j.core.`object`.command.ApplicationCommandOption
import discord4j.core.`object`.entity.Role
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.Channel
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData
import discord4j.discordjson.json.ApplicationCommandOptionData
import discord4j.discordjson.json.ImmutableApplicationCommandOptionData
import me.shedaniel.linkie.discord.utils.CommandContext
import me.shedaniel.linkie.discord.utils.Property
import me.shedaniel.linkie.discord.utils.blockNotNull
import me.shedaniel.linkie.discord.utils.client
import me.shedaniel.linkie.discord.utils.map
import me.shedaniel.linkie.discord.utils.property

class ReadResult<T> internal constructor(val value: T?, val fail: String?) {
    companion object {
        internal fun <T> fail(fail: String): ReadResult<T> = ReadResult(null, fail)
        internal fun <T> notFound(): ReadResult<T> = fail("Value not present!")
    }
}

private val <T> T.result: ReadResult<T>
    get() = ReadResult(this, null)

val ReadResult<*>.isSuccessful: Boolean
    get() = value != null

val <T> ReadResult<T>.valueOrThrow: T
    get() = value ?: throw RuntimeException(fail ?: "null")

private inline fun <T, R> ReadResult<T>.map(mapper: (T) -> R): ReadResult<R> =
    if (isSuccessful) mapper(value!!).result else ReadResult.fail(fail!!)

enum class ExecuteResult {
    EXECUTED,
    READ_VALUE,
    NONE,
}

interface SlashCommandOption<T> : SimpleCommandOptionMeta<T>, SlashCommandExecutor, SlashCommandExecutorAcceptor {
    fun toData(): ApplicationCommandOptionData
    fun execute(ctx: CommandContext, command: SlashCommand, reader: Property<ArgReader>, builder: OptionsGetterBuilder): ExecuteResult
}

abstract class AbstractSlashCommandOption<T>(
    var name: String,
    override var description: String,
    var executor: SlashCommandExecutor? = null,
) : SlashCommandOption<T> {
    override fun name(ctx: CommandContext): String = name
    abstract override var required: Boolean

    abstract val type: Int
    override fun toData(): ApplicationCommandOptionData = ApplicationCommandOptionData.builder()
        .name(name.also { require(it.toLowerCase() == it && "^[\\w-_]+\$".toRegex().matchEntire(it) != null) { "$it is not a valid name" } })
        .description(description)
        .type(type)
        .apply {
            if (required) {
                required(required)
            }
        }
        .also(::addExtra)
        .build()

    open fun addExtra(data: ImmutableApplicationCommandOptionData.Builder) {}

    override fun execute(executor: SlashCommandExecutor) {
        this.executor = executor
    }

    fun required(required: Boolean) {
        this.required = required
    }

    override fun execute(command: SlashCommand, ctx: CommandContext, optionsGetter: OptionsGetter): Boolean {
        return executor?.execute(command, ctx, optionsGetter) == true
    }
}

abstract class AbstractSingleSlashCommandOption<T>(
    name: String,
    description: String,
    override val parents: List<CommandOptionProperties>,
    override var required: Boolean = true,
) : AbstractSlashCommandOption<T>(name, description) {
    override fun execute(ctx: CommandContext, command: SlashCommand, reader: Property<ArgReader>, builder: OptionsGetterBuilder): ExecuteResult {
        val value = read(ctx, reader.get())
        return if (value.isSuccessful) {
            builder[this] = value.value
            if (execute(command, ctx, builder)) ExecuteResult.EXECUTED else ExecuteResult.READ_VALUE
        } else {
            ExecuteResult.NONE
        }
    }

    abstract fun read(ctx: CommandContext, reader: ArgReader): ReadResult<T>
}

abstract class AbstractNestedSlashCommandOption<T>(
    name: String,
    description: String,
    private val _parents: List<CommandOptionProperties>,
    override var required: Boolean = true,
    override val options: MutableList<SlashCommandOption<*>> = mutableListOf(),
) : AbstractSlashCommandOption<T>(name, description), NestedSlashCommandOption {
    override val parents: List<CommandOptionProperties>
        get() = _parents + this

    override fun addExtra(data: ImmutableApplicationCommandOptionData.Builder) {
        if (options.isNotEmpty()) {
            data.addAllOptions(options.map(SlashCommandOption<*>::toData))
        }
    }
}

class SubCommandOption(
    name: String,
    description: String,
    parents: List<CommandOptionProperties>,
    override var required: Boolean = false,
) : AbstractNestedSlashCommandOption<Map<String, Any>>(name, description, parents) {
    override val type: Int
        get() = ApplicationCommandOption.Type.SUB_COMMAND.value

    override fun execute(ctx: CommandContext, command: SlashCommand, reader: Property<ArgReader>, builder: OptionsGetterBuilder): ExecuteResult {
        if (name == reader.get().next()) {
            for (option in options) {
                val copy = reader.get().copy().property
                when (option.execute(ctx, command, copy, builder)) {
                    ExecuteResult.EXECUTED -> return ExecuteResult.EXECUTED
                    ExecuteResult.READ_VALUE -> reader.set(copy)
                }
            }
            return if (execute(command, ctx, builder)) {
                ExecuteResult.EXECUTED
            } else {
                ExecuteResult.READ_VALUE
            }
        }
        return ExecuteResult.NONE
    }
}

class SubGroupCommandOption(
    name: String,
    description: String,
    parents: List<CommandOptionProperties>,
    override var required: Boolean = false,
) : AbstractNestedSlashCommandOption<Pair<String, Any>>(name, description, parents) {
    override val type: Int
        get() = ApplicationCommandOption.Type.SUB_COMMAND_GROUP.value

    override fun execute(ctx: CommandContext, command: SlashCommand, reader: Property<ArgReader>, builder: OptionsGetterBuilder): ExecuteResult {
        for (option in options) {
            val result = option.execute(ctx, command, reader.map(ArgReader::copy), builder)
            if (result == ExecuteResult.EXECUTED) return ExecuteResult.EXECUTED
        }
        return if (execute(command, ctx, builder)) {
            ExecuteResult.EXECUTED
        } else {
            ExecuteResult.READ_VALUE
        }
    }
}

abstract class AbstractSingleChoicesSlashCommandOption<T>(
    name: String,
    description: String,
    parents: List<CommandOptionProperties>,
    val choices: MutableList<ApplicationCommandOptionChoiceData> = mutableListOf(),
) : AbstractSingleSlashCommandOption<T>(name, description, parents) {
    override fun addExtra(data: ImmutableApplicationCommandOptionData.Builder) {
        if (choices.isNotEmpty()) {
            data.choices(choices)
        }
    }

    protected fun internalChoice(name: String, value: Any) {
        choices.add(
            ApplicationCommandOptionChoiceData.builder()
                .name(name)
                .value(value)
                .build()
        )
    }
}

class StringCommandOption(
    name: String,
    description: String,
    parents: List<CommandOptionProperties>,
    private val unlimited: Boolean,
) : AbstractSingleChoicesSlashCommandOption<String>(name, description, parents) {
    override val type: Int
        get() = ApplicationCommandOption.Type.STRING.value

    fun choice(value: String) = choice(value, value)

    fun choice(name: String, value: String) {
        internalChoice(name, value)
    }

    override fun read(ctx: CommandContext, reader: ArgReader): ReadResult<String> =
        when (unlimited) {
            true -> if (!reader.hasNext()) ReadResult.notFound()
            else reader.iterator().asSequence().joinToString(" ").result
            else -> reader.next()?.result
        } ?: ReadResult.notFound()
}

class IntegerCommandOption(
    name: String,
    description: String,
    parents: List<CommandOptionProperties>,
) : AbstractSingleChoicesSlashCommandOption<Long>(name, description, parents) {
    override val type: Int
        get() = ApplicationCommandOption.Type.INTEGER.value

    fun choice(value: Int) = choice(value.toString(), value)

    fun choice(name: String, value: Int) {
        internalChoice(name, value)
    }

    override fun read(ctx: CommandContext, reader: ArgReader): ReadResult<Long> =
        reader.next()?.let {
            it.toLongOrNull()?.result ?: ReadResult.fail("$it is not a valid number!")
        } ?: ReadResult.notFound()
}

class BooleanCommandOption(
    name: String,
    description: String,
    parents: List<CommandOptionProperties>,
) : AbstractSingleSlashCommandOption<Boolean>(name, description, parents) {
    override val type: Int
        get() = ApplicationCommandOption.Type.BOOLEAN.value

    override fun read(ctx: CommandContext, reader: ArgReader): ReadResult<Boolean> =
        reader.next().let {
            when (it) {
                "true" -> true.result
                "false" -> false.result
                else -> ReadResult.fail("$it is not a valid boolean!")
            }
        }
}

class UserCommandOption(
    name: String,
    description: String,
    parents: List<CommandOptionProperties>,
) : AbstractSingleSlashCommandOption<User>(name, description, parents) {
    override val type: Int
        get() = ApplicationCommandOption.Type.USER.value

    override fun read(ctx: CommandContext, reader: ArgReader): ReadResult<User> =
        reader.next()?.let {
            it.toLongOrNull()?.result ?: ReadResult.fail("$it is not a valid number!")
        }?.map {
            ctx.client.getUserById(Snowflake.of(it)).blockNotNull()
        } ?: ReadResult.notFound()
}

class ChannelCommandOption(
    name: String,
    description: String,
    parents: List<CommandOptionProperties>,
) : AbstractSingleSlashCommandOption<Channel>(name, description, parents) {
    override val type: Int
        get() = ApplicationCommandOption.Type.CHANNEL.value

    override fun read(ctx: CommandContext, reader: ArgReader): ReadResult<Channel> =
        reader.next()?.let {
            it.toLongOrNull()?.result ?: ReadResult.fail("$it is not a valid number!")
        }?.map {
            ctx.client.getChannelById(Snowflake.of(it)).blockNotNull()
        } ?: ReadResult.notFound()
}

class RoleCommandOption(
    name: String,
    description: String,
    parents: List<CommandOptionProperties>,
) : AbstractSingleSlashCommandOption<Role>(name, description, parents) {
    override val type: Int
        get() = ApplicationCommandOption.Type.ROLE.value

    override fun read(ctx: CommandContext, reader: ArgReader): ReadResult<Role> =
        TODO()
}

//    class MentionableCommandOption(
//        name: String,
//        description: String,
//        parents: List<CommandOptionProperties>,
//    ) : AbstractSingleSlashCommandOption(name, description, parents) {
//        override val type: Int
//            get() = ApplicationCommandOption.Type.MENTIONABLE.value
//    }