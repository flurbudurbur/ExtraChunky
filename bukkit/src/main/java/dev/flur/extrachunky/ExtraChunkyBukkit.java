package dev.flur.extrachunky;

import dev.flur.extrachunky.command.ExtraChunkyCommand;
import dev.flur.extrachunky.command.RegisterCommand;
import dev.flur.extrachunky.platform.*;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static dev.flur.extrachunky.platform.MessageFormatter.*;

public final class ExtraChunkyBukkit extends JavaPlugin implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "start", "status", "merge", "reload", "host", "register", "unregister", "workers"
    );

    private ExtraChunkyCore core;
    private BukkitPlatform platform;

    @Override
    public void onEnable() {
        this.platform = new BukkitPlatform(this);
        this.core = new ExtraChunkyCore(platform);

        if (!core.initialize()) {
            setEnabled(false);
            return;
        }

        // Register commands
        var command = getCommand("extrachunky");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        // bStats metrics
        new Metrics(this, 28293);
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.disable();
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        BukkitSender bukkitSender = sender instanceof Player player
                ? new BukkitPlayer(player)
                : new BukkitSender(sender);

        if (args.length == 0) {
            showHelp(bukkitSender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        // Handle unregister specially since it's in RegisterCommand
        if (subCommand.equals("unregister")) {
            var registerCmd = core.getCommands().get("register");
            if (registerCmd instanceof RegisterCommand regCmd) {
                return regCmd.executeUnregister(bukkitSender);
            }
            return true;
        }

        // Handle reload
        if (subCommand.equals("reload")) {
            core.getConfig().reload();
            bukkitSender.sendMessage(prefix("Configuration reloaded."));
            return true;
        }

        ExtraChunkyCommand cmd = core.getCommands().get(subCommand);
        if (cmd != null) {
            return cmd.execute(bukkitSender, args);
        }

        showHelp(bukkitSender);
        return true;
    }

    private void showHelp(ExtraChunkySender sender) {
        sender.sendMessage(header("ExtraChunky Commands"));
        sender.sendMessage(NORMAL + highlight("/extrachunky start") + " - Start chunk generation");
        sender.sendMessage(NORMAL + highlight("/extrachunky status") + " - Show generation progress");
        sender.sendMessage(NORMAL + highlight("/extrachunky host [stop]") + " - Start/stop hosting for workers");
        sender.sendMessage(NORMAL + highlight("/extrachunky register <host:port>") + " - Connect to a host as worker");
        sender.sendMessage(NORMAL + highlight("/extrachunky unregister") + " - Disconnect from host");
        sender.sendMessage(NORMAL + highlight("/extrachunky workers") + " - List connected workers (host only)");
        sender.sendMessage(NORMAL + highlight("/extrachunky merge <paths...>") + " - Merge region files from other instances");
        sender.sendMessage(NORMAL + highlight("/extrachunky reload") + " - Reload configuration");
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String input = args[0].toLowerCase();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(input)) {
                    completions.add(sub);
                }
            }
            return completions;
        }

        return new ArrayList<>();
    }

    public ExtraChunkyCore getCore() {
        return core;
    }
}
