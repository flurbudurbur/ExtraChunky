package dev.flur.extrachunky;

import dev.flur.extrachunky.command.ExtraChunkyCommand;
import dev.flur.extrachunky.command.RegisterCommand;
import dev.flur.extrachunky.platform.*;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static dev.flur.extrachunky.platform.MessageFormatter.*;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

@Mod("extrachunky")
public class ExtraChunkyForge {
    private static ExtraChunkyForge instance;
    private ExtraChunkyCore core;
    private MinecraftServer server;
    private ForgePlatform platform;

    public ExtraChunkyForge() {
        instance = this;
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        this.server = event.getServer();
        this.platform = new ForgePlatform(this, server);
        this.core = new ExtraChunkyCore(platform);

        if (!core.initialize()) {
            return;
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (core != null) {
            core.disable();
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();

        dispatcher.register(
                literal("extrachunky")
                        .requires(source -> {
                            MinecraftServer server = source.getServer();
                            if (server != null && server.isSingleplayer()) {
                                return true;
                            }
                            return source.hasPermission(2);
                        })
                        .executes(context -> {
                            showHelp(new ForgeSender(context.getSource()));
                            return 1;
                        })
                        .then(literal("start")
                                .executes(context -> executeCommand("start", context.getSource(), new String[]{"start"})))
                        .then(literal("status")
                                .executes(context -> executeCommand("status", context.getSource(), new String[]{"status"})))
                        .then(literal("host")
                                .executes(context -> executeCommand("host", context.getSource(), new String[]{"host"}))
                                .then(literal("stop")
                                        .executes(context -> executeCommand("host", context.getSource(), new String[]{"host", "stop"}))))
                        .then(literal("register")
                                .then(argument("address", greedyString())
                                        .executes(context -> executeCommand("register", context.getSource(),
                                                new String[]{"register", getString(context, "address")}))))
                        .then(literal("unregister")
                                .executes(context -> {
                                    if (core != null) {
                                        var cmd = core.getCommands().get("register");
                                        if (cmd instanceof RegisterCommand regCmd) {
                                            regCmd.executeUnregister(new ForgeSender(context.getSource()));
                                        }
                                    }
                                    return 1;
                                }))
                        .then(literal("workers")
                                .executes(context -> executeCommand("workers", context.getSource(), new String[]{"workers"})))
                        .then(literal("merge")
                                .then(argument("paths", greedyString())
                                        .executes(context -> {
                                            String pathsArg = getString(context, "paths");
                                            String[] args = ("merge " + pathsArg).split(" ");
                                            return executeCommand("merge", context.getSource(), args);
                                        })))
                        .then(literal("reload")
                                .executes(context -> {
                                    if (core != null) {
                                        core.getConfig().reload();
                                        new ForgeSender(context.getSource()).sendMessage(prefix("Configuration reloaded."));
                                    }
                                    return 1;
                                }))
        );
    }

    private int executeCommand(String name, net.minecraft.commands.CommandSourceStack source, String[] args) {
        if (core == null) {
            return 0;
        }

        ExtraChunkyCommand cmd = core.getCommands().get(name);
        if (cmd != null) {
            cmd.execute(new ForgeSender(source), args);
        }
        return 1;
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

    public ExtraChunkyCore getCore() {
        return core;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public static ExtraChunkyForge getInstance() {
        return instance;
    }
}
