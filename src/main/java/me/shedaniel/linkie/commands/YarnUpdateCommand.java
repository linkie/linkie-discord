package me.shedaniel.linkie.commands;

import me.shedaniel.linkie.CommandBase;
import me.shedaniel.linkie.InvalidPermissionException;
import me.shedaniel.linkie.yarn.YarnManager;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;

import java.awt.*;
import java.util.concurrent.ScheduledExecutorService;

public class YarnUpdateCommand implements CommandBase {
    @Override
    public void execute(ScheduledExecutorService service, MessageCreateEvent event, MessageAuthor author, String cmd, String[] args) {
        if (!author.isBotOwner())
            throw new InvalidPermissionException();
        event.getChannel().sendMessage(new EmbedBuilder().setTitle("Updating Yarn...").setFooter("Requested by " + author.getDiscriminatedName(), author.getAvatar()).setTimestampToNow()).whenComplete((message, throwable) -> {
            YarnManager.updateYarn((time, e) -> {
                if (e != null)
                    message.edit(new EmbedBuilder().setTitle("Linkie Error").setColor(Color.red).setFooter("Requested by " + author.getDiscriminatedName(), author.getAvatar()).addField("Error occurred while updating yarn:", e.getClass().getSimpleName() + ": " + e.getLocalizedMessage()).setTimestampToNow());
                else {
                    message.edit(new EmbedBuilder().setTitle("Updated Yarn").addField("Duration Taken", time + "ms").setFooter("Requested by " + author.getDiscriminatedName(), author.getAvatar()).setTimestampToNow());
                }
            });
        });
    }
}
