package me.shedaniel.linkie.commands;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.reaction.ReactionEmoji;
import me.shedaniel.cursemetaapi.CurseMetaAPI;
import me.shedaniel.linkie.CommandBase;
import me.shedaniel.linkie.InvalidUsageException;
import me.shedaniel.linkie.LinkieDiscordKt;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

public class FabricApiVersionCommand implements CommandBase {
    
    public static final int ITEMS_PER_PAGE = 24;
    public static final float ITEMS_PER_PAGE_F = ITEMS_PER_PAGE;
    
    public static Integer parseIntOrNull(String s) {
        try {
            return Integer.parseInt(s);
        } catch (RuntimeException e) {
            return null;
        }
    }
    
    @Override
    public void execute(ScheduledExecutorService service, MessageCreateEvent event, Member author, String cmd, String[] args, MessageChannel channel)
            throws ExecutionException, InterruptedException {
        if (args.length > 2)
            throw new InvalidUsageException("+" + cmd + "[page] [-r]");
        int page = args.length == 0 ? 0 : (parseIntOrNull(args[0]) == null && args[0].equalsIgnoreCase("-r") ? 0 : parseIntOrNull(args[0]) - 1);
        boolean showReleaseOnly = (args.length == 2 && args[1].equalsIgnoreCase("-r")) || (args.length == 1 && args[0].equalsIgnoreCase("-r"));
        if (page < 0)
            throw new IllegalArgumentException("The minimum page is 1!");
        List<CurseMetaAPI.AddonFile> files = CurseMetaAPI.getAddonFiles(306612);
        files.sort(Comparator.comparingInt(value -> value.fileId));
        Collections.reverse(files);
        Map<String, CurseMetaAPI.AddonFile> map = new LinkedHashMap<>();
        for(CurseMetaAPI.AddonFile file : files) {
            String displayName = file.displayName;
            if (displayName.charAt(0) == '[' && displayName.indexOf(']') > -1) {
                String version = displayName.substring(1).split("]")[0];
                if (showReleaseOnly && (version.toLowerCase().contains("pre") || version.toLowerCase().startsWith("19w") || version.toLowerCase().startsWith("20w") || version.toLowerCase().startsWith("18w") || version.toLowerCase().startsWith("21w")))
                    continue;
                if (!map.containsKey(version))
                    map.put(version, file);
            }
        }
        List<String> old = new ArrayList<>(map.keySet());
        if (page > old.size() / ITEMS_PER_PAGE_F)
            throw new IllegalArgumentException("The maximum page is " + ((int) Math.ceil(old.size() / ITEMS_PER_PAGE_F) + 1) + "!");
        int cp[] = {page};
        channel.createEmbed(emd -> {
            emd.setTitle("Fabric API Versions");
            emd.setFooter("Page " + (cp[0] + 1) + "/" + ((int) Math.ceil(old.size() / ITEMS_PER_PAGE_F)) + ". Requested by " + author.getUsername() + "#" + author.getDiscriminator(), author.getAvatarUrl());
            emd.setTimestamp(Instant.now());
            map.keySet().stream().skip(ITEMS_PER_PAGE * cp[0]).limit(ITEMS_PER_PAGE).collect(Collectors.toList()).forEach(version -> {
                CurseMetaAPI.AddonFile file = map.get(version);
                emd.addField(version, file.fileName.replaceFirst("fabric-api-", "").replaceFirst("fabric-", "").replace(".jar", ""), true);
            });
            if (!showReleaseOnly)
                emd.setDescription("Tips: Use -r for release only.");
        }).subscribe(msg -> {
            int finalPage[] = {cp[0]};
            if (channel.getType().name().startsWith("GUILD_"))
                msg.removeAllReactions().block();
            msg.addReaction(ReactionEmoji.unicode("⬅")).subscribe();
            msg.addReaction(ReactionEmoji.unicode("❌")).subscribe();
            msg.addReaction(ReactionEmoji.unicode("➡")).subscribe();
            LinkieDiscordKt.getApi().getEventDispatcher().on(ReactionAddEvent.class).filter(e -> e.getMessageId().equals(msg.getId())).take(Duration.ofMinutes(30)).subscribe(reactionAddEvent -> {
                if (reactionAddEvent.getUserId().equals(LinkieDiscordKt.getApi().getSelfId().get())) {
                
                } else if (reactionAddEvent.getUserId().equals(author.getId())) {
                    if (!reactionAddEvent.getEmoji().asUnicodeEmoji().isPresent()) {
                        msg.removeReaction(reactionAddEvent.getEmoji(), reactionAddEvent.getUserId()).subscribe();
                    } else {
                        ReactionEmoji.Unicode unicode = reactionAddEvent.getEmoji().asUnicodeEmoji().get();
                        if (unicode.getRaw().equals("❌")) {
                            msg.delete().subscribe();
                        } else if (unicode.getRaw().equals("⬅")) {
                            msg.removeReaction(reactionAddEvent.getEmoji(), reactionAddEvent.getUserId()).subscribe();
                            if (finalPage[0] > 0) {
                                finalPage[0]--;
                                msg.edit(spec -> spec.setEmbed(emd -> {
                                    emd.setTitle("Fabric API Versions");
                                    emd.setFooter("Page " + (finalPage[0] + 1) + "/" + ((int) Math.ceil(old.size() / ITEMS_PER_PAGE_F)) + ". Requested by " + author.getUsername() + "#" + author.getDiscriminator(), author.getAvatarUrl());
                                    emd.setTimestamp(Instant.now());
                                    map.keySet().stream().skip(ITEMS_PER_PAGE * finalPage[0]).limit(ITEMS_PER_PAGE).collect(Collectors.toList()).forEach(version -> {
                                        CurseMetaAPI.AddonFile file = map.get(version);
                                        emd.addField(version, file.fileName.replaceFirst("fabric-api-", "").replaceFirst("fabric-", "").replace(".jar", ""), true);
                                    });
                                })).subscribe();
                            }
                        } else if (unicode.getRaw().equals("➡")) {
                            msg.removeReaction(reactionAddEvent.getEmoji(), reactionAddEvent.getUserId()).subscribe();
                            if (finalPage[0] < ((int) Math.ceil(old.size() / ITEMS_PER_PAGE_F)) - 1) {
                                finalPage[0]++;
                                msg.edit(spec -> spec.setEmbed(emd -> {
                                    emd.setTitle("Fabric API Versions");
                                    emd.setFooter("Page " + (finalPage[0] + 1) + "/" + ((int) Math.ceil(old.size() / ITEMS_PER_PAGE_F)) + ". Requested by " + author.getUsername() + "#" + author.getDiscriminator(), author.getAvatarUrl());
                                    emd.setTimestamp(Instant.now());
                                    map.keySet().stream().skip(ITEMS_PER_PAGE * finalPage[0]).limit(ITEMS_PER_PAGE).collect(Collectors.toList()).forEach(version -> {
                                        CurseMetaAPI.AddonFile file = map.get(version);
                                        emd.addField(version, file.fileName.replaceFirst("fabric-api-", "").replaceFirst("fabric-", "").replace(".jar", ""), true);
                                    });
                                })).subscribe();
                            }
                        } else {
                            msg.removeReaction(reactionAddEvent.getEmoji(), reactionAddEvent.getUserId()).subscribe();
                        }
                    }
                } else {
                    msg.removeReaction(reactionAddEvent.getEmoji(), reactionAddEvent.getUserId()).subscribe();
                }
            });
        });
    }
}
