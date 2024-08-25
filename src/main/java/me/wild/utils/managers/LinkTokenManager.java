package me.wild.utils.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LinkTokenManager {

    private final Map<UUID, String> linkTokens = new HashMap<>();

    public String generateLinkToken(UUID playerUUID) {
        String linkToken = UUID.randomUUID().toString();
        linkTokens.put(playerUUID, linkToken);
        return linkToken;
    }

    public boolean isValidLinkToken(UUID playerUUID, String token) {
        return linkTokens.containsKey(playerUUID) && linkTokens.get(playerUUID).equals(token);
    }
    
    public String getLinkToken(UUID playerUUID) {
    	if (linkTokens.get(playerUUID) == null || linkTokens.get(playerUUID).isEmpty()) {
    		generateLinkToken(playerUUID);
    	}
    	return linkTokens.get(playerUUID);
    }
}
