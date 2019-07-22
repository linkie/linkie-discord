package me.shedaniel.linkie.yarn;

import net.fabricmc.mappings.EntryTriple;

public class YarnField {
    
    private EntryTriple intermediary, server, client, mapped;
    
    public YarnField(EntryTriple intermediary, EntryTriple server, EntryTriple client) {
        this.intermediary = intermediary;
        this.server = server;
        this.client = client;
    }
    
    public YarnField(EntryTriple intermediary, EntryTriple mapped) {
        this.intermediary = intermediary;
        this.mapped = mapped;
    }
    
    public EntryTriple getIntermediary() {
        return intermediary;
    }
    
    public EntryTriple getServer() {
        return server;
    }
    
    public void setServer(EntryTriple server) {
        this.server = server;
    }
    
    public EntryTriple getClient() {
        return client;
    }
    
    public void setClient(EntryTriple client) {
        this.client = client;
    }
    
    public EntryTriple getMapped() {
        return mapped;
    }
    
    public void setMapped(EntryTriple mapped) {
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
