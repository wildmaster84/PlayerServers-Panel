package me.wild.utils.webserver;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import me.wild.PlayerServersPanel;
import me.wild.api.RequestHandler;
import me.wild.api.TemplateEngine;
import me.wild.utils.managers.AuthTokenManager;
import me.wild.utils.webserver.handlers.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class WebServer {

    private Undertow server;
    private RoutingHandler routingHandler;

    public void start(int port, AuthTokenManager authTokenManager) {
        routingHandler = new RoutingHandler();

        // Serve static files from the "web" directory
        File resourceBase = new File(PlayerServersPanel.getInstance().getDataFolder(), "web");
        ResourceHandler resourceHandler = new ResourceHandler(
            new ClassPathResourceManager(this.getClass().getClassLoader(), resourceBase.getPath())
        ).setDirectoryListingEnabled(false);  // Disable directory listing

        // Add routing for API and static files
        routingHandler.get(resourceBase.toString(), resourceHandler);

        // Add API routes
        routingHandler.post("/api/server/{server_id}/start", new ServerManagementHandler(authTokenManager));
        routingHandler.post("/api/server/{server_id}/stop", new ServerManagementHandler(authTokenManager));
        routingHandler.post("/api/server/{server_id}", new ServerManagementHandler(authTokenManager));
        routingHandler.post("/api/server/{server_id}/restart", new ServerManagementHandler(authTokenManager));
        routingHandler.post("/api/server/{server_id}/command", new ServerManagementHandler(authTokenManager));
        routingHandler.get("/api/server/{server_id}/logs", new ServerLogHandler(authTokenManager));
        
        // File Utilities
        routingHandler.post("/api/server/{server_id}/compress", new ServerManagementHandler(authTokenManager));
        routingHandler.post("/api/server/{server_id}/decompress", new ServerManagementHandler(authTokenManager));
        routingHandler.post("/api/server/{server_id}/rename", new ServerManagementHandler(authTokenManager));
        routingHandler.post("/api/server/{server_id}/download", new ServerManagementHandler(authTokenManager));
        
        // File system
        routingHandler.get("/api/server/{server_id}/files", new ServerManagementHandler(authTokenManager));
        routingHandler.post("/api/server/{server_id}/files", new ServerManagementHandler(authTokenManager));
        routingHandler.delete("/api/server/{server_id}/files", new ServerManagementHandler(authTokenManager));
        
        // Pages
        routingHandler.get("/dashboard", new DashboardHandler(authTokenManager));
        routingHandler.get("/login", new LoginHandler(PlayerServersPanel.getInstance().getDatabaseManager(), authTokenManager));
        routingHandler.post("/login", new LoginHandler(PlayerServersPanel.getInstance().getDatabaseManager(), authTokenManager));
        routingHandler.get("/register", new RegisterHandler(PlayerServersPanel.getInstance().getDatabaseManager()));
        routingHandler.post("/register", new RegisterHandler(PlayerServersPanel.getInstance().getDatabaseManager()));
        routingHandler.get("/", new HomePageHandler());
        routingHandler.get("/server/{server_id}", new ServerPageHandler(authTokenManager));
        routingHandler.get("/server/{server_id}/files", new FileManagerHandler(authTokenManager));
        
        HttpHandler errorHandler = exchange -> {
        	routingHandler.handleRequest(exchange);
        	if (exchange.getStatusCode() != 200) {
        		handleGlobalError(exchange);
        	}
        };
        
        // Start Undertow server
        server = Undertow.builder()
                .addHttpListener(port, "0.0.0.0")
                .setHandler(errorHandler)
                .build();
        server.start();
        
        PlayerServersPanel.getInstance().getLogger().info("Undertow server started on port " + port);
    }

    public void stop() {
        if (server != null) {
            server.stop();
            PlayerServersPanel.getInstance().getLogger().info("Undertow server stopped");
        }
    }

    public RoutingHandler getRoutingHandler() {
        return routingHandler;
    }
    
    private void handleGlobalError(HttpServerExchange exchange) throws IOException {
    	if (exchange.getRequestMethod().equals(Methods.GET) && !exchange.getRequestURI().contains("api")) {
    		switch(exchange.getStatusCode()) {
    		case 400: {
    			String renderedContent = TemplateEngine.renderTemplate("web/errors/" + exchange.getStatusCode() + ".html", new HashMap<>());
    			exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
	            exchange.getResponseSender().send(renderedContent);
	            break;
    		}
    		case 401: {
    			String renderedContent = TemplateEngine.renderTemplate("web/errors/" + exchange.getStatusCode() + ".html", new HashMap<>());
    			exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
	            exchange.getResponseSender().send(renderedContent);
	            break;
    		}
    		case 403: {
    			String renderedContent = TemplateEngine.renderTemplate("web/errors/" + exchange.getStatusCode() + ".html", new HashMap<>());
    			exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
	            exchange.getResponseSender().send(renderedContent);
	            break;
    		}
    		case 404: {
    			String renderedContent = TemplateEngine.renderTemplate("web/errors/" + exchange.getStatusCode() + ".html", new HashMap<>());
    			exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
	            exchange.getResponseSender().send(renderedContent);
	            break;
    		}
    		case 405: {
    			String renderedContent = TemplateEngine.renderTemplate("web/errors/" + exchange.getStatusCode() + ".html", new HashMap<>());
    			exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
	            exchange.getResponseSender().send(renderedContent);
	            break;
    		}
    		case 500: {
    			String renderedContent = TemplateEngine.renderTemplate("web/errors/" + exchange.getStatusCode() + ".html", new HashMap<>());
    			exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
	            exchange.getResponseSender().send(renderedContent);
	            break;
    		}
    		}
    		
    	} else {
    		switch(exchange.getStatusCode()) {
    		case 200: {
	    		RequestHandler.sendJsonResponse(exchange, exchange.getStatusCode(), true, "Request processed.");
	            break;
	    	}
    		case 201: {
	    		RequestHandler.sendJsonResponse(exchange, exchange.getStatusCode(), true, "Resource processed.");
	            break;
	    	}
	    	case 400: {
	    		RequestHandler.sendJsonResponse(exchange, exchange.getStatusCode(), false, "Invalid request.");
	            break;
	    	}
	    	case 401: {
	    		RequestHandler.sendJsonResponse(exchange, exchange.getStatusCode(), false, "Invalid session.");
	            break;
	    	}
	    	case 403: {
	    		RequestHandler.sendJsonResponse(exchange, exchange.getStatusCode(), false, "Access to the requested resource was denied.");
	            break;
	    	}
	    	case 404: {
	    		RequestHandler.sendJsonResponse(exchange, exchange.getStatusCode(), false, "Requested resource could not be found.");
	            break;
	    	}
	    	case 405: {
	    		RequestHandler.sendJsonResponse(exchange, exchange.getStatusCode(), false, "Method not allowed.");
	            break;
	    	}
	    	case 500: {
	    		RequestHandler.sendJsonResponse(exchange, exchange.getStatusCode(), false, "Internal server error.");
	            break;
	    	}
	    	default: {
	    		RequestHandler.sendJsonResponse(exchange, exchange.getStatusCode(), false, "Unhandled error");
	    	}
    	}
    	}
    }
}
