package dev.flur.extrachunky;

import com.google.inject.Inject;
import dev.flur.extrachunky.command.ExtraChunkyCommand;
import dev.flur.extrachunky.command.RegisterCommand;
import dev.flur.extrachunky.platform.*;
import org.apache.logging.log4j.Logger;
import org.bstats.sponge.Metrics;
import org.spongepowered.api.Game;
import org.spongepowered.api.Server;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.lifecycle.ConstructPluginEvent;
import org.spongepowered.api.event.lifecycle.RegisterCommandEvent;
import org.spongepowered.api.event.lifecycle.StartedEngineEvent;
import org.spongepowered.api.event.lifecycle.StoppingEngineEvent;
import org.spongepowered.plugin.PluginContainer;
import org.spongepowered.plugin.builtin.jvm.Plugin;

import java.nio.file.Path;
import java.util.List;

import static dev.flur.extrachunky.platform.MessageFormatter.*;

@Plugin("extrachunky")
public class ExtraChunkySponge {
    private ExtraChunkyCore core;
    private PluginContainer container;
    private Logger logger;
    @Inject
    private Game game;
    @Inject
    @ConfigDir(sharedRoot = false)
    private Path configPath;
    @Inject
    private Metrics.Factory metricsFactory;
    private SpongePlatform platform;

    @Listener
    public void onConstructPlugin(final ConstructPluginEvent event) {
        this.container = event.plugin();
        this.logger = event.plugin().logger();
    }

    @Listener
    public void onServerStarted(final StartedEngineEvent<Server> event) {
        this.platform = new SpongePlatform(this);
        this.core = new ExtraChunkyCore(platform);

        if (!core.initialize()) {
            return;
        }

        // bStats metrics
        metricsFactory.make(28293);
    }

    @Listener
    public void onServerStopping(final StoppingEngineEvent<Server> event) {
        if (core != null) {
            core.disable();
        }
    }

    @Listener
    public void onRegisterCommand(final RegisterCommandEvent<Command.Parameterized> event) {
        final Command.Parameterized startCommand = Command.builder()
                .permission("extrachunky.command.start")
                .executor(ctx -> {
                    executeCommand("start", new SpongeSender(ctx.cause().root()), new String[]{"start"});
                    return CommandResult.success();
                })
                .build();

        final Command.Parameterized statusCommand = Command.builder()
                .permission("extrachunky.command.status")
                .executor(ctx -> {
                    executeCommand("status", new SpongeSender(ctx.cause().root()), new String[]{"status"});
                    return CommandResult.success();
                })
                .build();

        final Command.Parameterized hostStopCommand = Command.builder()
                .permission("extrachunky.command.host")
                .executor(ctx -> {
                    executeCommand("host", new SpongeSender(ctx.cause().root()), new String[]{"host", "stop"});
                    return CommandResult.success();
                })
                .build();

        final Command.Parameterized hostCommand = Command.builder()
                .permission("extrachunky.command.host")
                .addChild(hostStopCommand, "stop")
                .executor(ctx -> {
                    executeCommand("host", new SpongeSender(ctx.cause().root()), new String[]{"host"});
                    return CommandResult.success();
                })
                .build();

        final Command.Parameterized registerCommand = Command.builder()
                .permission("extrachunky.command.register")
                .addParameters(Parameter.remainingJoinedStrings().key("address").build())
                .executor(ctx -> {
                    String address = ctx.requireOne(Parameter.key("address", String.class));
                    executeCommand("register", new SpongeSender(ctx.cause().root()), new String[]{"register", address});
                    return CommandResult.success();
                })
                .build();

        final Command.Parameterized unregisterCommand = Command.builder()
                .permission("extrachunky.command.unregister")
                .executor(ctx -> {
                    if (core != null) {
                        ExtraChunkyCommand cmd = core.getCommands().get("register");
                        if (cmd instanceof RegisterCommand regCmd) {
                            regCmd.executeUnregister(new SpongeSender(ctx.cause().root()));
                        }
                    }
                    return CommandResult.success();
                })
                .build();

        final Command.Parameterized workersCommand = Command.builder()
                .permission("extrachunky.command.workers")
                .executor(ctx -> {
                    executeCommand("workers", new SpongeSender(ctx.cause().root()), new String[]{"workers"});
                    return CommandResult.success();
                })
                .build();

        final Command.Parameterized mergeCommand = Command.builder()
                .permission("extrachunky.command.merge")
                .addParameters(Parameter.remainingJoinedStrings().key("paths").build())
                .executor(ctx -> {
                    String pathsArg = ctx.requireOne(Parameter.key("paths", String.class));
                    String[] args = ("merge " + pathsArg).split(" ");
                    executeCommand("merge", new SpongeSender(ctx.cause().root()), args);
                    return CommandResult.success();
                })
                .build();

        final Command.Parameterized reloadCommand = Command.builder()
                .permission("extrachunky.command.reload")
                .executor(ctx -> {
                    if (core != null) {
                        core.getConfig().reload();
                        new SpongeSender(ctx.cause().root()).sendMessage(prefix("Configuration reloaded."));
                    }
                    return CommandResult.success();
                })
                .build();

        event.register(this.container, Command.builder()
                .permission("extrachunky.command.base")
                .addChild(startCommand, "start")
                .addChild(statusCommand, "status")
                .addChild(hostCommand, "host")
                .addChild(registerCommand, "register")
                .addChild(unregisterCommand, "unregister")
                .addChild(workersCommand, "workers")
                .addChild(mergeCommand, "merge")
                .addChild(reloadCommand, "reload")
                .executor(ctx -> {
                    showHelp(new SpongeSender(ctx.cause().root()));
                    return CommandResult.success();
                })
                .build(), "extrachunky");
    }

    private void executeCommand(String name, ExtraChunkySender sender, String[] args) {
        if (core == null) {
            return;
        }

        ExtraChunkyCommand cmd = core.getCommands().get(name);
        if (cmd != null) {
            cmd.execute(sender, args);
        }
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

    public PluginContainer getContainer() {
        return container;
    }

    public Logger getLogger() {
        return logger;
    }

    public Game getGame() {
        return game;
    }

    public Path getConfigPath() {
        return configPath;
    }
}
