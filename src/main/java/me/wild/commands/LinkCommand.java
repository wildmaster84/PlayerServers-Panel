package me.wild.commands;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.UUID;

import me.wild.utils.managers.DatabaseManager;
import me.wild.utils.managers.LinkTokenManager;

public class LinkCommand extends Command {

    private final LinkTokenManager linkTokenManager;
    private final DatabaseManager databaseManager;

    public LinkCommand(LinkTokenManager linkTokenManager, DatabaseManager  databaseManager ) {
        super("link", "playerservers.link");
        this.linkTokenManager = linkTokenManager;
        this.databaseManager  = databaseManager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
    	if (!(sender instanceof ProxiedPlayer)) return;
    	
        if (args.length != 1) {
            sender.sendMessage(new TextComponent("Usage: /link <link_token>"));
            return;
        }

        String linkToken = args[0];
        UUID playerUUID = ((ProxiedPlayer) sender).getUniqueId();  // Assuming sender is a player

        // Validate the link token
        if (linkTokenManager.isValidLinkToken(playerUUID, linkToken)) {
            // Link the user's account in the database
            try {
                databaseManager.linkAccount(playerUUID);
                sender.sendMessage(new TextComponent("Your account has been successfully linked!"));
            } catch (Exception e) {
                sender.sendMessage(new TextComponent("Failed to link your account: " + e.getMessage()));
                e.printStackTrace();
            }
        } else {
            sender.sendMessage(new TextComponent("Invalid link token. Please try again."));
        }
    }
}

