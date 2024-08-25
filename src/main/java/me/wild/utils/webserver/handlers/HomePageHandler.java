package me.wild.utils.webserver.handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import me.wild.api.TemplateEngine;

import java.io.IOException;
import java.util.HashMap;
public class HomePageHandler implements HttpHandler {

    @Override
    public void handleRequest(HttpServerExchange exchange) throws IOException {
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }

        String renderedContent = TemplateEngine.renderTemplate("web/index.html",  new HashMap<>());

        // Serve the rendered content
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
        exchange.getResponseSender().send(renderedContent);
    }
}
