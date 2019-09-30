package me.shedaniel.linkie.commands;

import me.shedaniel.linkie.CommandBase;
import me.shedaniel.linkie.InvalidUsageException;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.MessageCreateEvent;

import java.awt.*;
import java.sql.Date;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class UserInfoCommand implements CommandBase {
    @Override public void execute(ScheduledExecutorService service, MessageCreateEvent event, MessageAuthor author, String cmd, String[] args) {
        if (args.length != 1)
            throw new InvalidUsageException("+" + cmd + " <user>");
        if (!event.isServerMessage())
            throw new InvalidUsageException("This command can be only main on servers!");
        Server server = event.getServer().orElseThrow(() -> new NullPointerException("Server not found!"));
        String s = args[0];
        long id = -1;
        AtomicReference<User> user = new AtomicReference<>(null);
        if (s.startsWith("<@") && s.endsWith(">"))
            id = Long.parseLong(s.substring(2, s.length() - 1));
        else
            try {
                id = Long.parseLong(s);
            } catch (Exception e) {
            }
        if (id != -1) {
            user.set(server.getMemberById(id).orElseThrow(() -> new NullPointerException("Member not found!")));
        } else {
            server.getMemberByDiscriminatedNameIgnoreCase(s).ifPresent(user1 -> user.set(user1));
            if (user.get() == null)
                server.getMembersByDisplayNameIgnoreCase(s).stream().findFirst().ifPresent(user1 -> user.set(user1));
            if (user.get() == null)
                server.getMembersByNameIgnoreCase(s).stream().findFirst().ifPresent(user1 -> user.set(user1));
            if (user.get() == null)
                server.getMembersByNicknameIgnoreCase(s).stream().findFirst().ifPresent(user1 -> user.set(user1));
            if (user.get() == null)
                throw new NullPointerException("Member not found!");
        }
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle("User Search: " + user.get().getDiscriminatedName())
                .setThumbnail(user.get().getAvatar())
                .setColor(Color.yellow)
                .setFooter("Requested by " + author.getDiscriminatedName(), author.getAvatar())
                .addInlineField("Join Time", user.get().getJoinedAtTimestamp(server).map(instant -> Date.from(instant).toGMTString()).orElse("Error!"))
                .setTimestampToNow();
        List<Role> roles = user.get().getRoles(server).stream().skip(1).collect(Collectors.toList());
        if (roles.isEmpty())
            builder.addField("Roles", "None");
        else builder.addField("Roles", roles.stream().map(role -> {
            return role.getMentionTag();
        }).collect(Collectors.joining(" ")));
        event.getServerTextChannel().orElseThrow(() -> new NullPointerException("Text Channel not found!")).sendMessage(builder);
    }
}
