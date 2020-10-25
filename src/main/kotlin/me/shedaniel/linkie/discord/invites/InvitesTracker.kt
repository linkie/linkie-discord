package me.shedaniel.linkie.discord.invites

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.channel.Channel
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.event.domain.InviteCreateEvent
import discord4j.core.event.domain.guild.MemberJoinEvent
import discord4j.core.event.domain.lifecycle.ReadyEvent
import me.shedaniel.linkie.discord.api
import me.shedaniel.linkie.discord.gateway
import me.shedaniel.linkie.discord.utils.addInlineField
import me.shedaniel.linkie.discord.utils.createEmbedMessage
import me.shedaniel.linkie.discord.utils.discriminatedName
import me.shedaniel.linkie.discord.utils.setTimestampToNow

class InvitesTracker(
    private val guildId: Long,
    private val channelId: Long
) {
    private val inviteEntries = mutableMapOf<String, InviteEntry>()

    fun init() {
        gateway.eventDispatcher.on(ReadyEvent::class.java).subscribe {
            updateCache()
        }
        gateway.eventDispatcher.on(InviteCreateEvent::class.java).subscribe {
            updateCache()
        }
        gateway.eventDispatcher.on(MemberJoinEvent::class.java).subscribe { event ->
            if (event.guildId.asLong() == guildId) {
                val entries = api.getGuildById(Snowflake.of(guildId)).invites.map {
                    val inviter = it.inviter().toOptional().orElse(null)
                    InviteEntry(it.code(), inviter?.id()?.toLongOrNull() ?: -1L, inviter?.let { "${it.username()}#${it.discriminator()}" } ?: "", it.uses().toOptional().orElse(-1))
                }.collectList().block()!!
                entries.firstOrNull {
                    inviteEntries[it.code]?.let { existingEntry -> existingEntry.uses < it.uses } == true
                }.let {
                    gateway.getChannelById(Snowflake.of(channelId)).filter { c -> c.type == Channel.Type.GUILD_TEXT }.subscribe { textChannel ->
                        val member = event.member
                        (textChannel as TextChannel).createEmbedMessage {
                            setTitle("**${member.discriminatedName}** joined the server.")
                            setThumbnail(member.avatarUrl)
                            setTimestampToNow()
                            if (it != null) {
                                addInlineField("Invite", it.code)
                                addInlineField("Invite By", it.creatorDiscriminatedName)
                            } else {
                                addInlineField("Invite", "Unknown")
                                addInlineField("Invite By", "Unknown")
                            }
                        }.subscribe()
                    }
                }
                inviteEntries.clear()
                entries.forEach { inviteEntries[it.code] = it }
            }
        }
    }

    private fun updateCache() {
        val entries = api.getGuildById(Snowflake.of(guildId)).invites.map {
            val inviter = it.inviter().toOptional().orElse(null)
            InviteEntry(it.code(), inviter?.id()?.toLongOrNull() ?: -1L, inviter?.let { "${it.username()}#${it.discriminator()}" } ?: "", it.uses().toOptional().orElse(-1))
        }.collectList().block()!!
        inviteEntries.clear()
        entries.forEach { inviteEntries[it.code] = it }
    }

    data class InviteEntry(
        val code: String,
        val creatorId: Long,
        val creatorDiscriminatedName: String,
        val uses: Int
    )
}