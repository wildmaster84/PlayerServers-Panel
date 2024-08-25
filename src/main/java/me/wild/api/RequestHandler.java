package me.wild.api;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.util.Headers;

public class RequestHandler {
	private static Gson gson = new Gson();
	
	public static void sendJsonResponse(HttpServerExchange exchange, int statusCode, boolean success, String message) {
        Map<String, Object> jsonResponse = new HashMap<>();
        jsonResponse.put("success", success);
        jsonResponse.put("message", message);

        String json = gson.toJson(jsonResponse);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
        exchange.setStatusCode(statusCode);
        exchange.getResponseSender().send(json);
    }

    // Overloaded method to allow custom JSON data along with success and message
    public static void sendJsonResponse(HttpServerExchange exchange, int statusCode, boolean success, String message, Map<String, Object> additionalData) {
        Map<String, Object> jsonResponse = new HashMap<>(additionalData);
        jsonResponse.put("success", success);
        jsonResponse.put("message", message);

        String json = gson.toJson(jsonResponse);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
        exchange.setStatusCode(statusCode);
        exchange.getResponseSender().send(json);
    }

    public static String getFormValue(FormData formData, String fieldName) {
        FormData.FormValue formValue = formData.getFirst(fieldName);
        return (formValue != null) ? formValue.getValue() : null;
    }
    
    public static String getClientIp(HttpServerExchange exchange) {
        String ipAddress = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = exchange.getSourceAddress().getHostString();
        }
        return ipAddress;
    }
    
    @SuppressWarnings("unchecked")
	public static Map<String, String> getJsonBody(HttpServerExchange exchange) throws IOException {
        return gson.fromJson(new InputStreamReader(exchange.getInputStream(), StandardCharsets.UTF_8), Map.class);
    }

}
