package me.shedaniel.linkie;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.MessageChannel;

import java.awt.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class CommandApi {
    
    protected Map<String, CommandBase> commandMap;
    protected String prefix;
    
    public CommandApi(String prefix) {
        this.commandMap = new HashMap<>();
        this.prefix = prefix.toLowerCase(Locale.ROOT);
    }
    
    public CommandApi registerCommand(CommandBase command, String... l) {
        for(String ll : l)
            commandMap.put(ll.toLowerCase(), command);
        return this;
    }
    
    public String getPrefix(boolean isSpecial) {
        return isSpecial ? "!" : prefix;
    }
    
    public void onMessageCreate(MessageCreateEvent event) {
        Member member = event.getMember().orElse(null);
        if (!event.getMember().isPresent() || member.isBot() || !event.getMessage().getContent().isPresent())
            return;
        CompletableFuture.runAsync(() -> {
            String message = event.getMessage().getContent().get();
            MessageChannel channel = event.getMessage().getChannel().block();
            String prefix = getPrefix(!channel.getType().name().startsWith("GUILD_") || (event.getGuildId().isPresent() && (event.getGuildId().get().asLong() != 432055962233470986l)));
            if (message.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                String content = message.substring(prefix.length());
                String split[] = content.contains(" ") ? content.split(" ") : new String[]{content}, cmd = split[0].toLowerCase(Locale.ROOT);
                String args[] = (Arrays.asList(split)).stream().skip(1).collect(Collectors.toList()).toArray(new String[0]);
                if (commandMap.containsKey(cmd))
                    try {
                        commandMap.get(cmd).execute(LinkieBot.executors, event, member, cmd, args, channel);
                    } catch (Throwable throwable) {
                        channel.createEmbed(emd -> {
                            emd.setTitle("Linkie Error");
                            emd.setColor(Color.red);
                            emd.setFooter("Requested by " + member.getUsername() + "#" + member.getDiscriminator(), member.getAvatarUrl());
                            emd.setTimestamp(Instant.now());
                            emd.addField("Error occurred while processing the command:", throwable.getClass().getSimpleName() + ": " + throwable.getLocalizedMessage(), false);
                        }).subscribe();
                    }
            }
        }, LinkieBot.executors);
    }
    
}