package me.wild.utils.webserver.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import me.wild.api.RequestHandler;
import me.wild.utils.managers.AuthTokenManager;
import net.cakemine.playerservers.bungee.PlayerServers;
import net.cakemine.playerservers.bungee.objects.PlayerServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class ServerLogHandler implements HttpHandler {

    private final AuthTokenManager authTokenManager;
    private static final Logger LOGGER = Logger.getLogger(ServerLogHandler.class.getName());
    private Queue<String> logQueue = new LinkedList<>();
    private long eventCounter = 0;

    public ServerLogHandler(AuthTokenManager authTokenManager) {
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

        exchange.startBlocking(); // Start blocking mode for stream operations
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/event-stream");
        exchange.getResponseHeaders().put(Headers.CACHE_CONTROL, "no-cache");
        exchange.getResponseHeaders().put(Headers.CONNECTION, "keep-alive");

        String serverId = exchange.getQueryParameters().get("server_id").getFirst();

        PlayerServer server = PlayerServers.getApi().getServerMap().get(serverId);
        if (server == null) {
            RequestHandler.sendJsonResponse(exchange, 404, false, "Server not found.");
            return;
        }

        // Tail the log file
        tailLogFile(exchange, server.getServerLog());
    }

    private void tailLogFile(HttpServerExchange exchange, BufferedReader bufferedReader) {
        long lastReceivedEvent = 1L;
        try (BufferedReader reader = bufferedReader) {
            String line;

            reader.lines().forEach(lineContent -> {});

            // Resend missed logs if any
            resendMissedLogs(exchange, lastReceivedEvent);

            while (!exchange.isComplete()) {
                while ((line = reader.readLine()) != null) {
                    eventCounter++;
                    addToLogQueue(line);  // Add log line to the queue with overflow protection
                    sendEvent(exchange, line, eventCounter);
                    exchange.getOutputStream().flush();

                }
                try {
	                Thread.sleep(5000); // Wait 500 milliseconds before trying again
	            } catch (InterruptedException e) {
	                Thread.currentThread().interrupt();
	                break;
	            }
            }

        } catch (IOException e) {
            LOGGER.warning("Exception occurred while streaming log: " + e.getMessage());
            // Handle broken pipe or client disconnect gracefully
            if ("Broken pipe".equals(e.getMessage())) {
                LOGGER.warning("Client disconnected");
            }
        } finally {
            exchange.endExchange();
        }
    }

    private void resendMissedLogs(HttpServerExchange exchange, long lastEventId) throws IOException {
        if (lastEventId == -1) {
        	lastEventId = 1L;
        }

        for (String logLine : logQueue) {
            if (eventCounter > lastEventId) {
                sendEvent(exchange, logLine, eventCounter);
            }
        }
    }

    private void sendEvent(HttpServerExchange exchange, String data, long eventId) throws IOException {
        if (exchange.isComplete()) {
            return;
        }
        exchange.getOutputStream().write(("id: " + eventId + "\n").getBytes());
        exchange.getOutputStream().write(("data: " + sanitizeLog(data) + "\n\n").getBytes());
    }

    private void addToLogQueue(String line) {
        synchronized (logQueue) {
            logQueue.add(line);  // Add the new log entry
        }
    }

    // Regular expressions for sanitizing log data
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile("[\\x00-\\x1F\\x7F]");
    private static final Pattern HTML_SPECIAL_CHARS_PATTERN = Pattern.compile("[&<>\"']");

    public static String sanitizeLog(String input) {
        // Step 1: Remove control characters
        String sanitized = CONTROL_CHAR_PATTERN.matcher(input).replaceAll("");

        // Step 2: Escape special HTML characters
        sanitized = HTML_SPECIAL_CHARS_PATTERN.matcher(sanitized).replaceAll(match -> {
            switch (match.group()) {
                case "&": return "&amp;";
                case "<": return "&lt;";
                case ">": return "&gt;";
                case "\"": return "&quot;";
                case "'": return "&#39;";
                default: return match.group();
            }
        });

        sanitized = HTML_TAG_PATTERN.matcher(sanitized).replaceAll("");

        return colorLog(sanitized);
    }

    private static String colorLog(String data) {
        if (data.contains("ERROR")) {
            return "<span style='color:#bb2f2f;'>" + data.replaceAll("[^\\x20-\\x7E]", "") + "</span>";
        } else if (data.contains("WARN")) {
            return "<span style='color:#ffc107;'>" + data.replaceAll("[^\\x20-\\x7E]", "") + "</span>";
        } else if (data.contains("INFO")) {
            return "<span style='color:#00bcd4;'>" + data.replaceAll("[^\\x20-\\x7E]", "") + "</span>";
        } else {
            return "<span>" + data.replaceAll("[^\\x20-\\x7E]", "") + "</span>";
        }
    }
}
