package me.shedaniel.linkie.commands;

import me.shedaniel.linkie.CommandBase;
import me.shedaniel.linkie.InvalidUsageException;
import me.shedaniel.linkie.LinkieBot;
import me.shedaniel.linkie.yarn.YarnManager;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.concurrent.ScheduledExecutorService;

public class YarnVersionCommand implements CommandBase {
    @Override
    public void execute(ScheduledExecutorService service, MessageCreateEvent event, MessageAuthor author, String cmd, String[] args) {
        if (args.length != 0)
            throw new InvalidUsageException("+" + cmd);
        String prefix = LinkieBot.getInstance().commandApi.getPrefix(!event.isServerMessage() || (event.getServer().isPresent() && event.getServer().get().getId() != 432055962233470986l));
        event.getChannel().sendMessage(new EmbedBuilder()
                .setTitle("Mappings for MC 1.2.5")
                .setFooter("Requested by " + author.getDiscriminatedName(), author.getAvatar())
                .setDescription("We don't have a build versioning thing lol why is this a thing?")
                .addInlineField("Last Fetch Time (UTC)", DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(YarnManager.lastUpdate), ZoneOffset.UTC)))
                .addInlineField("Next Fetch Time (UTC)", DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(YarnManager.nextUpdate), ZoneOffset.UTC)))
                .setTimestampToNow()
        );
    }
}
