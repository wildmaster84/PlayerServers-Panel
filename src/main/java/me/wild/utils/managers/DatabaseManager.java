package me.wild.utils.managers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.mindrot.jbcrypt.BCrypt;

import net.cakemine.playerservers.bungee.PlayerServers;
import net.cakemine.playerservers.bungee.PlayerServersAPI;

public class DatabaseManager {

    private Connection connection;
    private final ExecutorService executorService;
    private static PlayerServersAPI plugin = PlayerServers.getApi();

    public DatabaseManager() {
        // Create a single-threaded executor for database operations
        this.executorService = Executors.newSingleThreadExecutor();
    }

    // Asynchronous method to connect to the database
    public Future<Boolean> connect(String url) {
        return executorService.submit(() -> Connect(url));
    }

    // Synchronous method to connect to the database (runs on a separate thread)
    private boolean Connect(String url) throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);
            createTables();  // Initialize tables
            return true;  // Connection successful
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found.", e);
        } catch (SQLException e) {
            throw new SQLException("Failed to connect to the database.", e);
        }
    }

    // Asynchronous user registration
    public Future<Void> registerUser(UUID playerUUID, String username, String passwordHash) {
        return executorService.submit(() -> {
            RegisterUser(playerUUID, username, passwordHash);
            return null;
        });
    }

    // Synchronous method to register a user (runs on the separate thread)
    private void RegisterUser(UUID playerUUID, String username, String passwordHash) throws SQLException {
        String sql = "INSERT INTO users (player_uuid, username, password_hash) VALUES (?, ?, ?)";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerUUID.toString());
            pstmt.setString(2, username);
            pstmt.setString(3, passwordHash);
            pstmt.executeUpdate();
        }
    }

    // Asynchronous method to check user credentials
    public Future<Boolean> checkCredentials(String username, String passwordHash) {
        return executorService.submit(() -> CheckCredentials(username, passwordHash));
    }

    // Synchronous method to check user credentials
    private boolean CheckCredentials(String username, String enteredPassword) throws SQLException {
        String sql = "SELECT password_hash FROM users WHERE username = ?";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                // Retrieve the stored password hash from the result set
                String storedHash = rs.getString("password_hash");

                // Use BCrypt to check if the entered password matches the stored hash
                return BCrypt.checkpw(enteredPassword, storedHash);
            } else {
                // No user found with the given username
                return false;
            }
        }
    }

    // Synchronous method to create necessary tables
    private void createTables() throws SQLException {
                                          
        String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                                  "user_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                  "player_uuid VARCHAR(36) UNIQUE NOT NULL, " +
                                  "username VARCHAR(50) NOT NULL, " +
                                  "password_hash VARCHAR(255) NOT NULL, " +
                                  "is_linked BOOLEAN DEFAULT FALSE, " +
                                  "is_banned BOOLEAN DEFAULT FALSE" +
                                  ");";

        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(createUsersTable);
        }
    }

    // Other user and server management methods remain synchronous for now
    public Future<Void> linkAccount(UUID playerUUID) {
        return executorService.submit(() -> {
            LinkAccount(playerUUID);
            return null;
        });
    }

    // Synchronous method to link an account
    private void LinkAccount(UUID playerUUID) throws SQLException {
        String sql = "UPDATE users SET is_linked = TRUE WHERE player_uuid = ?";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerUUID.toString());
            pstmt.executeUpdate();
        }
    }

    public Future<Boolean> isAccountLinked(UUID playerUUID) {
        return executorService.submit(() -> IsAccountLinked(playerUUID));
    }

    // Synchronous method to check if an account is linked
    private boolean IsAccountLinked(UUID playerUUID) throws SQLException {
        String sql = "SELECT is_linked FROM users WHERE player_uuid = ?";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerUUID.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getBoolean("is_linked");
            }
        }
        return false;
    }

    public Future<Boolean> isBanned(UUID playerUUID) {
        return executorService.submit(() -> IsBanned(playerUUID));
    }

    // Synchronous method to check if a player is banned
    private boolean IsBanned(UUID playerUUID) throws SQLException {
        String sql = "SELECT is_banned FROM users WHERE player_uuid = ?";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerUUID.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getBoolean("is_banned");
            }
        }
        return false;
    }

    public Future<UUID> getPlayerUUIDByUsername(String username) {
        return executorService.submit(() -> GetPlayerUUIDByUsername(username));
    }
    
    private UUID GetPlayerUUIDByUsername(String username) throws SQLException {
        String sql = "SELECT player_uuid FROM users WHERE username = ?";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return UUID.fromString(rs.getString("player_uuid"));
            }
        }
        return null;
    }

    // Ensure the connection is open before any operation
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("Database connection is closed.");
        }
        return connection;
    }

    // Close the connection and shutdown the executor service
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        // Shutdown the executor service
        executorService.shutdown();
    }
}
