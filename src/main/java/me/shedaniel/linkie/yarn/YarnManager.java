package me.shedaniel.linkie.yarn;

import net.fabricmc.mappings.MappingsProvider;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class YarnManager {
    
    //    public static MappingsData b1_7_10 = new MappingsData();
    public static MappingsData r1_2_5 = new MappingsData();
    public static boolean updating = true;
    public static long lastUpdate = System.currentTimeMillis();
    public static long nextUpdate = -1;
    
    public static void updateYarn() {
        updating = true;
        lastUpdate = System.currentTimeMillis();
        long time = System.currentTimeMillis();
        try {
            //            updateYarn(new URL("https://github.com/minecraft-cursed-legacy/Minecraft-Cursed-POMF/archive/master.zip"), b1_7_10);
            updateYarn(new URL("https://github.com/Blayyke/yarn/archive/1.2.5.zip"), new URL("https://gist.githubusercontent.com/Chocohead/b7ea04058776495a93ed2d13f34d697a/raw/1.2.5%20Merge.tiny"), r1_2_5);
        } catch (Exception e) {
            e.printStackTrace();
        }
        lastUpdate = System.currentTimeMillis();
        time = lastUpdate - time;
        System.out.println("Updated Yarn in " + time + "ms");
        updating = false;
    }
    
    public static void updateYarn(URL yarnZip, URL tinyFile, MappingsData data) {
        try {
            data.mappings.clear();
            data.tiny = MappingsProvider.readTinyMappings(tinyFile.openStream(), false);
            InputStream input = yarnZip.openStream();
            ZipInputStream zipIn = new ZipInputStream(input);
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                InputStreamReader isr = new InputStreamReader(zipIn);
                if (!entry.isDirectory() && entry.getName().endsWith(".mapping")) {
                    MappingsData.MappingsFile file = new MappingsData.MappingsFile();
                    BufferedReader reader = new BufferedReader(isr);
                    int lineNum = 0;
                    while (reader.ready()) {
                        String line = reader.readLine();
                        int tab = StringUtils.countMatches(line, '\t');
                        String[] split = line.replace("\t", "").split(" ");
                        MappingsType type = MappingsType.getByString(split[0]);
                        if (tab == 0 && lineNum == 0 && type == MappingsType.CLASS) {
                            String obfName = split[1];
                            String deobfName = split.length > 2 ? split[2] : null;
                            file.setObfClass(obfName);
                            file.setDeobfClass(deobfName);
                            lineNum++;
                            continue;
                        } else if (tab == 1) {
                            if (type == MappingsType.FIELD && split.length == 4) {
                                String obfName = split[1];
                                String deobfName = split[2];
                                String desc = split[3];
                                if (!obfName.equals(deobfName)) {
                                    MappingsData.FieldMappings field = new MappingsData.FieldMappings();
                                    field.setObfName(obfName);
                                    field.setDeobfName(deobfName);
                                    field.setDesc(desc);
                                    field.test();
                                    file.getFieldMappings().add(field);
                                }
                            } else if (type == MappingsType.METHOD && split.length == 4) {
                                String obfName = split[1];
                                String deobfName = split[2];
                                String desc = split[3];
                                if (!obfName.equals(deobfName)) {
                                    MappingsData.MethodMappings method = new MappingsData.MethodMappings();
                                    method.setObfName(obfName);
                                    method.setDeobfName(deobfName);
                                    method.setDesc(desc);
                                    method.test();
                                    file.getMethodMappings().add(method);
                                }
                            }
                        }
                        lineNum++;
                    }
                    file.test();
                    data.mappings.add(file);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
