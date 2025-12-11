package dev.flur.extrachunky.platform;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class BukkitSender implements ExtraChunkySender {
    protected final CommandSender sender;

    public BukkitSender(CommandSender sender) {
        this.sender = sender;
    }

    @Override
    public void sendMessage(String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    @Override
    public String getName() {
        return sender.getName();
    }

    @Override
    public boolean hasPermission(String permission) {
        return sender.hasPermission(permission);
    }

    @Override
    public boolean isPlayer() {
        return false;
    }
}
