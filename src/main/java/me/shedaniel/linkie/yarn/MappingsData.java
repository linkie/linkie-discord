package me.shedaniel.linkie.yarn;

import net.fabricmc.mappings.Mappings;
import net.fabricmc.mappings.MappingsProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MappingsData {
    
    public List<MappingsFile> mappings = new ArrayList<>();
    public Mappings tiny = MappingsProvider.createEmptyMappings();
    
    public static class MappingsFile {
        
        private String obfClass;
        private String deobfClass;
        private List<FieldMappings> fieldMappings = new ArrayList<>();
        private List<MethodMappings> methodMappings = new ArrayList<>();
        
        public List<FieldMappings> getFieldMappings() {
            return fieldMappings;
        }
        
        public List<MethodMappings> getMethodMappings() {
            return methodMappings;
        }
        
        public String getObfClass() {
            return obfClass;
        }
        
        public void setObfClass(String obfClass) {
            this.obfClass = obfClass;
        }
        
        public String getDeobfClass() {
            return deobfClass == null ? getObfClass() : deobfClass;
        }
        
        public void setDeobfClass(String deobfClass) {
            this.deobfClass = deobfClass;
        }
        
        public void test() {
            if (obfClass == null)
                throw new IllegalArgumentException();
        }
        
        @Override
        public String toString() {
            ArrayList<Object> map = new ArrayList<>(getFieldMappings());
            map.add(getMethodMappings());
            return String.format("Mappings[%s -> %s]: %s", getObfClass(), getDeobfClass(), map.stream().map(Object::toString).collect(Collectors.joining(", ")));
        }
        
    }
    
    public static class FieldMappings {
        
        private String obfName;
        private String deobfName;
        private String desc;
        
        public String getObfName() {
            return obfName;
        }
        
        public void setObfName(String obfName) {
            this.obfName = obfName;
        }
        
        public String getDeobfName() {
            return deobfName == null ? getObfName() : deobfName;
        }
        
        public void setDeobfName(String deobfName) {
            this.deobfName = deobfName;
        }
        
        public String getDesc() {
            return desc;
        }
        
        public void setDesc(String desc) {
            this.desc = desc;
        }
        
        public void test() {
            if (obfName == null || deobfName == null || desc == null)
                throw new IllegalArgumentException();
        }
        
        @Override
        public String toString() {
            return String.format("Field[%s -> %s, %s]", getObfName(), getDeobfName(), getDesc());
        }
        
    }
    
    public static class MethodMappings {
        
        private String obfName;
        private String deobfName;
        private String desc;
        
        public String getObfName() {
            return obfName;
        }
        
        public void setObfName(String obfName) {
            this.obfName = obfName;
        }
        
        public String getDeobfName() {
            return deobfName == null ? getObfName() : deobfName;
        }
        
        public void setDeobfName(String deobfName) {
            this.deobfName = deobfName;
        }
        
        public String getDesc() {
            return desc;
        }
        
        public void setDesc(String desc) {
            this.desc = desc;
        }
        
        public void test() {
            if (obfName == null || deobfName == null || desc == null)
                throw new IllegalArgumentException();
        }
        
        @Override
        public String toString() {
            return String.format("Method[%s -> %s, %s]", getObfName(), getDeobfName(), getDesc());
        }
        
    }
    
}
