package me.shedaniel.linkie.commands;

import me.shedaniel.linkie.CommandBase;
import me.shedaniel.linkie.InvalidUsageException;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;

import java.util.concurrent.ScheduledExecutorService;

public class HelpCommand implements CommandBase {
    @Override
    public void execute(ScheduledExecutorService service, MessageCreateEvent event, MessageAuthor author, String cmd, String[] args) {
        if (args.length != 0)
            throw new InvalidUsageException("+" + cmd);
        event.getChannel().sendMessage(new EmbedBuilder()
                .setTitle("Linkie Help Command")
                .setFooter("Requested by " + author.getDiscriminatedName(), author.getAvatar())
                .addField("Help Command", "Value: +help, +?, +commands\nDisplays this message.")
                .addField("UserInfo Command", "Value: +userinfo, +info, +user, +whois, +who\nDisplays user information.")
                .addField("CurseForge4J CheckStats Command", "Value: +os\nCheck CurseForge mod stats.")
                .addField("CurseMetaApi CheckStats Command", "Value: +ns\nCheck CurseForge mod stats.")
                .addField("Fabric Api Versions Command", "Value: +fabricapi\nCheck Fabric API versions for every mc version.")
                .addField("Yarn Class Command", "Value: +yc\nCheck yarn 1.2.5 mappings.")
                .addField("Yarn Field Command", "Value: +yf\nCheck yarn 1.2.5 mappings.")
                .setTimestampToNow()
        );
    }
}
