package me.wild.utils.webserver.handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import me.wild.PlayerServersPanel;
import me.wild.api.RequestHandler;
import me.wild.api.TemplateEngine;
import me.wild.utils.managers.AuthTokenManager;
import me.wild.utils.managers.DatabaseManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class LoginHandler implements HttpHandler {

    private final DatabaseManager databaseManager;
    private final AuthTokenManager authTokenManager;

    public LoginHandler(DatabaseManager databaseManager, AuthTokenManager authTokenManager) {
        this.databaseManager = databaseManager;
        this.authTokenManager = authTokenManager;
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
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("title", "Login");

        // Render the template with the placeholders
        String templatePath = "web/login.html";  // Adjust the path as necessary
        String renderedContent = TemplateEngine.renderTemplate(templatePath, placeholders);

        // Send the rendered content to the client
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
        exchange.setStatusCode(200);
        exchange.getResponseSender().send(renderedContent);
    }

    private void handlePost(HttpServerExchange exchange) throws IOException {
    	FormParserFactory formParserFactory = FormParserFactory.builder().build();
        FormDataParser formDataParser = formParserFactory.createParser(exchange);

        if (formDataParser != null) {
            FormData formData = formDataParser.parseBlocking();
            
            String username = RequestHandler.getFormValue(formData, "username");
            String password = RequestHandler.getFormValue(formData, "password");

            if (username == null || password == null) {
            	RequestHandler.sendJsonResponse(exchange, 400, false, "Invalid request. Username and password must not be empty.");
                return;
            }
            
            try {
                // Asynchronously check user credentials
                Future<Boolean> futureCredentials = databaseManager.checkCredentials(username, password);
                boolean credentialsValid = futureCredentials.get();  // Wait for the result

                if (credentialsValid) {
                    // Asynchronously get the player's UUID
                    Future<UUID> futureUUID = databaseManager.getPlayerUUIDByUsername(username);
                    UUID playerUUID = futureUUID.get();  // Wait for the result

                    // Asynchronously check if the user is banned
                    Future<Boolean> futureBanned = databaseManager.isBanned(playerUUID);
                    boolean isBanned = futureBanned.get();  // Wait for the result

                    if (isBanned) {
                        RequestHandler.sendJsonResponse(exchange, 403, false, "Account banned.");
                    } else {
                        // Asynchronously check if the account is linked
                        Future<Boolean> futureLinked = databaseManager.isAccountLinked(playerUUID);
                        boolean isLinked = futureLinked.get();  // Wait for the result

                        if (isLinked) {
                            // Generate an auth token
                            String ipAddress = RequestHandler.getClientIp(exchange);
                            String authToken = authTokenManager.generateToken(ipAddress, playerUUID, authTokenManager.isAdmin(playerUUID));  // Adjust packetLength and isAdmin as needed

                            Cookie authCookie = new CookieImpl("Authorization", authToken).setValue(authToken).setMaxAge(7200).setSecure(false);
                            exchange.setStatusCode(302);
                            exchange.setResponseCookie(authCookie);
                            exchange.getResponseHeaders().put(Headers.LOCATION, "/dashboard");
                            exchange.endExchange(); // Ends the exchange and performs the redirect
                        } else {
                        	String linkToken = PlayerServersPanel.getInstance().getLinkTokenManager().getLinkToken(playerUUID);
                        	RequestHandler.sendJsonResponse(exchange, 401, false, "Account not linked. /link " + linkToken);
                        }
                    }
                } else {
                	RequestHandler.sendJsonResponse(exchange, 401, false, "Invalid credentials.");
                }
            } catch (InterruptedException | ExecutionException e) {
                RequestHandler.sendJsonResponse(exchange, 500, false, "An error occurred while processing your login.");
                e.printStackTrace();
            }
        } else {
            exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
            exchange.getResponseSender().send("Error parsing form data.");
        }
    }
}
