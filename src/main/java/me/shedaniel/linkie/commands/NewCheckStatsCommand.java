package me.shedaniel.linkie.commands;

import me.shedaniel.cursemetaapi.CurseMetaAPI;
import me.shedaniel.cursemetaapi.MetaSearch;
import me.shedaniel.linkie.CommandBase;
import me.shedaniel.linkie.InvalidUsageException;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;

import java.awt.*;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

public class NewCheckStatsCommand implements CommandBase {
    
    @Override
    public void execute(ScheduledExecutorService service, MessageCreateEvent event, MessageAuthor author, String cmd, String[] args) {
        if (args.length < 1)
            throw new InvalidUsageException("+" + cmd + " <project>");
        String ss = Arrays.asList(args).stream().collect(Collectors.joining(" "));
        EmbedBuilder builder = new EmbedBuilder().setTitle("Loading project: " + ss).setDescription("Might take a few seconds...").setFooter("Requested by " + author.getDiscriminatedName(), author.getAvatar()).setTimestampToNow();
        CompletableFuture<Message> sendMessage = event.getChannel().sendMessage(builder);
        try {
            String s = ss.toLowerCase(Locale.ROOT);
            CurseMetaAPI.Addon project = null;
            String comment = null;
            boolean search = true;
            if (args.length == 1)
                try {
                    project = CurseMetaAPI.getAddon(Integer.parseInt(s));
                } catch (Exception e) {
                
                }
            if (project != null)
                search = false;
            if (search) {
                sendMessage.whenComplete((message, throwable) -> message.edit(new EmbedBuilder().setTitle("Searching up project: " + ss).setDescription("Might take a while...").setFooter("Requested by " + author.getDiscriminatedName(), author.getAvatar()).setTimestampToNow()));
                List<CurseMetaAPI.Addon> addons = MetaSearch.create(432).setCategoryId(423).setSortingMethod(MetaSearch.SortMethod.TOTAL_DOWNLOADS).setSearchFilter(s).setPageSize(50).search();
                if (addons.isEmpty())
                    throw new NullPointerException("Project not found!");
                addons.sort(Comparator.comparingDouble(value -> similarity(value.name.toLowerCase(Locale.ROOT), s)));
                Collections.reverse(addons);
                project = addons.get(0);
                if (addons.size() > 1)
                    comment = String.format("There were: %s\nBut we chose **%s**.", addons.stream().map(p -> "**" + p.name + "**").collect(Collectors.joining(", ")), project.name);
            }
            EmbedBuilder updated = new EmbedBuilder();
            if (comment != null)
                updated.setDescription(comment);
            updated.setTitle(project.name);
            Optional<CurseMetaAPI.Addon.AddonAttachment> possibleIcon = project.attachments.stream().filter(addonAttachment -> addonAttachment.isDefault).findFirst();
            if (possibleIcon.isPresent())
                updated.setThumbnail(possibleIcon.get().thumbnailUrl);
            updated.setFooter("Requested by " + author.getDiscriminatedName(), author.getAvatar());
            updated.setColor(Color.GREEN);
            updated.addField("Project ID", project.id + "");
            updated.addField("Project Created", project.getDateCreated().toGMTString());
            updated.addField("Last Updated", project.getDateModified().toGMTString());
            double sinceStartAverage = project.downloadCount / ((Instant.now().toEpochMilli() - project.getDateCreated().toInstant().toEpochMilli()) / 1000d / 60d / 60d / 24d);
            updated.addField("Total Downloads", project.downloadCount + "");
            updated.addField("Mean Downloads (Since Start)", String.format("%.2f per day", sinceStartAverage));
            updated.addField("Member(s)", project.authors.stream().map(addonAuthor -> addonAuthor.name).collect(Collectors.joining(", ")));
            updated.setUrl(project.websiteUrl);
            updated.setTimestampToNow();
            sendMessage.thenApply(message -> message.edit(updated));
        } catch (Throwable throwable) {
            sendMessage.thenApply(message -> message.edit(new EmbedBuilder().setTitle("Linkie Error").setColor(Color.red).setFooter("Requested by " + event.getMessageAuthor().getDiscriminatedName(), event.getMessageAuthor().getAvatar()).addField("Error occurred while processing the command:", throwable.getClass().getSimpleName() + ": " + throwable.getLocalizedMessage()).setTimestampToNow()));
        }
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
