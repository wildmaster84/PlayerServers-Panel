package me.wild.utils.webserver.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import me.wild.api.RequestHandler;
import me.wild.api.TemplateEngine;
import me.wild.utils.managers.AuthTokenManager;
import me.wild.utils.managers.AuthTokenManager.TokenInfo;
import net.cakemine.playerservers.bungee.PlayerServers;
import net.cakemine.playerservers.bungee.PlayerServersAPI;
import net.cakemine.playerservers.bungee.objects.PlayerServer;

import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

public class ServerPageHandler implements HttpHandler {

    private final AuthTokenManager authTokenManager;

    public ServerPageHandler(AuthTokenManager authTokenManager) {
        this.authTokenManager = authTokenManager;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws IOException {
        // Extract the server_id from the URL
    	if (exchange.getQueryParameters().get("server_id") == null || exchange.getQueryParameters().get("server_id").isEmpty()) {
    		RequestHandler.sendJsonResponse(exchange, 404, false, "the requested page could not be found!");
    	}
        String serverId = exchange.getQueryParameters().get("server_id").getFirst();
        
        if (!authTokenManager.isAuthorized(exchange)) {
        	exchange.setStatusCode(302);
            exchange.getResponseHeaders().put(Headers.LOCATION, "/login");
            return;
        }
        
        if (PlayerServers.getApi().getServerMap().isEmpty() || PlayerServers.getApi().getServerMap().get(serverId) == null) {
            exchange.setStatusCode(404);
    		return;
    	}
        
        String authToken = exchange.getRequestCookie("Authorization").getValue();
        TokenInfo tokenInfo = authTokenManager.validateToken(authToken);
    	

        if (exchange.getRequestMethod().equalToString("GET")) {
            handleGet(exchange, serverId, tokenInfo);
        }
        
    }
    
    private void handleGet(HttpServerExchange exchange, String serverId, TokenInfo token) throws IOException {
        // Render the template with the log content
    	HashMap<String, String> placeholders = new HashMap<>();
    	PlayerServer server = PlayerServers.getApi().getServerMap().get(serverId);
    	placeholders.put("server_name", server.getName());
    	placeholders.put("server_port", String.valueOf(server.getPort()));
    	placeholders.put("server_maxram", String.valueOf(server.getRam().split("/")[1]));
    	placeholders.put("server_maxplayers", String.valueOf(server.getMaxPlayers()));
    	placeholders.put("server_uuid", serverId);
    	placeholders.put("player_uuid", token.getPlayerUUID().toString());
    	
        String renderedContent = TemplateEngine.renderTemplate("web/server_page.html",  placeholders);

        // Serve the rendered content
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
        exchange.getResponseSender().send(renderedContent);
    }
}
