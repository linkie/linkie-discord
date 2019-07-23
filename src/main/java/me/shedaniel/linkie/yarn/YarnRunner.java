package me.shedaniel.linkie.yarn;

import java.net.URL;

public class YarnRunner {
    
    public static void main(String[] args) {
        try {
            MappingsData mappingsData = new MappingsData();
            YarnManager.updateYarn(new URL("https://github.com/FabricMC/yarn/archive/1.14.4.zip"), null, mappingsData);
            long t = System.currentTimeMillis();
            YarnManager.mapClass("net/minecraft/class_770$class_772", mappingsData).ifPresent(System.out::println);
            System.out.println(System.currentTimeMillis() - t);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }
    
}
