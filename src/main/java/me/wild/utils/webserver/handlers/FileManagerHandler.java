package me.wild.utils.webserver.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import me.wild.api.TemplateEngine;
import me.wild.utils.managers.AuthTokenManager;

import java.io.IOException;
import java.util.HashMap;

public class FileManagerHandler implements HttpHandler {
    private final AuthTokenManager authTokenManager;

    public FileManagerHandler(AuthTokenManager authTokenManager) {
        this.authTokenManager = authTokenManager;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws IOException {
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }

        // Check if the user is authorized
        if (!authTokenManager.isAuthorized(exchange)) {
            exchange.setStatusCode(302);
            exchange.getResponseHeaders().put(Headers.LOCATION, "/login");
            return;
        }

        // Serve the file manager HTML page
        serveFileManagerPage(exchange);
    }

    private void serveFileManagerPage(HttpServerExchange exchange) throws IOException {
        // Load the HTML template and render it
        HashMap<String, String> placeholders = new HashMap<>();
        String serverUUID = exchange.getQueryParameters().get("server_id").getFirst();
        placeholders.put("server_uuid", serverUUID);
        String renderedContent = TemplateEngine.renderTemplate("web/file_manager.html", placeholders);

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
        exchange.getResponseSender().send(renderedContent);
    }
}
