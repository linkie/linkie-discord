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
        
        private String intermediaryClass;
        private String yarnClass;
        private List<FieldMappings> fieldMappings = new ArrayList<>();
        private List<MethodMappings> methodMappings = new ArrayList<>();
        
        public List<FieldMappings> getFieldMappings() {
            return fieldMappings;
        }
        
        public List<MethodMappings> getMethodMappings() {
            return methodMappings;
        }
        
        public String getIntermediaryClass() {
            return intermediaryClass;
        }
        
        public void setIntermediaryClass(String intermediaryClass) {
            this.intermediaryClass = intermediaryClass;
        }
        
        public String getYarnClass() {
            return yarnClass == null ? getIntermediaryClass() : yarnClass;
        }
        
        public void setYarnClass(String yarnClass) {
            this.yarnClass = yarnClass;
        }
        
        public void test() {
            if (intermediaryClass == null)
                throw new IllegalArgumentException();
        }
        
        @Override
        public String toString() {
            ArrayList<Object> map = new ArrayList<>(getFieldMappings());
            map.add(getMethodMappings());
            return String.format("Mappings[%s -> %s]: %s", getIntermediaryClass(), getYarnClass(), map.stream().map(Object::toString).collect(Collectors.joining(", ")));
        }
        
    }
    
    public static class FieldMappings {
        
        private String intermediaryName;
        private String yarnName;
        private String intermediaryDesc;
        
        public String getIntermediaryName() {
            return intermediaryName;
        }
        
        public void setIntermediaryName(String intermediaryName) {
            this.intermediaryName = intermediaryName;
        }
        
        public String getYarnName() {
            return yarnName == null ? getIntermediaryName() : yarnName;
        }
        
        public void setYarnName(String yarnName) {
            this.yarnName = yarnName;
        }
        
        public String getIntermediaryDesc() {
            return intermediaryDesc;
        }
        
        public void setIntermediaryDesc(String intermediaryDesc) {
            this.intermediaryDesc = intermediaryDesc;
        }
        
        public void test() {
            if (intermediaryName == null || yarnName == null || intermediaryDesc == null)
                throw new IllegalArgumentException();
        }
        
        @Override
        public String toString() {
            return String.format("Field[%s -> %s, %s]", getIntermediaryName(), getYarnName(), getIntermediaryDesc());
        }
        
    }
    
    public static class MethodMappings {
        
        private String intermediaryName;
        private String yarnName;
        private String intermediaryDesc;
        
        public String getIntermediaryName() {
            return intermediaryName;
        }
        
        public void setIntermediaryName(String intermediaryName) {
            this.intermediaryName = intermediaryName;
        }
        
        public String getYarnName() {
            return yarnName == null ? getIntermediaryName() : yarnName;
        }
        
        public void setYarnName(String yarnName) {
            this.yarnName = yarnName;
        }
        
        public String getIntermediaryDesc() {
            return intermediaryDesc;
        }
        
        public void setIntermediaryDesc(String intermediaryDesc) {
            this.intermediaryDesc = intermediaryDesc;
        }
        
        public void test() {
            if (intermediaryName == null || yarnName == null || intermediaryDesc == null)
                throw new IllegalArgumentException();
        }
        
        @Override
        public String toString() {
            return String.format("Method[%s -> %s, %s]", getIntermediaryName(), getYarnName(), getIntermediaryDesc());
        }
        
    }
    
}
