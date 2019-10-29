package me.shedaniel.linkie;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.MessageChannel;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;

public interface CommandBase {
    
    void execute(ScheduledExecutorService service, MessageCreateEvent event, Member author, String cmd, String[] args, MessageChannel channel)
            throws ExecutionException, InterruptedException;
    
}
