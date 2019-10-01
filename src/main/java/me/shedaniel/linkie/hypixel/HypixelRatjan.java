package me.shedaniel.linkie.hypixel;

import com.google.gson.JsonObject;
import me.shedaniel.linkie.LinkieBot;
import net.hypixel.api.HypixelAPI;
import net.hypixel.api.reply.PlayerReply;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HypixelRatjan {
    public static final HypixelAPI API = new HypixelAPI(UUID.fromString(System.getenv("HYPIXEL")));
    public static final String[] PLAYERS_TO_TRACK = {"24e76580-697d-4dee-ad1e-8746762f533e",
            "75f077b5-5f0b-4e22-a995-b3552691468b", "03e3fa82-6398-4af8-a11d-32e2d1b2d4c7", "9eaa0a74-f5e2-4352-b78e-1ba1f13323a3"};
    public static ScheduledExecutorService hypixelService = Executors.newScheduledThreadPool(16);
    
    public static void loop() {
        hypixelService.scheduleAtFixedRate(() -> {
            List<PlayerDetail> detailList = new ArrayList<>();
            for(String s : PLAYERS_TO_TRACK) {
                try {
                    PlayerReply reply = API.getPlayerByUuid(s).get();
                    JsonObject player = reply.getPlayer();
                    detailList.add(new PlayerDetail(player.get("displayname").getAsString(), player.get("lastLogin").getAsLong()));
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
            LinkieBot.getApi().getTextChannelById(628530198312386561l).ifPresent(channel -> {
                EmbedBuilder builder = new EmbedBuilder().setTitle("Hypixel Player Update");
                detailList.sort(Comparator.comparing(PlayerDetail::getDisplayname));
                for(PlayerDetail playerDetail : detailList) {
                    String time = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(playerDetail.lastLogin))
                            .replaceFirst("T", " ")
                            .replaceFirst("Z", "");
                    time = time.substring(0, time.lastIndexOf('.'));
                    builder.addField(playerDetail.displayname, "Last Played: " + time + "\nAlright: " + playerDetail.isAlright());
                }
                channel.sendMessage(builder).join();
            });
        }, 0, 5, TimeUnit.MINUTES);
    }
    
    public static class PlayerDetail {
        private String displayname;
        private long lastLogin;
        private boolean isAlright;
        
        public PlayerDetail(String displayname, long lastLogin) {
            this.displayname = displayname;
            this.lastLogin = lastLogin;
            this.isAlright = lastLogin < 1569888000000l;
        }
        
        public String getDisplayname() {
            return displayname;
        }
        
        public long getLastLogin() {
            return lastLogin;
        }
        
        public boolean isAlright() {
            return isAlright;
        }
    }
    
}
