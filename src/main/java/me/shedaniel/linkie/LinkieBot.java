package me.shedaniel.linkie;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.shedaniel.linkie.spring.LinkieSpringApplication;
import me.shedaniel.linkie.spring.LoadMeta;
import org.springframework.boot.SpringApplication;

import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class LinkieBot {
    
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final ScheduledExecutorService executors = Executors.newScheduledThreadPool(16);
    public ScheduledExecutorService sendUpdate = Executors.newSingleThreadScheduledExecutor();
    public ScheduledExecutorService updateGitHub = Executors.newScheduledThreadPool(16);
    public ScheduledExecutorService yarn = Executors.newScheduledThreadPool(16);
    public ScheduledExecutorService singleThreadExecutor = Executors.newSingleThreadScheduledExecutor();
    
    public static void main(String[] args) {
        LoadMeta.load();
        SpringApplication.run(LinkieSpringApplication.class, args);
//        LinkieDiscordKt.start();
    }
    
    public LocalDateTime parse(String s) {
        return LocalDateTime.parse(s.split("\\.")[0].replace("Z", ""));
    }
}
