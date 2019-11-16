package me.shedaniel.linkie;

//import me.shedaniel.linkie.spring.LinkieMinecraftInfoKt;
//import me.shedaniel.linkie.spring.LinkieSpringApplication;
//import org.springframework.boot.SpringApplication;

import me.shedaniel.linkie.audio.LinkieMusic;

public class LinkieBot {
    
    public static void main(String[] args) {
        //        LinkieMinecraftInfoKt.startInfoSync();
        //        SpringApplication.run(LinkieSpringApplication.class, args);
        LinkieDiscordKt.start();
    }
    
}
