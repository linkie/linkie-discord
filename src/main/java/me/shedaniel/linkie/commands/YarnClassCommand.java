package me.shedaniel.linkie.commands;

import me.shedaniel.linkie.CommandBase;
import me.shedaniel.linkie.InvalidUsageException;
import me.shedaniel.linkie.LinkieBot;
import me.shedaniel.linkie.yarn.YarnClass;
import me.shedaniel.linkie.yarn.YarnManager;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static me.shedaniel.linkie.LinkieBot.contains;
import static me.shedaniel.linkie.yarn.YarnManager.getLast;

public class YarnClassCommand implements CommandBase {
    @Override
    public void execute(ScheduledExecutorService service, MessageCreateEvent event, MessageAuthor author, String cmd, String[] args) {
        if (YarnManager.updating)
            throw new RuntimeException("Yarn is being downloaded, please wait for around 10 seconds");
        if (args.length != 1 && args.length != 2)
            throw new InvalidUsageException("+" + cmd + " <search> [page]");
        event.getChannel().sendMessage(new EmbedBuilder().setTitle("Loading...").setFooter("Requested by " + author.getDiscriminatedName(), author.getAvatar()).setTimestampToNow()).whenComplete((message1, throwable) -> {
            try {
                String name = args[0];
                int page = args.length > 1 ? Integer.parseInt(args[1]) - 1 : 0;
                String low = name.toLowerCase(Locale.ROOT);
                List<YarnClass> files = new ArrayList<>();
                YarnManager.r1_2_5.tiny.getClassEntries().forEach(classEntry -> {
                    String intermediary = classEntry.get("intermediary");
                    String server = classEntry.get("server");
                    String client = classEntry.get("client");
                    if (contains(getLast(intermediary).toLowerCase(Locale.ROOT), low) || (server != null && contains(server, low)) || (client != null && contains(client, low))) {
                        files.add(new YarnClass(intermediary, server, client));
                    }
                });
                YarnManager.r1_2_5.mappings.forEach(mappingsFile -> {
                    if (!files.stream().anyMatch(yarnClass -> getLast(yarnClass.getIntermediary()).equalsIgnoreCase(getLast(mappingsFile.getIntermediaryClass()))))
                        if (contains(getLast(mappingsFile.getIntermediaryClass()).toLowerCase(Locale.ROOT), low) || contains(getLast(mappingsFile.getYarnClass()).toLowerCase(Locale.ROOT), low))
                            files.add(new YarnClass(mappingsFile.getIntermediaryClass(), mappingsFile.getYarnClass()));
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
                            YarnManager.mapClass(yarnClass.getIntermediary(), YarnManager.r1_2_5).ifPresent(s -> {
                                yarnClass.setMapped(s);
                            });
                        }
                });
                if (files.isEmpty())
                    throw new NullPointerException("Match classes not found!");
                files.sort(Comparator.comparingDouble(value -> similarity(get(value, getLast(low)), getLast(low))));
                Collections.reverse(files);
                EmbedBuilder builder = new EmbedBuilder().setTitle("List of Yarn Mappings (Page " + (page + 1) + "/" + (int) Math.ceil(files.size() / 5d) + ")").setFooter("Requested by " + author.getDiscriminatedName(), author.getAvatar()).setTimestampToNow();
                final String[] desc = {""};
                files.stream().skip(5 * page).limit(5).map(yarnClass -> {
                    String obf = yarnClass.getClient() == null && yarnClass.getServer() == null ? null : "client=" + yarnClass.getClient() + ",server=" + yarnClass.getServer();
                    String main = yarnClass.getMapped() != null ? yarnClass.getMapped() : yarnClass.getIntermediary();
                    return "**MC 1.2.5: " + main + "**\n__Name__: " + (obf == null ? "" : obf + " => ") + "`" + yarnClass.getIntermediary() + "`" + (yarnClass.getMapped() != null && !yarnClass.getIntermediary().equals(yarnClass.getMapped()) ? " => `" + yarnClass.getMapped() + "`" : "");
                }).forEach(s -> {
                    if (desc[0].length() + s.length() > 1990)
                        return;
                    if (!desc[0].isEmpty())
                        desc[0] += "\n\n";
                    desc[0] += s;
                });
                builder.setDescription(desc[0].substring(0, Math.min(desc[0].length(), 2000)));
                message1.edit(builder);
                int finalPage[] = {page};
                if (message1.isServerMessage())
                    message1.removeAllReactions().get();
                message1.addReactions("⬅", "❌", "➡").thenRun(() -> {
                    message1.addReactionAddListener(reactionAddEvent -> {
                        try {
                            if (!reactionAddEvent.getUser().getDiscriminatedName().equals(LinkieBot.getApi().getYourself().getDiscriminatedName()) && !reactionAddEvent.getUser().getDiscriminatedName().equals(author.getDiscriminatedName())) {
                                reactionAddEvent.removeReaction();
                                return;
                            }
                            if (reactionAddEvent.getUser().getDiscriminatedName().equals(author.getDiscriminatedName()))
                                if (reactionAddEvent.getEmoji().equalsEmoji("❌")) {
                                    reactionAddEvent.deleteMessage();
                                } else if (reactionAddEvent.getEmoji().equalsEmoji("⬅")) {
                                    reactionAddEvent.removeReaction();
                                    if (finalPage[0] > 0) {
                                        finalPage[0]--;
                                        EmbedBuilder builder1 = new EmbedBuilder().setTitle("List of Yarn Mappings (Page " + (finalPage[0] + 1) + "/" + (int) Math.ceil(files.size() / 5d) + ")").setFooter("Requested by " + author.getDiscriminatedName(), author.getAvatar()).setTimestampToNow();
                                        final String[] desc1 = {""};
                                        files.stream().skip(5 * finalPage[0]).limit(5).map(yarnClass -> {
                                            String obf = yarnClass.getClient() == null && yarnClass.getServer() == null ? null : "client=" + yarnClass.getClient() + ",server=" + yarnClass.getServer();
                                            String main = yarnClass.getMapped() != null ? yarnClass.getMapped() : yarnClass.getIntermediary();
                                            return "**MC 1.2.5: " + main + "**\n__Name__: " + (obf == null ? "" : obf + " => ") + "`" + yarnClass.getIntermediary() + "`" + (yarnClass.getMapped() != null ? " => `" + yarnClass.getMapped() + "`" : "");
                                        }).forEach(s -> {
                                            if (desc1[0].length() + s.length() > 1990)
                                                return;
                                            if (!desc1[0].isEmpty())
                                                desc1[0] += "\n\n";
                                            desc1[0] += s;
                                        });
                                        builder1.setDescription(desc1[0].substring(0, Math.min(desc1[0].length(), 2000)));
                                        message1.edit(builder1);
                                    }
                                } else if (reactionAddEvent.getEmoji().equalsEmoji("➡")) {
                                    reactionAddEvent.removeReaction();
                                    if (finalPage[0] < (int) Math.ceil(files.size() / 5d) - 1) {
                                        finalPage[0]++;
                                        EmbedBuilder builder1 = new EmbedBuilder().setTitle("List of Yarn Mappings (Page " + (finalPage[0] + 1) + "/" + (int) Math.ceil(files.size() / 5d) + ")").setFooter("Requested by " + author.getDiscriminatedName(), author.getAvatar()).setTimestampToNow();
                                        final String[] desc1 = {""};
                                        files.stream().skip(5 * finalPage[0]).limit(5).map(yarnClass -> {
                                            String obf = yarnClass.getClient() == null && yarnClass.getServer() == null ? null : "client=" + yarnClass.getClient() + ",server=" + yarnClass.getServer();
                                            String main = yarnClass.getMapped() != null ? yarnClass.getMapped() : yarnClass.getIntermediary();
                                            return "**MC 1.2.5: " + main + "**\n__Name__: " + (obf == null ? "" : obf + " => ") + "`" + yarnClass.getIntermediary() + "`" + (yarnClass.getMapped() != null ? " => `" + yarnClass.getMapped() + "`" : "");
                                        }).forEach(s -> {
                                            if (desc1[0].length() + s.length() > 1990)
                                                return;
                                            if (!desc1[0].isEmpty())
                                                desc1[0] += "\n\n";
                                            desc1[0] += s;
                                        });
                                        builder1.setDescription(desc1[0].substring(0, Math.min(desc1[0].length(), 2000)));
                                        message1.edit(builder1);
                                    }
                                }
                        } catch (Throwable throwable1) {
                            throwable1.printStackTrace();
                        }
                    }).removeAfter(30, TimeUnit.MINUTES);
                });
            } catch (Throwable throwable1) {
                message1.edit(new EmbedBuilder().setTitle("Linkie Error").setColor(Color.red).setFooter("Requested by " + author.getDiscriminatedName(), author.getAvatar()).addField("Error occurred while processing the command:", throwable1.getClass().getSimpleName() + ": " + throwable1.getLocalizedMessage()).setTimestampToNow());
            }
        });
    }
    
    public String get(YarnClass yarnClass, String search) {
        String intermediary = getLast(yarnClass.getIntermediary());
        if (contains(intermediary, search))
            return intermediary;
        if (yarnClass.getMapped() != null) {
            String mapped = getLast(yarnClass.getMapped()).toLowerCase(Locale.ROOT);
            if (contains(mapped, search))
                return mapped;
        }
        if (yarnClass.getServer() != null) {
            String mapped = getLast(yarnClass.getServer()).toLowerCase(Locale.ROOT);
            if (contains(mapped, search))
                return mapped;
        }
        if (yarnClass.getClient() != null) {
            String mapped = getLast(yarnClass.getClient()).toLowerCase(Locale.ROOT);
            if (contains(mapped, search))
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
