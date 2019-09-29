package me.shedaniel.linkie.commands;

import me.shedaniel.cursemetaapi.CurseMetaAPI;
import me.shedaniel.linkie.CommandBase;
import me.shedaniel.linkie.InvalidUsageException;
import me.shedaniel.linkie.LinkieBot;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class FabricApiVersionCommand implements CommandBase {
    @Override
    public void execute(ScheduledExecutorService service, MessageCreateEvent event, MessageAuthor author, String cmd, String[] args)
            throws ExecutionException, InterruptedException {
        if (args.length > 1)
            throw new InvalidUsageException("+" + cmd + "[page]");
        int page = args.length == 0 ? 0 : Integer.parseInt(args[0]) - 1;
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
                if (!map.containsKey(version))
                    map.put(version, file);
            }
        }
        List<String> versions = new ArrayList<>(map.keySet());
        List<String> old = new ArrayList<>(versions);
        if (page > old.size() / 25f)
            throw new IllegalArgumentException("The maximum page is " + ((int) Math.ceil(old.size() / 25f) + 1) + "!");
        versions = versions.stream().skip(25 * page).collect(Collectors.toList());
        EmbedBuilder embedBuilder = new EmbedBuilder().setTitle("Fabric API Versions").setFooter("Page " + (page + 1) + "/" + ((int) Math.ceil(old.size() / 25f)) + ". Requested by " + author.getDiscriminatedName(), author.getAvatar()).setTimestampToNow();
        versions.forEach(version -> {
            CurseMetaAPI.AddonFile file = map.get(version);
            embedBuilder.addInlineField(version, file.fileName.replaceFirst("fabric-api-", "").replaceFirst("fabric-", "").replace(".jar", ""));
        });
        page += 0;
        int finalPage[] = {page};
//        embedBuilder.setDescription("Use +" + cmd + " [page] for more pages");
        Message message = event.getChannel().sendMessage(embedBuilder).get();
        if (message.isServerMessage())
            message.removeAllReactions().get();
        message.addReactions("⬅", "❌", "➡").thenRun(() -> {
            message.addReactionAddListener(reactionAddEvent -> {
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
                                System.out.println(map.keySet().stream().skip(25 * finalPage[0]).count());
                                EmbedBuilder embedBuilder1 = new EmbedBuilder().setTitle("Fabric API Versions").setFooter("Page " + (finalPage[0] + 1) + "/" + ((int) Math.ceil(old.size() / 25f)) + ". Requested by " + author.getDiscriminatedName(), author.getAvatar()).setTimestampToNow();
                                map.keySet().stream().skip(25 * finalPage[0]).forEach(version -> {
                                    CurseMetaAPI.AddonFile file = map.get(version);
                                    embedBuilder.addInlineField(version, file.fileName.replaceFirst("fabric-api-", "").replaceFirst("fabric-", "").replace(".jar", ""));
                                });
                                message.edit(embedBuilder1);
                            }
                        } else if (reactionAddEvent.getEmoji().equalsEmoji("➡")) {
                            reactionAddEvent.removeReaction();
                            if (finalPage[0] < ((int) Math.ceil(old.size() / 25f)) - 1) {
                                finalPage[0]++;
                                System.out.println(map.keySet().stream().skip(25 * finalPage[0]).count());
                                EmbedBuilder embedBuilder1 = new EmbedBuilder().setTitle("Fabric API Versions").setFooter("Page " + (finalPage[0] + 1) + "/" + ((int) Math.ceil(old.size() / 25f)) + ". Requested by " + author.getDiscriminatedName(), author.getAvatar()).setTimestampToNow();
                                map.keySet().stream().skip(25 * finalPage[0]).forEach(version -> {
                                    CurseMetaAPI.AddonFile file = map.get(version);
                                    embedBuilder.addInlineField(version, file.fileName.replaceFirst("fabric-api-", "").replaceFirst("fabric-", "").replace(".jar", ""));
                                });
                                message.edit(embedBuilder1);
                            }
                        }
                } catch (Throwable throwable1) {
                    throwable1.printStackTrace();
                }
            }).removeAfter(30, TimeUnit.MINUTES);
        });
    }
}
