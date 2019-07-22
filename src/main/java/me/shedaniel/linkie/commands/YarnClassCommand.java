package me.shedaniel.linkie.commands;

import me.shedaniel.linkie.CommandBase;
import me.shedaniel.linkie.InvalidUsageException;
import me.shedaniel.linkie.yarn.YarnClass;
import me.shedaniel.linkie.yarn.YarnManager;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

public class YarnClassCommand implements CommandBase {
    @Override
    public void execute(ScheduledExecutorService service, MessageCreateEvent event, MessageAuthor author, String cmd, String[] args) {
        if (YarnManager.updating)
            throw new RuntimeException("Yarn is being downloaded, please wait for around 10 seconds");
        if (args.length != 1)
            throw new InvalidUsageException("+" + cmd + " [search term]");
        event.getChannel().sendMessage(new EmbedBuilder().setTitle("Loading...").setFooter("Requested by " + author.getDiscriminatedName(), author.getAvatar()).setTimestampToNow()).whenComplete((message1, throwable) -> {
            try {
                String name = args[0];
                String low = name.toLowerCase(Locale.ROOT);
                List<YarnClass> files = new ArrayList<>();
                YarnManager.r1_2_5.tiny.getClassEntries().forEach(classEntry -> {
                    String intermediary = classEntry.get("intermediary");
                    String server = classEntry.get("server");
                    String client = classEntry.get("client");
                    if (getLast(intermediary).toLowerCase(Locale.ROOT).contains(low) || (server != null && server.contains(low)) || (client != null && client.contains(low))) {
                        files.add(new YarnClass(intermediary, server, client));
                    }
                });
                YarnManager.r1_2_5.mappings.forEach(mappingsFile -> {
                    if (!files.stream().anyMatch(yarnClass -> getLast(yarnClass.getIntermediary()).equalsIgnoreCase(getLast(mappingsFile.getObfClass()))))
                        if (mappingsFile.getObfClass().toLowerCase(Locale.ROOT).contains(low) || mappingsFile.getDeobfClass().toLowerCase(Locale.ROOT).contains(low))
                            files.add(new YarnClass(mappingsFile.getObfClass(), mappingsFile.getDeobfClass()));
                });
                files.forEach(yarnClass -> {
                    if (yarnClass.incomplete())
                        if (yarnClass.needObf()) {
                            YarnManager.r1_2_5.tiny.getClassEntries().stream().filter(classEntry -> {
                                return getLast(classEntry.get("intermediary")).equalsIgnoreCase(getLast(yarnClass.getIntermediary()));
                            }).findAny().ifPresent(classEntry -> {
                                yarnClass.setClient(classEntry.get("client"));
                                yarnClass.setServer(classEntry.get("server"));
                            });
                        } else if (yarnClass.needMapped()) {
                            YarnManager.r1_2_5.mappings.stream().filter(mappingsFile -> {
                                return getLast(mappingsFile.getObfClass()).equalsIgnoreCase(getLast(yarnClass.getIntermediary()));
                            }).findAny().ifPresent(mappingsFile -> yarnClass.setMapped(mappingsFile.getDeobfClass()));
                        }
                });
                if (files.isEmpty())
                    throw new NullPointerException("null");
                files.sort(Comparator.comparingDouble(value -> similarity(get(value, getLast(low)), getLast(low))));
                Collections.reverse(files);
                EmbedBuilder builder = new EmbedBuilder().setTitle("List of Yarn Mappings").setFooter("Requested by " + author.getDiscriminatedName(), author.getAvatar()).setTimestampToNow();
                String desc = files.stream().limit(10).map(yarnClass -> {
                    String obf = "client=" + yarnClass.getClient() + ",server=" + yarnClass.getServer();
                    String main = yarnClass.getMapped() != null ? yarnClass.getMapped() : yarnClass.getIntermediary();
                    return "**MC 1.2.5: " + main + "**\n__Name__: " + obf + " => `" + yarnClass.getIntermediary() + "`" + (yarnClass.getMapped() != null ? " => `" + yarnClass.getMapped() + "`" : "");
                }).collect(Collectors.joining("\n\n"));
                builder.setDescription(desc);
                message1.edit(builder);
            } catch (Throwable throwable1) {
                message1.edit(new EmbedBuilder().setTitle("Linkie Error").setColor(Color.red).setFooter("Requested by " + event.getMessageAuthor().getDiscriminatedName(), event.getMessageAuthor().getAvatar()).addField("Error occurred while processing the command:", throwable1.getClass().getSimpleName() + ": " + throwable1.getLocalizedMessage()).setTimestampToNow());
            }
        });
    }
    
    public String get(YarnClass yarnClass, String search) {
        String intermediary = getLast(yarnClass.getIntermediary());
        if (intermediary.contains(search))
            return intermediary;
        if (yarnClass.getMapped() != null) {
            String mapped = getLast(yarnClass.getMapped()).toLowerCase(Locale.ROOT);
            if (mapped.contains(search))
                return mapped;
        }
        if (yarnClass.getServer() != null) {
            String mapped = getLast(yarnClass.getMapped()).toLowerCase(Locale.ROOT);
            if (mapped.contains(search))
                return mapped;
        }
        if (yarnClass.getClient() != null) {
            String mapped = getLast(yarnClass.getMapped()).toLowerCase(Locale.ROOT);
            if (mapped.contains(search))
                return mapped;
        }
        return "jdkwjidhwudhuwihduhudwuhiwuhui";
    }
    
    public int editDistance(String s1, String s2) {
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();
        
        int[] costs = new int[s2.length() + 1];
        for(int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for(int j = 0; j <= s2.length(); j++) {
                if (i == 0)
                    costs[j] = j;
                else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1))
                            newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0)
                costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }
    
    public String getLast(String s) {
        String[] s1Split = s.split("/");
        return s1Split[s1Split.length - 1];
    }
    
    public double similarity(String s1, String s2) {
        s1 = getLast(s1);
        s2 = getLast(s2);
        String longer = s1, shorter = s2;
        if (s1.length() < s2.length()) { // longer should always have greater length
            longer = s2;
            shorter = s1;
        }
        int longerLength = longer.length();
        if (longerLength == 0) {
            return 1.0; /* both strings are zero length */
        }
        return (longerLength - editDistance(longer, shorter)) / (double) longerLength;
    }
}
