package me.shedaniel.linkie.spring;

import com.google.gson.reflect.TypeToken;
import me.shedaniel.cursemetaapi.CurseMetaAPI;
import me.shedaniel.linkie.LinkieBot;

import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LoadMeta {
    public static ScheduledExecutorService metaService = Executors.newScheduledThreadPool(16);
    public static List<MinecraftVersion> minecraftVersions = new ArrayList<>();
    
    public static void load() {
        metaService.scheduleAtFixedRate(() -> {
            minecraftVersions.clear();
            try {
                List<MinecraftVersion> list = LinkieBot.GSON.fromJson(
                        new InputStreamReader(CurseMetaAPI.InternetUtils.getSiteStream(new URL("https://meta.fabricmc.net/v2/versions/game"))),
                        new TypeToken<List<MinecraftVersion>>() {}.getType()
                );
                minecraftVersions.addAll(list);
                System.out.println("Loaded " + minecraftVersions.size() + " MC Versions.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 180, TimeUnit.SECONDS);
    }
    
    public static class MinecraftVersion {
        public String version;
        public boolean stable = false;
    
        public boolean isStable() {
            return stable;
        }
    }
}
