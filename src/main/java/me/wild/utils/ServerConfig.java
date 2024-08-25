package me.wild.utils;

import java.util.UUID;

public class ServerConfig {

    private int maxRam;
    private int port;
    private int maxPlayers;
    private String worldName;
    private String directoryPath;  // Added directory path
    private String serverName;
    private String ownerUUID;
    private String serverUUID;

    public ServerConfig(int maxRam, int port, int maxPlayers, String worldName, String serverName, UUID serverUUID, UUID ownerUUID, String directoryPath) {
    	this.serverUUID = serverUUID.toString();
    	this.ownerUUID = ownerUUID.toString();
        this.maxRam = maxRam;
        this.port = port;
        this.maxPlayers = maxPlayers;
        this.worldName = worldName;
        this.serverName = serverName;
        this.directoryPath = directoryPath;  // Set the directory based on player UUID
    }

    // Getters and setters
    public int getMaxRam() {
        return maxRam;
    }

    public void setMaxRam(int maxRam) {
        this.maxRam = maxRam;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }
    
    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getDirectoryPath() {
        return directoryPath;
    }
    
    public void setOwnerUUID(String ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    public String getOwnerUUID() {
        return ownerUUID;
    }
    
    public void setServerUUID(String serverUUID) {
        this.serverUUID = serverUUID;
    }

    public String getServerUUID() {
        return serverUUID;
    }

    public void setDirectoryPath(String directoryPath) {
        this.directoryPath = directoryPath;
    }

    public ServerConfig getConfig() {
        return this;  // Return the current configuration
    }

	public int getMaxPlayers() {
		// TODO Auto-generated method stub
		return maxPlayers;
	}
	
	public void setMaxPlayers(int maxPlayers) {
		// TODO Auto-generated method stub
		this.maxPlayers = maxPlayers;
	}
}
