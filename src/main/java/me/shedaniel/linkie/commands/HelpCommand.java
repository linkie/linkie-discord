package me.shedaniel.linkie.commands;

import me.shedaniel.linkie.CommandBase;
import me.shedaniel.linkie.InvalidUsageException;
import me.shedaniel.linkie.LinkieBot;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;

import java.util.concurrent.ScheduledExecutorService;

public class HelpCommand implements CommandBase {
    @Override
    public void execute(ScheduledExecutorService service, MessageCreateEvent event, MessageAuthor author, String cmd, String[] args) {
        if (args.length != 0)
            throw new InvalidUsageException("+" + cmd);
        String prefix = LinkieBot.getInstance().commandApi.getPrefix(!event.isServerMessage() || (event.getServer().isPresent() && event.getServer().get().getId() != 432055962233470986l));
        event.getChannel().sendMessage(new EmbedBuilder()
                .setTitle("Linkie Help Command")
                .setFooter("Requested by " + author.getDiscriminatedName(), author.getAvatar())
                .addField("Help Command", "Value: " + prefix + "help, " + prefix + "?, " + prefix + "commands\nDisplays this message.")
                .addField("UserInfo Command", "Value: " + prefix + "userinfo, " + prefix + "info, " + prefix + "user, " + prefix + "whois, " + prefix + "who\nDisplays user information.")
                .addField("CurseForge4J CheckStats Command", "Value: " + prefix + "os\nCheck CurseForge mod stats.")
                .addField("CurseMetaApi CheckStats Command", "Value: " + prefix + "ns\nCheck CurseForge mod stats.")
                .addField("Fabric Api Versions Command", "Value: " + prefix + "fabricapi\nCheck Fabric API versions for every mc version.")
                .addField("Yarn Class Command", "Value: " + prefix + "yc\nCheck yarn 1.2.5 mappings.")
                .addField("Yarn Field Command", "Value: " + prefix + "yf\nCheck yarn 1.2.5 mappings.")
                .addField("Yarn Method Command", "Value: " + prefix + "ym\nCheck yarn 1.2.5 mappings.")
                .addField("Yarn Version Command", "Value: " + prefix + "yv\nCheck yarn 1.2.5 mappings last fetch time.")
                .addField("Yarn Update Command", "Value: " + prefix + "yu\nReupdate yarn 1.2.5 mappings. (Can be only used by bot owner)")
                .setTimestampToNow()
        );
    }
}
