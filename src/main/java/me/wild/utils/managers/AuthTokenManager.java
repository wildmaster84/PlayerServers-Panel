package me.wild.utils.managers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import io.undertow.server.HttpServerExchange;
import me.wild.PlayerServersPanel;
import me.wild.api.RequestHandler;
import net.md_5.bungee.api.ProxyServer;

public class AuthTokenManager {
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int IV_SIZE = 16;  // AES block size
    private static final long TOKEN_EXPIRATION_TIME_MS = 2 * 60 * 60 * 1000L; // 2 hours in milliseconds

    private static String serverToken = getAESKeyFromAPIToken(PlayerServersPanel.getInstance().getApiToken());;
    private final Map<UUID, String> sessionStore = new HashMap<>();  // Maps player UUID to session ID

    // Generate a new token with a 2-hour expiration time
    public String generateToken(String ipAddress, UUID playerUUID, boolean isAdmin) {
        long expirationTime = System.currentTimeMillis() + TOKEN_EXPIRATION_TIME_MS;
        String sessionId = UUID.randomUUID().toString();
        sessionStore.put(playerUUID, sessionId);  // Store the session ID

        String tokenData = ipAddress + "::" + playerUUID.toString() + "::" + expirationTime + "::" + isAdmin + "::" + sessionId;
        return encrypt(tokenData, serverToken);
    }

    // Invalidate the session by removing the session ID for the player UUID
    public void invalidateSession(UUID playerUUID) {
        sessionStore.remove(playerUUID);
    }

    public TokenInfo validateToken(String token) {
        try {
            String decryptedData = decrypt(token, serverToken);
            String[] parts = decryptedData.split("::");
            if (parts.length != 5) return null;

            String ipAddress = parts[0];
            UUID playerUUID = UUID.fromString(parts[1]);
            long expirationTime = Long.parseLong(parts[2]);
            boolean isAdmin = Boolean.parseBoolean(parts[3]);
            String sessionId = parts[4];

            // Check if the token has expired
            if (System.currentTimeMillis() > expirationTime) return null;

            // Validate the session ID
            String storedSessionId = sessionStore.get(playerUUID);
            if (!sessionId.equals(storedSessionId)) return null;

            return new TokenInfo(playerUUID, isAdmin, ipAddress, expirationTime);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean isAuthorized(HttpServerExchange exchange) {
        String authToken = exchange.getRequestCookie("Authorization") != null ?
                exchange.getRequestCookie("Authorization").getValue() : null;
        if (authToken == null || authToken.isEmpty()) return false;
        

        TokenInfo tokenInfo = validateToken(authToken);
        if (tokenInfo == null || !tokenInfo.getIpAddress().equals(RequestHandler.getClientIp(exchange)) ||
                tokenInfo.isAdmin() != isAdmin(tokenInfo.getPlayerUUID())) {
            return false;
        }
        return true;
    }
    
    public boolean isAdmin(UUID playerUUID) {
        net.md_5.bungee.api.connection.ProxiedPlayer player = ProxyServer.getInstance().getPlayer(playerUUID);
        if (player != null) {
            return player.hasPermission("servers.admin");
        }
        return false;
    }

    // Encrypt a string using AES
    public static String encrypt(String data, String key) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKey secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");

            byte[] iv = new byte[IV_SIZE];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[IV_SIZE + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, IV_SIZE);
            System.arraycopy(encryptedBytes, 0, combined, IV_SIZE, encryptedBytes.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // Decrypt a string using AES
    public static String decrypt(String encryptedData, String key) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKey secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");

            byte[] combined = Base64.getDecoder().decode(encryptedData);
            byte[] iv = new byte[IV_SIZE];
            byte[] encryptedBytes = new byte[combined.length - IV_SIZE];
            System.arraycopy(combined, 0, iv, 0, IV_SIZE);
            System.arraycopy(combined, IV_SIZE, encryptedBytes, 0, encryptedBytes.length);

            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // Generate a key from the API token
    public static String getAESKeyFromAPIToken(String uuid) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] key = sha.digest(uuid.getBytes());
            byte[] keyBytes = new byte[16];
            System.arraycopy(key, 0, keyBytes, 0, 16);

            StringBuilder hexString = new StringBuilder();
            for (byte b : keyBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "";
        }
    }

    // Helper class to store token information
    public static class TokenInfo {
        private final UUID playerUUID;
        private final boolean isAdmin;
        private final String ipAddress;
        private final long expirationTime;

        public TokenInfo(UUID playerUUID, boolean isAdmin, String ipAddress, long expirationTime) {
            this.playerUUID = playerUUID;
            this.isAdmin = isAdmin;
            this.ipAddress = ipAddress;
            this.expirationTime = expirationTime;
        }

        public UUID getPlayerUUID() {
            return playerUUID;
        }

        public boolean isAdmin() {
            return isAdmin;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public long getExpirationTime() {
            return expirationTime;
        }
    }
}
