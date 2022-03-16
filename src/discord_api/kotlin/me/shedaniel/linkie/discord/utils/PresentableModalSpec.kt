package me.shedaniel.linkie.discord.utils

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.interaction.ComponentInteractionEvent
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent
import discord4j.core.spec.InteractionPresentModalSpec
import java.time.Duration

typealias ExtraComponentAction = InteractionMessageCreator.(ComponentInteractionEvent, GatewayDiscordClient) -> ComponentActionType

class PresentableModalSpec(
    var title: String? = null,
    var layout: (LayoutComponentsBuilder.() -> Unit)? = null,
    val actions: MutableList<ExtraComponentAction> = mutableListOf(),
) {
    fun layout(creator: LayoutComponentsBuilder.() -> Unit) {
        layout = creator
    }

    fun title(title: String) {
        this.title = title
    }

    fun action(action: ExtraComponentAction) {
        actions += action
    }

    fun compile(client: GatewayDiscordClient, user: User): InteractionPresentModalSpec {
        val spec = InteractionPresentModalSpec.builder()
        val customId = customId()
        spec.customId(customId)
        title?.also { spec.title(it) }
        layout?.also {
            val builder = it.build()
            var actions = builder.actions
            var reacted = false

            client.event<ModalSubmitInteractionEvent>().take(Duration.ofMinutes(10)).subscribe { event ->
                if (reacted) return@subscribe
                actions.any { (filter, action) ->
                    when (filter(event, client, user)) {
                        ComponentActionType.NOT_APPLICABLE -> return@any false
                        ComponentActionType.ACKNOWLEDGE -> {
                            event.deferEdit().subscribe()
                            return@any true
                        }
                        ComponentActionType.HANDLE -> {
                            var sentAny = false
                            val msgCreator = ComponentInteractMessageCreator(event, client, user, extraConfig = { new ->
                                if (new.layout == null) {
                                    val newBuilder = layout!!.build()
                                    actions = newBuilder.actions
                                    components(newBuilder.components.toList())
                                } else reacted = true
                            }, { new ->
                                if (new.layout == null) {
                                    val newBuilder = layout!!.build()
                                    actions = newBuilder.actions
                                    components(newBuilder.components.toList().map { it.data })
                                } else reacted = true
                            }, {
                                it.subscribe()
                                sentAny = true
                            }, {
                                sentAny = true
                            })
                            action.invoke(msgCreator, event)
                            if (!sentAny) {
                                event.deferEdit().subscribe()
                            }
                            return@any true
                        }
                    }
                }

                this.actions.any { action ->
                    var sentAny = false
                    val msgCreator = ComponentInteractMessageCreator(event, client, user, extraConfig = { new ->
                        if (new.layout == null) {
                            val newBuilder = layout!!.build()
                            actions = newBuilder.actions
                            components(newBuilder.components.toList())
                        } else reacted = true
                    }, { new ->
                        if (new.layout == null) {
                            val newBuilder = layout!!.build()
                            actions = newBuilder.actions
                            components(newBuilder.components.toList().map { it.data })
                        } else reacted = true
                    }, {
                        it.subscribe()
                        sentAny = true
                    }, {
                        sentAny = true
                    })

                    when (action(msgCreator, event, client)) {
                        ComponentActionType.NOT_APPLICABLE -> return@any false
                        ComponentActionType.ACKNOWLEDGE -> {
                            event.deferEdit().subscribe()
                            return@any true
                        }
                        ComponentActionType.HANDLE -> {
                            if (!sentAny) {
                                event.deferEdit().subscribe()
                            }
                            return@any true
                        }
                    }
                }
            }

            spec.components(builder.components.toList())
        }
        return spec.build()
    }
}