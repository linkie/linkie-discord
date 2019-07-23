package me.shedaniel.linkie.yarn;

import net.fabricmc.mappings.MappingsProvider;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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
            //            updateYarn(new URL("https://github.com/FabricMC/yarn/archive/1.14.4.zip"), null, r1_2_5);
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
            if (tinyFile != null)
                data.tiny = MappingsProvider.readTinyMappings(tinyFile.openStream(), false);
            InputStream input = yarnZip.openStream();
            ZipInputStream zipIn = new ZipInputStream(input);
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                InputStreamReader isr = new InputStreamReader(zipIn);
                if (!entry.isDirectory() && entry.getName().endsWith(".mapping")) {
                    MappingsData.MappingsFile parentClass = new MappingsData.MappingsFile();
                    data.mappings.add(parentClass);
                    BufferedReader reader = new BufferedReader(isr);
                    List<String> strings = new ArrayList<>();
                    List<YarnLine> lines = new ArrayList<>();
                    while (reader.ready())
                        strings.add(reader.readLine());
                    lines.addAll(strings.stream().map(s -> {
                        int tab = StringUtils.countMatches(s, '\t');
                        String[] split = s.replace("\t", "").split(" ");
                        MappingsType type = MappingsType.getByString(split[0]);
                        return new YarnLine(s, tab, split, type);
                    }).collect(Collectors.toList()));
                    
                    List<MappingsData.MappingsFile> classesTree = new ArrayList<>();
                    for(YarnLine line : lines) {
                        if (line.type == MappingsType.CLASS) {
                            String intermediaryName = line.split[1];
                            String yarnName = line.split.length > 2 ? line.split[2] : intermediaryName;
                            MappingsData.MappingsFile file = line.tab > 0 ? new MappingsData.MappingsFile() : parentClass;
                            for(int i = line.tab - 1; i >= 0; i--) {
                                MappingsData.MappingsFile file1 = classesTree.get(i);
                                if (file1 != file) {
                                    intermediaryName = file1.getIntermediaryClass() + "$" + intermediaryName;
                                    yarnName = file1.getYarnClass() + "$" + yarnName;
                                }
                            }
                            file.setIntermediaryClass(intermediaryName);
                            file.setYarnClass(yarnName);
                            if (line.tab > 0)
                                data.mappings.add(file);
                            if (line.tab <= classesTree.size())
                                classesTree.add(file);
                            else
                                classesTree.set(line.tab, file);
                        } else if (line.type == MappingsType.FIELD) {
                            if (line.split.length != 4)
                                continue;
                            String intermediary = line.split[1];
                            String yarn = line.split[2];
                            String desc = line.split[3];
                            if (!intermediary.equals(yarn)) {
                                MappingsData.FieldMappings field = new MappingsData.FieldMappings();
                                field.setIntermediaryName(intermediary);
                                field.setYarnName(yarn);
                                field.setIntermediaryDesc(desc);
                                field.test();
                                classesTree.get(line.tab - 1).getFieldMappings().add(field);
                            }
                        } else if (line.type == MappingsType.METHOD) {
                            if (line.split.length != 4)
                                continue;
                            String intermediary = line.split[1];
                            String yarn = line.split[2];
                            String desc = line.split[3];
                            if (!intermediary.equals(yarn)) {
                                MappingsData.MethodMappings method = new MappingsData.MethodMappings();
                                method.setIntermediaryName(intermediary);
                                method.setYarnName(yarn);
                                method.setIntermediaryDesc(desc);
                                method.test();
                                classesTree.get(line.tab - 1).getMethodMappings().add(method);
                            }
                        }
                    }
                    
                    parentClass.test();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static Optional<String> mapClass(String clazz, MappingsData mappingsData) {
        for(MappingsData.MappingsFile mapping : mappingsData.mappings) {
            Optional<String> s = mapClass("", "", clazz, mapping);
            if (s.isPresent())
                return s;
        }
        return Optional.empty();
    }
    
    private static Optional<String> mapClass(String before, String yarnBefore, String clazz, MappingsData.MappingsFile file) {
        String s1 = before + (before.isEmpty() ? "" : "$") + file.getIntermediaryClass();
        String s2 = yarnBefore + (yarnBefore.isEmpty() ? "" : "$") + file.getYarnClass();
        if (s1.equalsIgnoreCase(clazz))
            return Optional.of(s2);
        //        for(MappingsData.MappingsFile innerClass : file.getInnerClasses()) {
        //            Optional<String> s = mapClass(s1, s2, clazz, innerClass);
        //            if (s.isPresent())
        //                return s;
        //        }
        return Optional.empty();
    }
    
    public static String getLast(String s) {
        return getLast(getLast(s, '/'), '$');
    }
    
    private static String getLast(String s, char c) {
        if (s.indexOf(c) <= -1)
            return s;
        String[] s1Split = s.split(c + "");
        return s1Split[s1Split.length - 1];
    }
    
    static class YarnLine {
        String line;
        int tab;
        String[] split;
        MappingsType type;
        
        public YarnLine(String line, int tab, String[] split, MappingsType type) {
            this.line = line;
            this.tab = tab;
            this.split = split;
            this.type = type;
        }
    }
    
}
