package me.shedaniel.linkie;

import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.event.message.MessageCreateEvent;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;

public interface CommandBase {
    
    void execute(ScheduledExecutorService service, MessageCreateEvent event, MessageAuthor author, String cmd, String[] args)
            throws ExecutionException, InterruptedException;
    
}
