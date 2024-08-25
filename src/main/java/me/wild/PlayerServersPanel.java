package me.wild;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;

import me.wild.commands.LinkCommand;
import me.wild.commands.RegenerateTokenCommand;
import me.wild.utils.managers.AuthTokenManager;
import me.wild.utils.managers.DatabaseManager;
import me.wild.utils.managers.LinkTokenManager;
import me.wild.utils.webserver.WebServer;
import net.cakemine.playerservers.bungee.PlayerServers;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

public class PlayerServersPanel extends Plugin {

    private static PlayerServersPanel instance;
    private LinkTokenManager linkTokenManager;
    private DatabaseManager databaseManager;
    private AuthTokenManager authTokenManager;
    private WebServer webServer;
    private String apiToken;
    private Configuration config;

    @Override
    public void onEnable() {
    	if (PlayerServers.getApi().getPluginVersion()[0] < 2 || PlayerServers.getApi().getPluginVersion()[1] < 1) {
    		getLogger().warning("PlayerServers-Panel requires PlayerServers v2.1.0+");
    		this.onDisable();
    	}
        instance = this;
        extractResources();
        loadConfiguration();
        authTokenManager = new AuthTokenManager();
        databaseManager = new DatabaseManager();
        linkTokenManager = new LinkTokenManager(); 
        String dbUrl = getConfig().getString("database.url", "jdbc:sqlite:playerservers.db").replace("jdbc:sqlite:", "jdbc:sqlite:" + instance.getDataFolder() + "/"); // Default to SQLite
        
        Future<Boolean> futureConnection = databaseManager.connect(dbUrl);
        
        try {
            boolean connected = futureConnection.get();  // Wait for the connection to complete
            if (connected) {
                getLogger().info("Database connected successfully.");
                // Start the web server for API access
                startWebserver();

                // Register commands, listeners, and other initialization tasks
                ProxyServer.getInstance().getPluginManager().registerCommand(this, new RegenerateTokenCommand());
                ProxyServer.getInstance().getPluginManager().registerCommand(this, new LinkCommand(linkTokenManager, databaseManager));
                
                getLogger().info("PlayerServers has been enabled.");

            } else {
                getLogger().severe("Failed to connect to the database. Disabling plugin.");
                getProxy().stop();  // Stop the proxy or disable the plugin if the database connection fails
            }
        } catch (InterruptedException | ExecutionException e) {
            getLogger().severe("Error during database connection: " + e);
            e.printStackTrace();
            getProxy().stop();  // Stop the proxy or disable the plugin if there's an exception
        }
    }
    
    private void startWebserver() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
        	int port = getConfig().getInt("webserver.port", 8080);  // Default port to 8080
            webServer = new WebServer();
            try {
                webServer.start(port, authTokenManager);
            } catch (Exception e) {
                getLogger().severe("Failed to start WebServer: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onDisable() {
        // Save any remaining data and stop servers gracefully
    	if (webServer != null) {
            webServer.stop();
        }
    	
    	if (databaseManager != null) {
            databaseManager.close();
            getLogger().info("Database connection closed.");
        }
    	
        getLogger().info("PlayerServers-Panel has been disabled.");
    }

    public static PlayerServersPanel getInstance() {
        return instance;
    }

    private void loadConfiguration() {
        // Logic to load configuration files (config.yml, etc.)
    	saveDefaultConfig();  // Ensure default config is saved if not present
        reloadConfig();
        
        // God how i hate this
        if (getConfig().getString("api-token") == null || getConfig().getString("api-token").isEmpty()) {
        	regenerateApiToken();
        }
        apiToken = getConfig().getString("api-token");

        getLogger().info("Configuration loaded.");
    }
    
    public String getApiToken() {
        return apiToken;
    }
    
    public LinkTokenManager getLinkTokenManager() {
    	return linkTokenManager;
    }
    
    public AuthTokenManager getAuthTokenManager() {
    	return authTokenManager;
    }
    
    public void reloadConfig() {
        try {        	
            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(getConfigFile());
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to load configuration.", e);
        }
    }

    public Configuration getConfig() {
        if (config == null) {
            reloadConfig();  // Ensure config is loaded if accessed before initialization
        }
        return config;
    }

    public void saveConfig() {
        try {
            ConfigurationProvider.getProvider(YamlConfiguration.class).save(config, getConfigFile());
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to save configuration.", e);
        }
    }

    public void saveDefaultConfig() {
        if (!getConfigFile().exists()) {
            try {
                getDataFolder().mkdirs();
                saveResource("config.yml", false);
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to save default configuration.", e);
            }
        }
    }

    private File getConfigFile() {
        return new File(getDataFolder(), "config.yml");
    }

    // Utility function to save a resource file
    private void saveResource(String resourcePath, boolean replace) {
        File outFile = new File(getDataFolder(), resourcePath);
        if (!outFile.exists() || replace) {
            try (InputStream in = getResourceAsStream(resourcePath)) {
                Files.copy(in, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to save resource: " + resourcePath, e);
            }
        }
    }
    
    public DatabaseManager getDatabaseManager() {
    	return databaseManager;
    }
    
    public void regenerateApiToken() {
        String newToken = UUID.randomUUID().toString();
        getConfig().set("api-token", newToken);
        saveConfig();  // Save the new token to the config file
        reloadConfig();  // Reload to ensure everything is up to date
    }
    
    private void extractResources() {
        File dataFolder = getDataFolder();
        File webFolder = new File(dataFolder, "web");
        File errorFolder = new File(dataFolder, "web/errors");
        File templateFolder = new File(dataFolder, "templates/default");

        // Create the target folders if they don't exist
        if (!webFolder.exists() && !webFolder.mkdirs()) {
            getLogger().severe("Could not create directory: " + webFolder.getAbsolutePath());
            return;
        }
        if (!errorFolder.exists() && !errorFolder.mkdirs()) {
            getLogger().severe("Could not create directory: " + errorFolder.getAbsolutePath());
            return;
        }
        if (!templateFolder.exists() && !templateFolder.mkdirs()) {
            getLogger().severe("Could not create directory: " + templateFolder.getAbsolutePath());
            return;
        }

        // List of files to extract
        String[] webFiles = {"login.html", "index.html", "dashboard.html", "register.html", "server_page.html", "file_manager.html"};
        String[] errorFiles = {"404.html"};
        String[] templateFiles = {"SERVER_JAR_HERE.txt"};

        // Extract web files
        extractFiles("web", webFiles, webFolder);
        extractFiles("web/errors", errorFiles, errorFolder);
        extractFiles("templates/default", templateFiles, templateFolder);
    }

    private void extractFiles(String resourceBase, String[] fileNames, File targetFolder) {
        for (String fileName : fileNames) {
            try (InputStream in = getResourceAsStream(resourceBase + "/" + fileName)) {
                if (in != null) {
                    File targetFile = new File(targetFolder, fileName);
                    if (!targetFile.exists()) {
                        Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        getLogger().info("Extracted " + targetFile.getAbsolutePath());
                    }
                } else {
                    getLogger().severe("Failed to find resource: " + resourceBase + "/" + fileName);
                }
            } catch (IOException e) {
                getLogger().severe("Failed to extract resource: " + e.getMessage());
            }
        }
    }
    
    public WebServer getWebserver() {
    	return webServer;
    }
}
