package me.shedaniel.linkie.yarn;

public enum MappingsType {
    UNKNOWN,
    CLASS,
    FIELD,
    METHOD;
    
    public static MappingsType getByString(String s) {
        for(MappingsType value : values())
            if (value.name().equalsIgnoreCase(s))
                return value;
        return UNKNOWN;
    }
}
