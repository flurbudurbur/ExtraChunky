package dev.flur.extrachunky.platform;

import org.bukkit.entity.Player;

public class BukkitPlayer extends BukkitSender {
    private final Player player;

    public BukkitPlayer(Player player) {
        super(player);
        this.player = player;
    }

    @Override
    public boolean isPlayer() {
        return true;
    }

    public Player getBukkitPlayer() {
        return player;
    }
}
