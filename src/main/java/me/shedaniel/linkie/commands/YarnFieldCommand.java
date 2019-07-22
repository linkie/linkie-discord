package me.shedaniel.linkie.commands;

import me.shedaniel.linkie.CommandBase;
import me.shedaniel.linkie.InvalidUsageException;
import me.shedaniel.linkie.yarn.MappingsData;
import me.shedaniel.linkie.yarn.YarnField;
import me.shedaniel.linkie.yarn.YarnManager;
import net.fabricmc.mappings.EntryTriple;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

public class YarnFieldCommand implements CommandBase {
    @Override
    public void execute(ScheduledExecutorService service, MessageCreateEvent event, MessageAuthor author, String cmd, String[] args) {
        if (YarnManager.updating)
            throw new RuntimeException("Yarn is being downloaded, please wait for around 10 seconds");
        if (args.length != 1)
            throw new InvalidUsageException("+" + cmd + " [search term]");
        event.getChannel().sendMessage(new EmbedBuilder().setTitle("Loading...").setFooter("Requested by " + author.getDiscriminatedName(), author.getAvatar()).setTimestampToNow()).whenComplete((message1, throwable) -> {
            try {
                String name = args[0].indexOf('.') > -1 ? args[0].split("\\.")[1] : args[0];
                String clazz = args[0].indexOf('.') > -1 ? args[0].split("\\.")[0] : null;
                String low = name.toLowerCase(Locale.ROOT);
                String lowClazz = clazz != null ? clazz.toLowerCase(Locale.ROOT) : null;
                List<YarnField> files = new ArrayList<>();
                YarnManager.r1_2_5.tiny.getFieldEntries().forEach(fieldEntry -> {
                    EntryTriple intermediary = fieldEntry.get("intermediary");
                    EntryTriple server = fieldEntry.get("server");
                    EntryTriple client = fieldEntry.get("client");
                    if (clazz == null || intermediary.getOwner().toLowerCase(Locale.ROOT).contains(lowClazz) || (server != null && server.getOwner().toLowerCase(Locale.ROOT).contains(lowClazz)) || (client != null && client.getOwner().toLowerCase(Locale.ROOT).contains(lowClazz))) {
                        if (getLast(intermediary.getName()).toLowerCase(Locale.ROOT).contains(low) || (server != null && server.getName().contains(low)) || (client != null && client.getName().contains(low)))
                            files.add(new YarnField(intermediary, server, client));
                    }
                });
                YarnManager.r1_2_5.mappings.forEach(mappingsFile -> {
                    boolean matchClass = clazz == null || mappingsFile.getObfClass().toLowerCase(Locale.ROOT).contains(lowClazz) || mappingsFile.getDeobfClass().toLowerCase(Locale.ROOT).contains(lowClazz);
                    if (matchClass)
                        for(MappingsData.FieldMappings fieldMapping : mappingsFile.getFieldMappings())
                            if (fieldMapping.getObfName().toLowerCase(Locale.ROOT).contains(low) || fieldMapping.getDeobfName().toLowerCase(Locale.ROOT).contains(low))
                                files.add(new YarnField(new EntryTriple(mappingsFile.getObfClass(), fieldMapping.getObfName(), fieldMapping.getDesc()), new EntryTriple(mappingsFile.getDeobfClass(), fieldMapping.getDeobfName(), fieldMapping.getDesc())));
                });
                files.forEach(yarnField -> {
                    if (yarnField.incomplete())
                        if (yarnField.needObf()) {
                            YarnManager.r1_2_5.tiny.getFieldEntries().stream().filter(fieldEntry -> {
                                return getLast(fieldEntry.get("intermediary").getName()).equalsIgnoreCase(getLast(yarnField.getIntermediary().getName()));
                            }).findAny().ifPresent(classEntry -> {
                                yarnField.setClient(classEntry.get("client"));
                                yarnField.setServer(classEntry.get("server"));
                            });
                        } else if (yarnField.needMapped()) {
                            for(MappingsData.MappingsFile file : YarnManager.r1_2_5.mappings.stream().filter(mappingsFile -> {
                                return getLast(mappingsFile.getObfClass()).equalsIgnoreCase(getLast(yarnField.getIntermediary().getOwner()));
                            }).collect(Collectors.toList())) {
                                for(MappingsData.FieldMappings fieldMapping : file.getFieldMappings()) {
                                    if (fieldMapping.getObfName().equalsIgnoreCase(yarnField.getIntermediary().getName())) {
                                        yarnField.setMapped(new EntryTriple(file.getDeobfClass(), fieldMapping.getDeobfName(), fieldMapping.getDesc()));
                                        break;
                                    }
                                }
                            }
                            //                            YarnManager.r1_2_5.mappings.stream().filter(mappingsFile -> {
                            //                                return getLast(mappingsFile.getObfClass()).equalsIgnoreCase(getLast(yarnClass.getIntermediary()));
                            //                            }).findAny().ifPresent(mappingsFile -> yarnClass.setMapped(mappingsFile.getDeobfClass()));
                        }
                });
                if (files.isEmpty())
                    throw new NullPointerException("null");
                files.sort(Comparator.comparingDouble(value -> similarity(get(value, getLast(low)), getLast(low))));
                Collections.reverse(files);
                EmbedBuilder builder = new EmbedBuilder().setTitle("List of Yarn Mappings").setFooter("Requested by " + author.getDiscriminatedName(), author.getAvatar()).setTimestampToNow();
                String desc = files.stream().limit(7).map(yarnField -> {
                    if (yarnField.needObf())
                        return null;
                    String obf = "client=" + (yarnField.getClient() == null ? "null" : yarnField.getClient().getName()) + ",server=" + (yarnField.getServer() == null ? "null" : yarnField.getServer().getName());
                    String main = yarnField.getMapped() != null ? yarnField.getMapped().getOwner() + "." + yarnField.getMapped().getName() : yarnField.getIntermediary().getOwner() + "." + yarnField.getIntermediary().getName();
                    return "**MC 1.2.5: " + main + "**\n__Name__: " + obf + " => `" + yarnField.getIntermediary().getName() + "`" + (yarnField.getMapped() != null ? " => `" + yarnField.getMapped().getName() + "`" : "") + "\n__Type__: `" + nameType(mapDesc(yarnField.getIntermediary().getDesc())).replace('/', '.') + "`\n__Mixin Target__: `" + turnDesc(yarnField.getMapped() != null ? yarnField.getMapped().getOwner() : yarnField.getIntermediary().getOwner()) + (yarnField.getMapped() != null ? yarnField.getMapped().getName() : yarnField.getIntermediary().getName()) + ":" + mapDesc(yarnField.getIntermediary().getDesc()) + "`";
                }).filter(Objects::nonNull).collect(Collectors.joining("\n\n"));
                builder.setDescription(desc.substring(0, Math.min(desc.length(), 2000)));
                message1.edit(builder);
            } catch (Throwable throwable1) {
                throwable1.printStackTrace();
                message1.edit(new EmbedBuilder().setTitle("Linkie Error").setColor(Color.red).setFooter("Requested by " + event.getMessageAuthor().getDiscriminatedName(), event.getMessageAuthor().getAvatar()).addField("Error occurred while processing the command:", throwable1.getClass().getSimpleName() + ": " + throwable1.getLocalizedMessage()).setTimestampToNow());
            }
        });
    }
    
