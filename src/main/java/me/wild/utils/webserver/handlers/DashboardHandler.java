package me.wild.utils.webserver.handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import com.google.gson.Gson;
import me.wild.api.TemplateEngine;
import me.wild.utils.managers.AuthTokenManager;
import net.cakemine.playerservers.bungee.PlayerServers;
import net.cakemine.playerservers.bungee.objects.PlayerServer;
import me.wild.utils.managers.AuthTokenManager.TokenInfo;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DashboardHandler implements HttpHandler {

    private final AuthTokenManager authTokenManager;
    private final Gson gson = new Gson();

    public DashboardHandler(AuthTokenManager authTokenManager) {
        this.authTokenManager = authTokenManager;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws IOException {
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }
        
        if (!authTokenManager.isAuthorized(exchange)) {
        	exchange.setStatusCode(302);
            exchange.getResponseHeaders().put(Headers.LOCATION, "/login");
            return;
        }

        // Only handle POST requests
        if (!exchange.getRequestMethod().equalToString("GET")) {
            exchange.setStatusCode(302);
            exchange.getResponseHeaders().put(Headers.LOCATION, "/login");
            return;
        }
        
        String authToken = exchange.getRequestCookie("Authorization").getValue();
        TokenInfo tokenInfo = authTokenManager.validateToken(authToken);
        UUID playerUUID = tokenInfo.getPlayerUUID();

        boolean isAdmin = authTokenManager.isAdmin(playerUUID);
        HashMap<String, PlayerServer> servers = new HashMap<>();
		HashMap<String, Object> serverList = new HashMap<>();

		if (isAdmin) {
		    servers = PlayerServers.getApi().getServerMap();
		} else {
		    servers.put("playerUUID.toString()", PlayerServers.getApi().getServerMap().get(playerUUID.toString()));
		}
		
		
		servers.values().forEach(ps -> {
			HashMap<String, Object> serverOption = new HashMap<>();
			serverOption.put("status", ps.getStatus().name());
			serverOption.put("serverName", ps.getName());
			serverOption.put("maxPlayers", ps.getMaxPlayers());
			serverOption.put("maxRam", ps.getRam().split("/")[1]);
			serverOption.put("port", ps.getPort());
			serverOption.put("home", "servers/" + ps.getUUID().toLowerCase());
			serverOption.put("owner", ps.getUUID().toString());
			serverOption.put("uuid", ps.getUUID().toString());
			serverList.put(ps.getName(), serverOption);
		});

		String serverJson = gson.toJson(serverList);

		// Prepare placeholders for the template
		Map<String, String> placeholders = new HashMap<>();
		placeholders.put("title", "Dashboard");
		placeholders.put("header", isAdmin ? "All Servers" : "Your Servers");
		placeholders.put("server_list", serverJson);

		// Render the template with the placeholders
		String templatePath = "web/dashboard.html";  // Adjust the path as necessary
		String renderedContent = TemplateEngine.renderTemplate(templatePath, placeholders);

		// Send the rendered content to the client
		exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
		exchange.getResponseSender().send(renderedContent);
    }
}
