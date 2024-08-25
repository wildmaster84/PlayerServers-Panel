package me.wild.api;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import me.wild.PlayerServersPanel;

public class TemplateEngine {

    // Method to load an HTML template file and replace placeholders
    public static String renderTemplate(String templatePath, Map<String, String> placeholders) throws IOException {
        StringBuilder templateContent = new StringBuilder();

        // Read the template file
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(PlayerServersPanel.getInstance().getDataFolder(), templatePath)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                templateContent.append(line).append("\n");
            }
        }

        // Replace placeholders with actual values
        String renderedContent = templateContent.toString();
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            renderedContent = renderedContent.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }

        return renderedContent;
    }
}

