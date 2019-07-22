package me.shedaniel.linkie;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.shedaniel.cursemetaapi.CurseMetaAPI;
import me.shedaniel.linkie.commands.*;
import org.eclipse.egit.github.core.Gist;
import org.eclipse.egit.github.core.GistFile;
import org.eclipse.egit.github.core.service.GistService;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.channel.ServerChannel;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.javacord.api.util.logging.ExceptionLogger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import java.awt.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class LinkieBot {
    
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static LinkieBot instance;
    public ScheduledExecutorService executors = Executors.newScheduledThreadPool(16);
    public ScheduledExecutorService sendUpdate = Executors.newSingleThreadScheduledExecutor();
    public ScheduledExecutorService updateGitHub = Executors.newScheduledThreadPool(16);
    public ScheduledExecutorService singleThreadExecutor = Executors.newSingleThreadScheduledExecutor();
    public CommandApi commandApi;
    public DiscordApi api;
    
    public LinkieBot() {
        LinkieBot.instance = this;
        HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        });
        try {
            GistService service = new GistService();
            service.getClient().setOAuth2Token(System.getenv("GITHUB"));
            Gist gist = service.getGist("71e2d88a0f49e6dc2d6895f235e47c99");
            Map<String, GistFile> files = gist.getFiles();
            GistFile messagesJson = files.get("messages.json");
            JsonArray jsonArray = GSON.fromJson(messagesJson.getContent(), JsonArray.class);
            messagesJson.setContent(GSON.toJson(jsonArray));
            service.updateGist(gist);
        } catch (Exception e) {
            e.printStackTrace();
        }
        new DiscordApiBuilder().setToken(System.getenv("TOKEN")).login().thenAccept(api -> {
            this.api = api;
            api.addMessageCreateListener(commandApi = new CommandApi(api, "+"));
            if (false) {
                commandApi.registerCommand(new HelpCommand(), "help", "?", "commands");
                commandApi.registerCommand(new FabricApiVersionCommand(), "fabricapi");
                commandApi.registerCommand(new UserInfoCommand(), "userinfo", "info", "user", "whois", "who");
                commandApi.registerCommand(new OldCheckStatsCommand(), "oldcheckstats", "os");
                commandApi.registerCommand(new NewCheckStatsCommand(), "newcheckstats", "ns");
                commandApi.registerCommand((service, event, author, cmd, args) -> {
                    if (author.getId() == 430615025066049538l)
                        this.runUpdate();
                }, "forcestart");
                api.setMessageCacheSize(10, 60 * 60);
                api.addServerMemberJoinListener(event -> {
                    if (event.getServer().getId() == 432055962233470986l)
                        event.getServer().getChannelById(432057546589601792l).map(Channel::asTextChannel).ifPresent(tc -> tc.ifPresent(textChannel -> {
                            User user = event.getUser();
                            textChannel.sendMessage(new EmbedBuilder().setTitle("Welcome **" + user.getDiscriminatedName() + "**! #" + event.getServer().getMembers().size()).setThumbnail(user.getAvatar()).setTimestampToNow().setDescription("Welcome " + user.getDiscriminatedName() + " to `" + event.getServer().getName() + "`. Get mod related support at <#576851123345031177>, <#582248149729411072>, <#593809520682205184> and <#576851701911388163>, and chat casually at <#432055962233470988>!\n\nAnyways, enjoy your stay!")).join();
                        }));
                });
                api.addServerMemberLeaveListener(event -> {
                    if (event.getServer().getId() == 432055962233470986l)
                        event.getServer().getChannelById(432057546589601792l).map(Channel::asTextChannel).ifPresent(tc -> tc.ifPresent(textChannel -> {
                            User user = event.getUser();
                            String[] bad = getBad();
                            textChannel.sendMessage(new EmbedBuilder().setTitle("Goodbye **" + user.getDiscriminatedName() + "**! Farewell.").setDescription("\"" + bad[new Random().nextInt(bad.length)] + "\" - Linkie").setThumbnail(user.getAvatar()).setTimestampToNow()).join();
                        }));
                });
                singleThreadExecutor.scheduleAtFixedRate(this::runUpdate, 0, 1, TimeUnit.MINUTES);
            }
        }).exceptionally(ExceptionLogger.get());
        while (true) {
        
        }
    }
    
    public static DiscordApi getApi() {
        return getInstance().api;
    }
    
    public static LinkieBot getInstance() {
        return instance;
    }
    
    public static void main(String[] args) {
        new LinkieBot();
    }
    
    private String[] getBad() {
        try {
            GistService service = new GistService();
            service.getClient().setOAuth2Token(System.getenv("GITHUB"));
            Gist gist = service.getGist("71e2d88a0f49e6dc2d6895f235e47c99");
            Map<String, GistFile> files = gist.getFiles();
            GistFile messagesJson = files.get("messages.json");
            JsonArray jsonArray = GSON.fromJson(messagesJson.getContent(), JsonArray.class);
            List<String> s = new ArrayList<>();
            jsonArray.forEach(jsonElement -> {
                try {
                    s.add(jsonElement.getAsString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            return s.toArray(new String[0]);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new String[0];
    }
    
    private void runUpdate() {
        long start = System.currentTimeMillis();
        try {
            System.out.println("Starting update!");
            GistService service = new GistService();
            service.getClient().setOAuth2Token(System.getenv("GITHUB"));
            Gist gist = service.getGist("b046fb65b74f297380ec2264b7f28f94");
            Map<String, GistFile> files = gist.getFiles();
            GistFile projectsJson = files.get("projects.json");
            GistFile filesJson = files.get("files.json");
            JsonArray jsonArray = GSON.fromJson(projectsJson.getContent(), JsonArray.class);
            JsonObject filesObj = GSON.fromJson(filesJson.getContent(), JsonObject.class);
            AtomicBoolean edited = new AtomicBoolean(false);
            AtomicInteger done = new AtomicInteger(0);
            jsonArray.forEach(jsonElement -> {
                CompletableFuture.runAsync(() -> {
                    int addonId = jsonElement.getAsInt();
                    try {
                        CurseMetaAPI.Addon addon = CurseMetaAPI.getAddon(addonId);
                        List<CurseMetaAPI.AddonFile> addonFiles = CurseMetaAPI.getAddonFiles(addonId);
                        addonFiles.sort(Comparator.comparingInt(value -> value.fileId));
                        Collections.reverse(addonFiles);
                        if (!addonFiles.isEmpty()) {
                            CurseMetaAPI.AddonFile addonFile = addonFiles.get(0);
                            if (!filesObj.has(addonId + "") || filesObj.get(addonId + "").getAsInt() != addonFile.fileId) {
                                if (filesObj.has(addonId + ""))
                                    filesObj.remove(addonId + "");
                                filesObj.addProperty(addonId + "", addonFile.fileId);
                                edited.set(true);
                                System.out.println(addon.name + " has update!");
                                long time = System.currentTimeMillis();
                                CompletableFuture.runAsync(() -> {
                                    try {
                                        while (api == null)
                                            if (System.currentTimeMillis() - time > 60000) {
                                                System.out.println("Overtime!");
                                                return;
                                            }
                                        ServerChannel serverChannel = api.getServerById(432055962233470986l).get().getChannelById(531450739935936543l).get();
                                        EmbedBuilder embedBuilder = new EmbedBuilder().setTitle(addon.name);
                                        Optional<CurseMetaAPI.Addon.AddonAttachment> possibleIcon = addon.attachments.stream().filter(addonAttachment -> addonAttachment.isDefault).findFirst();
                                        if (possibleIcon.isPresent())
                                            embedBuilder.setThumbnail(possibleIcon.get().thumbnailUrl);
                                        embedBuilder.addInlineField("Display Name", addonFile.displayName);
                                        embedBuilder.addInlineField("File Name", addonFile.fileName);
                                        embedBuilder.addInlineField("File Id", addonFile.fileId + "");
                                        embedBuilder.addField("Game Versions", addonFile.gameVersion.stream().collect(Collectors.joining(", ")));
                                        embedBuilder.setUrl("https://www.curseforge.com/minecraft/mc-mods/" + addon.slug + "/files/" + addonFile.fileId);
                                        embedBuilder.setTimestamp(parse(addonFile.fileDate).toInstant(ZoneOffset.UTC));
                                        embedBuilder.setColor(addonFile.releaseType == 1 ? Color.GREEN : addonFile.releaseType == 2 ? Color.blue.brighter().brighter() : Color.RED);
                                        serverChannel.asTextChannel().get().sendMessage(embedBuilder).get();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }, sendUpdate);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    done.incrementAndGet();
                }, updateGitHub);
            });
            CompletableFuture.runAsync(() -> {
                try {
                    while (done.get() < jsonArray.size()) {
                    
                    }
                    System.out.println("Update finished, uploading!");
                    if (edited.get())
                        filesJson.setContent(GSON.toJson(filesObj));
                    if (edited.get())
                        service.updateGist(gist);
                    System.out.println("Upload finished, final time: " + (System.currentTimeMillis() - start) + "ms!");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }, updateGitHub);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public LocalDateTime parse(String s) {
        //        if (s.indexOf('.') >= 0)
        return LocalDateTime.parse(s.split("\\.")[0].replace("Z", ""));
        //        return LocalDateTime.parse(s);
    }
}
