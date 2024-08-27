package me.wild.utils.webserver.handlers;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import me.wild.PlayerServersPanel;
import me.wild.api.RequestHandler;
import me.wild.utils.FileUtils;
import me.wild.utils.managers.AuthTokenManager;
import me.wild.utils.managers.AuthTokenManager.TokenInfo;
import net.cakemine.playerservers.bungee.PlayerServers;
import net.cakemine.playerservers.bungee.objects.PlayerServer;
import net.md_5.bungee.api.ProxyServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ServerManagementHandler implements HttpHandler {

    private final AuthTokenManager authTokenManager;

    public ServerManagementHandler(AuthTokenManager authTokenManager) {
        this.authTokenManager = authTokenManager;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws IOException {
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }

        if (!authTokenManager.isAuthorized(exchange)) {
            RequestHandler.sendJsonResponse(exchange, 401, false, "Invalid or Expired session!");
            return;
        }

        exchange.startBlocking();

        String path = exchange.getRelativePath();
        String[] pathSegments = path.split("/");
        
        if (pathSegments.length < 4 || pathSegments[3] == null || pathSegments[3].isEmpty()) {
            RequestHandler.sendJsonResponse(exchange, 404, false, "Invalid server ID");
            return;
        }

        String serverId = pathSegments[3];
        String authToken = exchange.getRequestCookie("Authorization").getValue();
        TokenInfo token = authTokenManager.validateToken(authToken);
        
        UUID serverUUID = UUID.fromString(serverId);
        if (!token.getPlayerUUID().toString().equalsIgnoreCase(serverId) && !token.isAdmin()) {
        	RequestHandler.sendJsonResponse(exchange, 403, false, "You do not have access to this server! ");
        	return;
        }
        if (PlayerServers.getApi().getServerMap().isEmpty() || PlayerServers.getApi().getServerMap().get(serverUUID.toString()) == null) {
    		RequestHandler.sendJsonResponse(exchange, 404, false, "Server not found!");
    		return;
    	}

        String lastSegment = pathSegments[pathSegments.length - 1];

        switch (lastSegment) {
            case "start":
            case "stop":
            case "restart":
            case "compress":
            case "decompress":
            case "rename":
            case "command":
            case "download":
                handleServerCommand(exchange, lastSegment, serverUUID);
                break;
            case "files":
                handleFileOperations(exchange, serverUUID);
                break;
            default:
            	if (lastSegment == serverId) {
            		PlayerServer server = PlayerServers.getApi().getServerMap().get(serverUUID.toString());
            		
            		HashMap<String, Object> status = new HashMap<>();
            		HashMap<String, Object> info = new HashMap<>();
            		
            		status.put("status", server.getStatus());
            		status.put("serverName", server.getName());
            		status.put("maxPlayers", server.getMaxPlayers());
            		status.put("maxRam", server.getRam().split("/")[1]);
            		status.put("port", server.getPort());
            		status.put("home", "plugins/PlayerServers/servers/" + serverUUID.toString());
            		status.put("owner", server.getUUID());
            		
            		info.put("info", status);
            		RequestHandler.sendJsonResponse(exchange, 200, true, "Retrived server info", info);
            		break;
            	}
                RequestHandler.sendJsonResponse(exchange, 404, false, "Unknown API endpoint. " + lastSegment + " " + serverId);
                break;
        }
    }

    private void handleServerCommand(HttpServerExchange exchange, String action, UUID serverUUID) throws IOException {
    	if (PlayerServers.getApi().getServerMap().isEmpty() || PlayerServers.getApi().getServerMap().get(serverUUID.toString()) == null) {
    		RequestHandler.sendJsonResponse(exchange, 404, false, "Server not found!");
    		return;
    	}
    	Map<String, String> request;
    	PlayerServer server = PlayerServers.getApi().getServerMap().get(serverUUID.toString());
        switch (action) {
            case "start":
            	PlayerServers.getApi().getServerManager().startupSrv(serverUUID.toString(), null);
                RequestHandler.sendJsonResponse(exchange, 200, true, "Sent start command!");
                break;
            case "stop":
            	PlayerServers.getApi().getServerManager().stopSrv(serverUUID.toString());
                RequestHandler.sendJsonResponse(exchange, 200, true, "Sent stop command!");
                break;
            case "restart":
            	PlayerServers.getApi().getServerManager().stopSrv(serverUUID.toString());
            	
            	ProxyServer.getInstance().getScheduler().schedule(PlayerServersPanel.getInstance(), () -> {
            		PlayerServers.getApi().getServerManager().startupSrv(serverUUID.toString(), null);
            	}, 10L, TimeUnit.SECONDS);
            	
                RequestHandler.sendJsonResponse(exchange, 200, true, "Sent restart command!");
                break;
            case "command":
                request = RequestHandler.getJsonBody(exchange);
                String command = request.get("command");
                if (command != null && !command.isEmpty()) {
                	server.sendCommand(command);
                    RequestHandler.sendJsonResponse(exchange, 200, true, "Sent command");
                } else {
                    RequestHandler.sendJsonResponse(exchange, 200, false, "Command was empty!");
                }
                break;
            case "compress":
            	 request = RequestHandler.getJsonBody(exchange);
                 String source = request.get("source");
                 if (source == null || source.isEmpty()) {
                     RequestHandler.sendJsonResponse(exchange, 400, false, "Missing 'source' or 'destination' parameter.");
                     break;
                 }
                 String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
                 
                 File directory = new File("plugins/PlayerServers/servers/", serverUUID.toString());

                 File sourcePath = new File(directory, source);
                 File destinationZip = new File(sourcePath.getParent(), source + "-" + timeStamp + ".zip");
                 
                 if (!FileUtils.isWithinServerDirectory(server, destinationZip)) {
                     RequestHandler.sendJsonResponse(exchange, 403, false, "Access denied. File is outside of the server directory.");
                     break;
                 }

                 if (!sourcePath.exists()) {
                     RequestHandler.sendJsonResponse(exchange, 404, false, "Source file/directory not found.");
                     break;
                 }
                 
                 boolean wasCompressed = FileUtils.compress(sourcePath, destinationZip);
                 if (wasCompressed) {
                	 RequestHandler.sendJsonResponse(exchange, 200, true, "Compressed file(s).");
                 } else {
                     RequestHandler.sendJsonResponse(exchange, 500, false, "failed tp compressed file(s).");
                 }
                 break;
            case "decompress":
                request = RequestHandler.getJsonBody(exchange);
                String zipFilePath = request.get("zipFile");
                if (zipFilePath == null || zipFilePath.isEmpty()) {
                    RequestHandler.sendJsonResponse(exchange, 400, false, "Missing 'zipFile' parameter.");
                    break;
                }
                File directory2 = new File("plugins/PlayerServers/servers/", serverUUID.toString());
                File zipFile = new File(directory2, zipFilePath);
                File destinationDir = new File(zipFile.getParent());
                
                if (!FileUtils.isWithinServerDirectory(server, zipFile)) {
                    RequestHandler.sendJsonResponse(exchange, 403, false, "Access denied. File is outside of the server directory.");
                    break;
                }

                if (!zipFile.exists()) {
                    RequestHandler.sendJsonResponse(exchange, 404, false, "ZIP file not found.");
                    break;
                }

                boolean wasDecompressed = FileUtils.decompress(zipFile, destinationDir);
                if (wasDecompressed) {
                    RequestHandler.sendJsonResponse(exchange, 200, true, "Decompressed file(s).");
                } else {
                    RequestHandler.sendJsonResponse(exchange, 500, false, "Failed to decompress file(s). " + zipFile.toPath().toString() + " " + destinationDir.toPath().toString());
                }
                break;
            case "rename":
                request = RequestHandler.getJsonBody(exchange);
                String oldPathStr = request.get("oldPath");
                String newPathStr = request.get("newPath");
                if (oldPathStr == null || oldPathStr.isEmpty() || newPathStr == null || newPathStr.isEmpty()) {
                    RequestHandler.sendJsonResponse(exchange, 400, false, "Missing 'oldPath' or 'newPath' parameter.");
                    break;
                }
                File directory3 = new File("plugins/PlayerServers/servers/", serverUUID.toString());
                File oldFile = new File(directory3, oldPathStr);
                File newFile = new File(oldFile.getParent(), newPathStr);
                
                if (!FileUtils.isWithinServerDirectory(server, newFile)) {
                    RequestHandler.sendJsonResponse(exchange, 403, false, "Access denied. File is outside of the server directory.");
                    break;
                }

                if (!oldFile.exists()) {
                    RequestHandler.sendJsonResponse(exchange, 404, false, "File to rename not found.");
                    break;
                }

                boolean wasRenamed = oldFile.renameTo(newFile);
                if (wasRenamed) {
                    RequestHandler.sendJsonResponse(exchange, 200, true, "File renamed successfully.");
                } else {
                    RequestHandler.sendJsonResponse(exchange, 500, false, "Failed to rename file.");
                }
                break;
            case "download":
                // Read the JSON body to get the file path for download
                request = RequestHandler.getJsonBody(exchange);
                String filePath = request.get("path");

                if (filePath == null || filePath.isEmpty()) {
                    RequestHandler.sendJsonResponse(exchange, 400, false, "Missing 'path' parameter.");
                    break;
                }
                File directory4 = new File("plugins/PlayerServers/servers/", serverUUID.toString());

                File fileToDownload = new File(directory4, filePath);
                
                if (!FileUtils.isWithinServerDirectory(server, fileToDownload)) {
                    RequestHandler.sendJsonResponse(exchange, 403, false, "Access denied. File is outside of the server directory.");
                    break;
                }

                if (!fileToDownload.exists() || !fileToDownload.isFile()) {
                    RequestHandler.sendJsonResponse(exchange, 404, false, "File not found.");
                    break;
                }

                // Set headers for download
                exchange.getResponseHeaders().put(io.undertow.util.Headers.CONTENT_DISPOSITION, "attachment; filename=\"" + fileToDownload.getName() + "\"");
                exchange.getResponseHeaders().put(io.undertow.util.Headers.CONTENT_TYPE, Files.probeContentType(fileToDownload.toPath()));
                exchange.getResponseHeaders().put(io.undertow.util.Headers.CONTENT_LENGTH, String.valueOf(fileToDownload.length()));
                sendFile(fileToDownload, exchange);
                break;
        }
    }

    @SuppressWarnings("resource")
	private void sendFile(File fileToDownload, HttpServerExchange exchange) {
    	try (FileChannel fileChannel = new FileInputStream(fileToDownload).getChannel()) {
            exchange.getResponseSender().transferFrom(fileChannel, new IoCallback() {
                @Override
                public void onComplete(HttpServerExchange exchange, Sender sender) {
                    // Close the exchange once the file is fully sent
                	exchange.endExchange();
                }

                @Override
                public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                    // Handle any exceptions during file transfer
                    RequestHandler.sendJsonResponse(exchange, 500, false, "Error serving file: " + exception.getMessage());
                }
            });
        } catch (IOException e) {
            RequestHandler.sendJsonResponse(exchange, 500, false, "Error opening file: " + e.getMessage());
        }
		
	}

	private void handleFileOperations(HttpServerExchange exchange, UUID serverUUID) throws IOException {
        net.cakemine.playerservers.bungee.objects.PlayerServer server = PlayerServers.getApi().getServerMap().get(serverUUID.toString());
        File directory = new File("plugins/PlayerServers/servers/", serverUUID.toString());
        String requestedPath = exchange.getQueryParameters().get("path") == null || exchange.getQueryParameters().get("path").isEmpty() ? "/" : exchange.getQueryParameters().get("path").getFirst().replaceAll("\\.\\./", "");
        File file = new File(directory, requestedPath);

        if (!FileUtils.isWithinServerDirectory(server, file)) {
            redirectToServerHome(exchange, server);
            return;
        }

        switch (exchange.getRequestMethod().toString()) {
            case "GET":
                handleGetFile(exchange, file);
                break;
            case "POST":
                handleFileUpload(exchange, file);
                break;
            case "DELETE":
            	if (!file.exists() || file.isHidden()) {
            		RequestHandler.sendJsonResponse(exchange, 500, false, "File does not exist.");
            		break;
            	}
            	
            	if (file.isDirectory()) {
            		FileUtils.deleteDirectory(file.toPath());
                    RequestHandler.sendJsonResponse(exchange, 200, true, "Deleted directory.");
                    break;
                    
            	}
            	if (file.isFile()) {
            		boolean wasDeleted = FileUtils.deleteFile(exchange, file);
                    if (wasDeleted) {
                    	RequestHandler.sendJsonResponse(exchange, 200, true, "Deleted file.");
                    } else {
                    	RequestHandler.sendJsonResponse(exchange, 500, false, "Failed to delete file.");
                    }
                    break;
            	}
            	RequestHandler.sendJsonResponse(exchange, 500, false, "Failed to process request.");
                break;
            default:
                RequestHandler.sendJsonResponse(exchange, 405, false, "Invalid request.");
                break;
        }
    }

    private void handleGetFile(HttpServerExchange exchange, File file) throws IOException {
        if (!file.exists()) {
            RequestHandler.sendJsonResponse(exchange, 404, false, "Path does not exist.");
            return;
        }
        if (file.isDirectory()) {
            listFiles(exchange, file);
        } else if (!file.isHidden() && file.isFile()) {
            serveFileContent(exchange, file);
        } else {
            RequestHandler.sendJsonResponse(exchange, 403, false, "Access denied.");
        }
    }

    private void listFiles(HttpServerExchange exchange, File directory) throws IOException {
        File[] files = directory.listFiles();
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> filePath = new HashMap<>();
        if (files != null) {
            for (File file : files) {
                if (!file.isHidden()) {
                    response.put(file.getName(), file.isDirectory() ? "directory" : "file");
                }
            }
        }
        filePath.put("files", response);
        RequestHandler.sendJsonResponse(exchange, 200, true, "Pulled file directory.", filePath);
    }

    private void serveFileContent(HttpServerExchange exchange, File file) throws IOException {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, Files.probeContentType(file.toPath()));
        exchange.getResponseSender().send(new String(Files.readAllBytes(file.toPath())));
    }

    private void handleFileUpload(HttpServerExchange exchange, File file) throws IOException {
        String fileContent = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Files.write(file.toPath(), fileContent.getBytes());
        RequestHandler.sendJsonResponse(exchange, 200, true, "File saved successfully.");
    }

    private void redirectToServerHome(HttpServerExchange exchange, net.cakemine.playerservers.bungee.objects.PlayerServer server) throws IOException {
        String serverHomePath = "/server/" + server.getUUID().toString() + "/files";
        exchange.setStatusCode(302);
        exchange.getResponseHeaders().put(Headers.LOCATION, serverHomePath);
        exchange.endExchange();
    }
}
