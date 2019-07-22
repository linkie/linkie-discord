package me.shedaniel.linkie.yarn;

public class YarnClass {
    
    private String intermediary, server, client, mapped;
    
    public YarnClass(String intermediary, String server, String client) {
        this.intermediary = intermediary;
        this.server = server;
        this.client = client;
    }
    
    public YarnClass(String intermediary, String mapped) {
        this.intermediary = intermediary;
        this.mapped = mapped;
    }
    
    public String getIntermediary() {
        return intermediary;
    }
    
    public String getServer() {
        return server;
    }
    
    public void setServer(String server) {
        this.server = server;
    }
    
    public String getClient() {
        return client;
    }
    
    public void setClient(String client) {
        this.client = client;
    }
    
    public String getMapped() {
        return mapped;
    }
    
    public void setMapped(String mapped) {
        this.mapped = mapped;
    }
    
    public boolean incomplete() {
        return needMapped() || needObf();
    }
    
    public boolean needMapped() {
        return mapped == null;
    }
    
    public boolean needObf() {
        return client == null && server == null;
    }
    
}
