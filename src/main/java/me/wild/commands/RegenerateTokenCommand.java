package me.wild.commands;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;

import java.util.UUID;

import me.wild.PlayerServersPanel;

public class RegenerateTokenCommand extends Command {

    public RegenerateTokenCommand() {
        super("regenerateToken", "playerservers.admin");  // Set appropriate permission
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        String newToken = UUID.randomUUID().toString();  // Generate a new token
        PlayerServersPanel.getInstance().regenerateApiToken();

        sender.sendMessage(new TextComponent("API token has been regenerated. New token: " + newToken));
    }
}
