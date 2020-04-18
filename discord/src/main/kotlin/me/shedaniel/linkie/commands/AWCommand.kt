package me.shedaniel.linkie.commands

import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import fr.minuskube.pastee.JPastee
import fr.minuskube.pastee.data.Paste
import fr.minuskube.pastee.data.Section
import me.shedaniel.linkie.*
import java.util.*

object AWCommand : CommandBase {
    private var pastee: JPastee? = null

    override fun execute(event: MessageCreateEvent, user: User, cmd: String, args: Array<String>, channel: MessageChannel) {
        if (args.size != 2)
            throw InvalidUsageException("!$cmd <namespace> <version>\nDo !namespaces for list of namespaces.")
        val namespace = Namespaces.namespaces[args.first().toLowerCase(Locale.ROOT)]
                ?: throw IllegalArgumentException("Invalid Namespace: ${args.first()}\nNamespaces: " + Namespaces.namespaces.keys.joinToString(", "))
        if (namespace.reloading)
            throw IllegalStateException("Mappings (ID: ${namespace.id}) is reloading now, please try again in 5 seconds.")
        if (!namespace.supportsAW())
            throw IllegalStateException("Mappings (ID: ${namespace.id}) does not support Access Widener.")
        val mappingsProvider = namespace.getProvider(args[1])
        if (mappingsProvider.isEmpty()) {
            val list = namespace.getAllSortedVersions()
            throw NullPointerException("Invalid Version: " + args.last() + "\nVersions: " +
                    if (list.size > 20)
                        list.take(20).joinToString(", ") + ", etc"
                    else list.joinToString(", "))
        }
        val message = channel.createEmbed {
            it.apply {
                setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
                setTimestampToNow()
                var desc = "Searching up entries for **${namespace.id} ${mappingsProvider.version}**."
                if (!mappingsProvider.cached!!) desc += "\nThis mappings version is not yet cached, might take some time to download."
                setDescription(desc)
            }
        }.block() ?: throw NullPointerException("Unknown Message!")
        try {
            val mappingsContainer = mappingsProvider.mappingsContainer!!.invoke()
            val builder = StringBuilder()

            builder.append("accessWidener\tv1  intermediary\n")
            mappingsContainer.classes.forEach { clazz ->
                builder.append("extendable class ${clazz.intermediaryName}\n")
                clazz.methods.forEach { method ->
                    builder.append("accessible method ${clazz.intermediaryName} ${method.intermediaryName} " +
                            "${method.intermediaryDesc}\n")
                }
                clazz.fields.forEach { field ->
                    builder.append("accessible field ${clazz.intermediaryName} ${field.intermediaryName} " +
                            "${field.intermediaryDesc}\n")
                }
            }
            val apiKey = System.getenv("PASTEEE") ?: throw IllegalStateException("Invalid paste.ee api key")
            if (pastee == null) pastee = JPastee(apiKey)
            val submit = (this.pastee ?: throw IllegalStateException("Failed to initialize paste.ee api")).submit(
                    Paste.builder()
                            .addSection(Section.builder()
                                    .name("everything.accesswidener")
                                    .contents(builder.toString())
                                    .build()
                            )
                            .build()
            )
            if (submit.isSuccess) {
                message.edit {
                    it.setEmbed {
                        it.setTitle("Everything Access Widener")
                        it.setFooter("Requested by " + user.discriminatedName, user.avatarUrl)
                        it.setTimestampToNow()
                        it.addField("Version", mappingsContainer.version)
                        it.addField("Paste", submit.link)
                    }
                }.subscribe()
            } else throw IllegalStateException("Failed to submit paste.ee paste: ${submit.errorString}")
        } catch (t: Throwable) {
            try {
                message.edit { it.setEmbed { it.generateThrowable(t, user) } }.subscribe()
            } catch (throwable2: Throwable) {
                throwable2.addSuppressed(t)
                throw throwable2
            }
        }
    }

    override fun getName(): String? = "Everything Access Widener"
    override fun getDescription(): String? = "Destroys your fabric environment by making everything public!"
}