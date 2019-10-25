package me.shedaniel.linkie.spring;

import com.google.gson.reflect.TypeToken;
import me.shedaniel.cursemetaapi.CurseMetaAPI;
import me.shedaniel.linkie.LinkieBot;
import me.shedaniel.linkie.Pair;

import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LoadMeta {
    public static ScheduledExecutorService metaService = Executors.newScheduledThreadPool(16);
    public static List<MinecraftVersion> minecraftVersions = new ArrayList<>();
    public static String loaderVersion = null;
    public static Map<String, Pair<CurseMetaAPI.AddonFile, Boolean>> fabricApi = new LinkedHashMap<>();
    
    public static void load() {
        metaService.scheduleAtFixedRate(() -> {
            try {
                List<MinecraftVersion> list = LinkieBot.GSON.fromJson(
                        new InputStreamReader(CurseMetaAPI.InternetUtils.getSiteStream(new URL("https://meta.fabricmc.net/v2/versions/game"))),
                        new TypeToken<List<MinecraftVersion>>() {}.getType()
                );
                minecraftVersions.clear();
                minecraftVersions.addAll(list);
                System.out.println("Loaded " + minecraftVersions.size() + " MC Versions.");
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                List<YarnBuild> list = LinkieBot.GSON.fromJson(
                        new InputStreamReader(CurseMetaAPI.InternetUtils.getSiteStream(new URL("https://meta.fabricmc.net/v2/versions/yarn"))),
                        new TypeToken<List<YarnBuild>>() {}.getType()
                );
                if (!list.isEmpty()) {
                    minecraftVersions.forEach(version -> version.yarnMaven = null);
                    for(YarnBuild build : list) {
                        for(MinecraftVersion minecraftVersion : minecraftVersions) {
                            if (minecraftVersion.version.equalsIgnoreCase(build.gameVersion)) {
                                if (minecraftVersion.yarnMaven == null)
                                    minecraftVersion.yarnMaven = build.maven;
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                List<LoaderBuild> list = LinkieBot.GSON.fromJson(
                        new InputStreamReader(CurseMetaAPI.InternetUtils.getSiteStream(new URL("https://meta.fabricmc.net/v2/versions/loader"))),
                        new TypeToken<List<LoaderBuild>>() {}.getType()
                );
                if (!list.isEmpty()) {
                    loaderVersion = list.get(0).maven;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                Map<String, Pair<CurseMetaAPI.AddonFile, Boolean>> map = new LinkedHashMap<>();
                List<CurseMetaAPI.AddonFile> files = CurseMetaAPI.getAddonFiles(306612);
                files.sort(Comparator.comparingInt(value -> value.fileId));
                Collections.reverse(files);
                for(CurseMetaAPI.AddonFile file : files) {
                    String displayName = file.displayName;
                    if (displayName.charAt(0) == '[' && displayName.indexOf(']') > -1) {
                        String version = displayName.substring(1).split("]")[0];
                        if (version.contains("/"))
                            version = version.substring(0, version.indexOf("/"));
                        boolean isSnapshot = version.toLowerCase().contains("pre") || version.toLowerCase().startsWith("1.14_combat-") || version.toLowerCase().startsWith("19w") || version.toLowerCase().startsWith("20w") || version.toLowerCase().startsWith("18w") || version.toLowerCase().startsWith("21w");
                        if (!map.containsKey(version))
                            map.put(version, new Pair<>(file, !isSnapshot));
                    }
                }
                if (!map.isEmpty()) {
                    fabricApi = map;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 15, TimeUnit.MINUTES);
    }
    
    public static class MinecraftVersion {
        public String version;
        public boolean stable = false;
        public String yarnMaven;
        
        public boolean isStable() {
            return stable;
        }
    }
    
    public static class YarnBuild {
        public String gameVersion;
        public String maven;
    }
    
    public static class LoaderBuild {
        public String maven;
    }
}
