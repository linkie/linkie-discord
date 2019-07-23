package me.shedaniel.linkie.commands;

import me.shedaniel.linkie.CommandBase;
import me.shedaniel.linkie.InvalidUsageException;
import me.shedaniel.linkie.LinkieBot;
import me.shedaniel.linkie.yarn.MappingsData;
import me.shedaniel.linkie.yarn.YarnManager;
import me.shedaniel.linkie.yarn.YarnMethod;
import net.fabricmc.mappings.EntryTriple;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static me.shedaniel.linkie.LinkieBot.contains;

public class YarnMethodCommand implements CommandBase {
    @Override
    public void execute(ScheduledExecutorService service, MessageCreateEvent event, MessageAuthor author, String cmd, String[] args) {
        if (YarnManager.updating)
            throw new RuntimeException("Yarn is being downloaded, please wait for around 10 seconds");
        if (args.length != 1 && args.length != 2)
            throw new InvalidUsageException("+" + cmd + " <search> [page]");
        event.getChannel().sendMessage(new EmbedBuilder().setTitle("Loading...").setFooter("Requested by " + author.getDiscriminatedName(), author.getAvatar()).setTimestampToNow()).whenComplete((message1, throwable) -> {
            try {
                int page = args.length > 1 ? Integer.parseInt(args[1]) - 1 : 0;
                String name = args[0].indexOf('.') > -1 ? args[0].split("\\.")[1] : args[0];
                String clazz = args[0].indexOf('.') > -1 ? args[0].split("\\.")[0] : null;
                String low = name.toLowerCase(Locale.ROOT);
                String lowClazz = clazz != null ? clazz.toLowerCase(Locale.ROOT) : null;
                List<YarnMethod> files = new ArrayList<>();
                YarnManager.r1_2_5.tiny.getMethodEntries().forEach(methodEntry -> {
                    EntryTriple intermediary = methodEntry.get("intermediary");
                    EntryTriple server = methodEntry.get("server");
                    EntryTriple client = methodEntry.get("client");
                    if (clazz == null || contains(getLast(intermediary.getOwner().toLowerCase(Locale.ROOT)), lowClazz) || (server != null && contains(getLast(server.getOwner().toLowerCase(Locale.ROOT)), lowClazz)) || (client != null && contains(getLast(client.getOwner().toLowerCase(Locale.ROOT)), lowClazz))) {
                        if (contains(getLast(intermediary.getName()).toLowerCase(Locale.ROOT), low) || (server != null && contains(server.getName(), low)) || (client != null && contains(client.getName(), low)))
                            files.add(new YarnMethod(intermediary, server, client));
                    }
                });
                YarnManager.r1_2_5.mappings.forEach(mappingsFile -> {
                    boolean matchClass = clazz == null || contains(getLast(mappingsFile.getIntermediaryClass().toLowerCase(Locale.ROOT)), lowClazz) || contains(getLast(mappingsFile.getYarnClass().toLowerCase(Locale.ROOT)), lowClazz);
                    if (matchClass)
                        for(MappingsData.MethodMappings methodMapping : mappingsFile.getMethodMappings())
                            if (contains(methodMapping.getIntermediaryName().toLowerCase(Locale.ROOT), low) || contains(methodMapping.getYarnName().toLowerCase(Locale.ROOT), low))
                                files.add(new YarnMethod(new EntryTriple(mappingsFile.getIntermediaryClass(), methodMapping.getIntermediaryName(), methodMapping.getIntermediaryDesc()), new EntryTriple(mappingsFile.getYarnClass(), methodMapping.getYarnName(), methodMapping.getIntermediaryDesc())));
                });
                files.forEach(yarnMethod -> {
                    if (yarnMethod.incomplete())
                        if (yarnMethod.needObf()) {
                            YarnManager.r1_2_5.tiny.getMethodEntries().stream().filter(methodEntry -> {
                                return getLast(methodEntry.get("intermediary").getName()).equalsIgnoreCase(getLast(yarnMethod.getIntermediary().getName()));
                            }).findAny().ifPresent(classEntry -> {
                                yarnMethod.setClient(classEntry.get("client"));
                                yarnMethod.setServer(classEntry.get("server"));
                            });
                        } else if (yarnMethod.needMapped()) {
                            for(MappingsData.MappingsFile file : YarnManager.r1_2_5.mappings.stream().filter(mappingsFile -> {
                                return getLast(mappingsFile.getIntermediaryClass()).equalsIgnoreCase(getLast(yarnMethod.getIntermediary().getOwner()));
                            }).collect(Collectors.toList())) {
                                for(MappingsData.MethodMappings methodMapping : file.getMethodMappings()) {
                                    if (methodMapping.getIntermediaryName().equalsIgnoreCase(yarnMethod.getIntermediary().getName())) {
                                        yarnMethod.setMapped(new EntryTriple(file.getYarnClass(), methodMapping.getYarnName(), methodMapping.getIntermediaryDesc()));
                                        break;
                                    }
                                }
                            }
                        }
                });
                List<YarnMethod> cloneFiles = new ArrayList<>(files);
                files.clear();
                cloneFiles.forEach(yarnMethod -> {
                    if (!files.stream().anyMatch(yarnMethod1 -> yarnMethod1.getIntermediary().getName().equals(yarnMethod.getIntermediary().getName())))
                        files.add(yarnMethod);
                });
                if (files.isEmpty())
                    throw new NullPointerException("Match methods not found!");
                files.sort(Comparator.comparingDouble(value -> similarity(get(value, getLast(low)), getLast(low))));
                Collections.reverse(files);
                EmbedBuilder builder = new EmbedBuilder().setTitle("List of Yarn Mappings (Page " + (page + 1) + "/" + (int) Math.ceil(files.size() / 5d) + ")").setFooter("Requested by " + author.getDiscriminatedName(), author.getAvatar()).setTimestampToNow();
                final String[] desc = {""};
                files.stream().skip(page * 5).limit(5).map(yarnMethod -> {
                    if (yarnMethod.needObf())
                        return null;
                    String obf = "client=" + (yarnMethod.getClient() == null ? "null" : yarnMethod.getClient().getName()) + ",server=" + (yarnMethod.getServer() == null ? "null" : yarnMethod.getServer().getName());
                    String main = yarnMethod.getMapped() != null ? yarnMethod.getMapped().getOwner() + "." + yarnMethod.getMapped().getName() : yarnMethod.getIntermediary().getOwner() + "." + yarnMethod.getIntermediary().getName();
                    String methodDesc = (yarnMethod.getMapped() == null ? yarnMethod.getIntermediary().getName() : yarnMethod.getMapped().getName()) + mapDesc(yarnMethod.getIntermediary().getDesc());
                    return "**MC 1.2.5: " + main + "**\n__Name__: " + obf + " => `" + yarnMethod.getIntermediary().getName() + "`" + (yarnMethod.getMapped() != null ? " => `" + yarnMethod.getMapped().getName() + "`" : "") + "\n__Descriptor__: `" + methodDesc + "`\n__Mixin Target__: `" + turnDesc(yarnMethod.getMapped() != null ? yarnMethod.getMapped().getOwner() : yarnMethod.getIntermediary().getOwner()) + methodDesc + "`";
                }).filter(Objects::nonNull).forEach(s -> {
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
                                        files.stream().skip(finalPage[0] * 5).limit(5).map(yarnMethod -> {
                                            if (yarnMethod.needObf())
                                                return null;
                                            String obf = "client=" + (yarnMethod.getClient() == null ? "null" : yarnMethod.getClient().getName()) + ",server=" + (yarnMethod.getServer() == null ? "null" : yarnMethod.getServer().getName());
                                            String main = yarnMethod.getMapped() != null ? yarnMethod.getMapped().getOwner() + "." + yarnMethod.getMapped().getName() : yarnMethod.getIntermediary().getOwner() + "." + yarnMethod.getIntermediary().getName();
                                            String methodDesc = (yarnMethod.getMapped() == null ? yarnMethod.getIntermediary().getName() : yarnMethod.getMapped().getName()) + mapDesc(yarnMethod.getIntermediary().getDesc());
                                            return "**MC 1.2.5: " + main + "**\n__Name__: " + obf + " => `" + yarnMethod.getIntermediary().getName() + "`" + (yarnMethod.getMapped() != null ? " => `" + yarnMethod.getMapped().getName() + "`" : "") + "\n__Descriptor__: `" + methodDesc + "`\n__Mixin Target__: `" + turnDesc(yarnMethod.getMapped() != null ? yarnMethod.getMapped().getOwner() : yarnMethod.getIntermediary().getOwner()) + methodDesc + "`";
                                        }).filter(Objects::nonNull).forEach(s -> {
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
                                        files.stream().skip(finalPage[0] * 5).limit(5).map(yarnMethod -> {
                                            if (yarnMethod.needObf())
                                                return null;
                                            String obf = "client=" + (yarnMethod.getClient() == null ? "null" : yarnMethod.getClient().getName()) + ",server=" + (yarnMethod.getServer() == null ? "null" : yarnMethod.getServer().getName());
                                            String main = yarnMethod.getMapped() != null ? yarnMethod.getMapped().getOwner() + "." + yarnMethod.getMapped().getName() : yarnMethod.getIntermediary().getOwner() + "." + yarnMethod.getIntermediary().getName();
                                            String methodDesc = (yarnMethod.getMapped() == null ? yarnMethod.getIntermediary().getName() : yarnMethod.getMapped().getName()) + mapDesc(yarnMethod.getIntermediary().getDesc());
                                            return "**MC 1.2.5: " + main + "**\n__Name__: " + obf + " => `" + yarnMethod.getIntermediary().getName() + "`" + (yarnMethod.getMapped() != null ? " => `" + yarnMethod.getMapped().getName() + "`" : "") + "\n__Descriptor__: `" + methodDesc + "`\n__Mixin Target__: `" + turnDesc(yarnMethod.getMapped() != null ? yarnMethod.getMapped().getOwner() : yarnMethod.getIntermediary().getOwner()) + methodDesc + "`";
                                        }).filter(Objects::nonNull).forEach(s -> {
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
                message1.edit(new EmbedBuilder().setTitle("Linkie Error").setColor(Color.red).setFooter("Requested by " + event.getMessageAuthor().getDiscriminatedName(), event.getMessageAuthor().getAvatar()).addField("Error occurred while processing the command:", throwable1.getClass().getSimpleName() + ": " + throwable1.getLocalizedMessage()).setTimestampToNow());
            }
        });
    }
    
    private String turnDesc(String s) {
        return "L" + s + ";";
    }
    
    private String mapDesc(String desc) {
        String[] split = desc.split("\\)");
        if (split.length > 2)
            throw new IllegalStateException("Wrong Description!");
        String s = split[0];
        if (s.indexOf(';') > -1) {
            for(String aClass : s.substring(1).split(";")) {
                int l = aClass.indexOf("L");
                split[0] = split[0].replace(aClass.substring(l + 1), mapClass(aClass.substring(l + 1)));
            }
        }
        split[1] = mapDescClass(split[1]);
        return String.join(")", split);
    }
    
    private String mapDescClass(String clazz) {
        if (clazz.startsWith("L"))
            return "L" + mapClass(clazz.substring(1, clazz.length() - 1)) + ";";
        if (clazz.startsWith("[[L"))
            return "L" + mapClass(clazz.substring(3, clazz.length() - 1)) + ";";
        return mapClass(clazz);
    }
    
    private String mapClass(String clazz) {
        return YarnManager.r1_2_5.mappings.stream().filter(mappingsFile -> mappingsFile.getIntermediaryClass().equals(clazz)).findAny().map(MappingsData.MappingsFile::getYarnClass).orElse(clazz);
    }
    
    public String get(YarnMethod yarnClass, String search) {
        String intermediary = getLast(yarnClass.getIntermediary().getName());
        if (contains(intermediary, search))
            return intermediary;
        if (yarnClass.getMapped() != null) {
            String mapped = getLast(yarnClass.getMapped().getName()).toLowerCase(Locale.ROOT);
            if (contains(mapped, search))
                return mapped;
        }
        if (yarnClass.getServer() != null) {
            String mapped = getLast(yarnClass.getServer().getName()).toLowerCase(Locale.ROOT);
            if (contains(mapped, search))
                return mapped;
        }
        if (yarnClass.getClient() != null) {
            String mapped = getLast(yarnClass.getClient().getName()).toLowerCase(Locale.ROOT);
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
    
    public String getLast(String s) {
        if (s.indexOf('/') <= -1)
            return s;
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
