package me.shedaniel.linkie;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;

import java.awt.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class CommandApi implements MessageCreateListener {
    
    protected Map<String, CommandBase> commandMap;
    protected String prefix;
    protected DiscordApi api;
    
    public CommandApi(DiscordApi api, String prefix) {
        this.api = api;
        this.commandMap = new HashMap<>();
        this.prefix = prefix.toLowerCase(Locale.ROOT);
    }
    
    public CommandApi registerCommand(CommandBase command, String... l) {
        for(String ll : l)
            commandMap.put(ll.toLowerCase(), command);
        return this;
    }
    
    public DiscordApi getApi() {
        return api;
    }
    
    public String getPrefix(boolean isSpecial) {
        return isSpecial ? "!" : prefix;
    }
    
    @Override
    public void onMessageCreate(MessageCreateEvent event) {
        if (event.getMessageAuthor().isBotUser())
            return;
        CompletableFuture.runAsync(() -> {
            String message = event.getMessageContent();
            String prefix = getPrefix(!event.isServerMessage() || (event.getServer().isPresent() && (event.getServer().get().getId() != 432055962233470986l)));
            if (message.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                String content = message.substring(prefix.length());
                String split[] = content.contains(" ") ? content.split(" ") : new String[]{content}, cmd = split[0].toLowerCase(Locale.ROOT);
                String args[] = (Arrays.asList(split)).stream().skip(1).collect(Collectors.toList()).toArray(new String[0]);
                if (commandMap.containsKey(cmd))
                    try {
                        commandMap.get(cmd).execute(LinkieBot.getInstance().executors, event, event.getMessageAuthor(), cmd, args);
                    } catch (Throwable throwable) {
                        event.getChannel().sendMessage(new EmbedBuilder().setTitle("Linkie Error").setColor(Color.red).setFooter("Requested by " + event.getMessageAuthor().getDiscriminatedName(), event.getMessageAuthor().getAvatar()).addField("Error occurred while processing the command:", throwable.getClass().getSimpleName() + ": " + throwable.getLocalizedMessage()).setTimestampToNow());
                    }
            }
        }, LinkieBot.getInstance().executors);
    }
    
}