    private String turnDesc(String s) {
        return "L" + s + ";";
    }
    
    private String nameType(String desc) {
        if (desc.length() == 1 || (desc.length() == 2 && desc.charAt(0) == '[')) {
            char c = desc.length() == 1 ? desc.charAt(0) : desc.charAt(1);
            switch (c) {
                case 'Z':
                    return "boolean";
                case 'C':
                    return "char";
                case 'B':
                    return "byte";
                case 'S':
                    return "short";
                case 'I':
                    return "int";
                case 'F':
                    return "float";
                case 'J':
                    return "long";
                case 'D':
                    return "double";
            }
        }
        if (desc.startsWith("L"))
            return desc.substring(1, desc.length() - 1);
        if (desc.startsWith("[[L"))
            return desc.substring(3, desc.length() - 1);
        return desc;
    }
    
    private String mapDesc(String desc) {
        if (desc.startsWith("L")) {
            String substring = desc.substring(1, desc.length() - 1);
            Optional<MappingsData.MappingsFile> any = YarnManager.r1_2_5.mappings.stream().filter(mappingsFile -> mappingsFile.getObfClass().equals(substring)).findAny();
            if (any.isPresent())
                return "L" + any.get().getDeobfClass() + ";";
        }
        if (desc.startsWith("[[L")) {
            String substring = desc.substring(3, desc.length() - 1);
            Optional<MappingsData.MappingsFile> any = YarnManager.r1_2_5.mappings.stream().filter(mappingsFile -> mappingsFile.getObfClass().equals(substring)).findAny();
            if (any.isPresent())
                return "[[L" + any.get().getDeobfClass() + ";";
        }
        return desc;
    }
    
    public String get(YarnField yarnClass, String search) {
        String intermediary = getLast(yarnClass.getIntermediary().getName());
        if (intermediary.contains(search))
            return intermediary;
        if (yarnClass.getMapped() != null) {
            String mapped = getLast(yarnClass.getMapped().getName()).toLowerCase(Locale.ROOT);
            if (mapped.contains(search))
                return mapped;
        }
        if (yarnClass.getServer() != null) {
            String mapped = getLast(yarnClass.getServer().getName()).toLowerCase(Locale.ROOT);
            if (mapped.contains(search))
                return mapped;
        }
        if (yarnClass.getClient() != null) {
            String mapped = getLast(yarnClass.getClient().getName()).toLowerCase(Locale.ROOT);
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
        if (!s.contains("/"))
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
