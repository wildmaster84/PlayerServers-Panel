package me.wild.utils.webserver.handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import me.wild.api.RequestHandler;
import me.wild.api.TemplateEngine;
import me.wild.utils.managers.DatabaseManager;
import net.md_5.bungee.api.ProxyServer;
import org.mindrot.jbcrypt.BCrypt;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class RegisterHandler implements HttpHandler {

    private final DatabaseManager databaseManager;
    private final Gson gson = new Gson();

    public RegisterHandler(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws IOException {
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }

        if (exchange.getRequestMethod().equalToString("GET")) {
            handleGet(exchange);
        } else if (exchange.getRequestMethod().equalToString("POST")) {
            handlePost(exchange);
        }
    }

    private void handleGet(HttpServerExchange exchange) throws IOException {
        // Render the template with the log content
        String renderedContent = TemplateEngine.renderTemplate("web/register.html",  new HashMap<>());

        // Serve the rendered content
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
        exchange.getResponseSender().send(renderedContent);
    }

    @SuppressWarnings("unchecked")
	private void handlePost(HttpServerExchange exchange) throws IOException {
    	exchange.getRequestReceiver().receiveFullString((ex, message) -> {
            Map<String, String> request = gson.fromJson(message, Map.class);

            if (request.get("username") == null || request.get("password") == null || request.get("confirmPassword") == null ||
                request.get("username").isEmpty() || request.get("password").isEmpty() || request.get("confirmPassword").isEmpty()) {
                RequestHandler.sendJsonResponse(exchange, 400, false, "Username and password must not be empty.");
                return;
            }

            if (!request.get("password").equals(request.get("confirmPassword"))) {
                RequestHandler.sendJsonResponse(exchange, 400, false, "Passwords do not match. Please try again.");
                return;
            }

            if (ProxyServer.getInstance().getPlayer(request.get("username")) == null) {
                RequestHandler.sendJsonResponse(exchange, 400, false, "You must be logged into the server to create an account!");
                return;
            }

            try {
                String hashedPassword = BCrypt.hashpw(request.get("password"), BCrypt.gensalt());
                UUID playerUUID = ProxyServer.getInstance().getPlayer(request.get("username")).getUniqueId();

                Future<UUID> futureUUID = databaseManager.getPlayerUUIDByUsername(request.get("username"));
                UUID existingUUID = futureUUID.get();

                if (existingUUID == null) {
                    Future<Void> futureRegistration = databaseManager.registerUser(playerUUID, request.get("username"), hashedPassword);
                    futureRegistration.get();
                    RequestHandler.sendJsonResponse(exchange, 200, true, "Registration successful.");
                } else {
                    RequestHandler.sendJsonResponse(exchange, 409, false, "Account already exists.");
                }
            } catch (InterruptedException | ExecutionException e) {
                RequestHandler.sendJsonResponse(exchange, 500, false, "An error occurred while processing your registration.");
                e.printStackTrace();
            }
        });
    }
}
