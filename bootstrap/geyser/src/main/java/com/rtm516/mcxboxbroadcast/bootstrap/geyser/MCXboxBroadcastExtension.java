package com.rtm516.mcxboxbroadcast.bootstrap.geyser;

import com.rtm516.mcxboxbroadcast.core.Logger;
import com.rtm516.mcxboxbroadcast.core.SessionInfo;
import com.rtm516.mcxboxbroadcast.core.SessionManager;
import com.rtm516.mcxboxbroadcast.core.configs.ExtensionConfig;
import com.rtm516.mcxboxbroadcast.core.exceptions.SessionCreationException;
import com.rtm516.mcxboxbroadcast.core.exceptions.SessionUpdateException;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.command.Command;
import org.geysermc.geyser.api.command.CommandSource;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCommandsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.extension.Extension;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

public class MCXboxBroadcastExtension implements Extension {
    Logger logger;
    SessionManager sessionManager;
    SessionInfo sessionInfo;
    ExtensionConfig config;

    @Subscribe
    public void onCommandDefine(GeyserDefineCommandsEvent event) {
        event.register(Command.builder(this)
            .source(CommandSource.class)
            .name("restart")
            .description("Restart the connection to Xbox Live.")
            .executor((source, command, args) -> {
                if (!source.isConsole()) {
                    source.sendMessage("This command can only be ran from the console.");
                    return;
                }

                restart();
            })
            .build());

        event.register(Command.builder(this)
            .source(CommandSource.class)
            .name("dumpsession")
            .description("Dump the current session to json files.")
            .executor((source, command, args) -> {
                if (!source.isConsole()) {
                    source.sendMessage("This command can only be ran from the console.");
                    return;
                }

                sessionManager.dumpSession();
            })
            .build());

        event.register(Command.builder(this)
            .source(CommandSource.class)
            .name("accounts")
            .description("Manage sub-accounts.")
            .executor((source, command, args) -> {
                if (!source.isConsole()) {
                    source.sendMessage("This command can only be ran from the console.");
                    return;
                }

                if (args.length < 2) {
                    if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
                        sessionManager.listSessions();
                        return;
                    }

                    source.sendMessage("Usage:");
                    source.sendMessage("accounts list");
                    source.sendMessage("accounts add/remove <sub-session-id>");
                    return;
                }

                switch (args[0].toLowerCase()) {
                    case "add":
                        sessionManager.addSubSession(args[1]);
                        break;
                    case "remove":
                        sessionManager.removeSubSession(args[1]);
                        break;
                    default:
                        source.sendMessage("Unknown accounts command: " + args[0]);
                }
            })
            .build());
    }

    private void restart() {
        sessionManager.shutdown();

        sessionManager = new SessionManager(this.dataFolder().toString(), logger);

        createSession();
    }

    @Subscribe
    public void onPostInitialize(GeyserPostInitializeEvent event) {
        logger = new ExtensionLoggerImpl(this.logger());
        sessionManager = new SessionManager(this.dataFolder().toString(), logger);

        // Load the config file
        config = ConfigLoader.load(this, MCXboxBroadcastExtension.class, ExtensionConfig.class);

        // Pull onto another thread so we don't hang the main thread
        new Thread(() -> {
            // Get the ip to broadcast
            String ip = config.remoteAddress();
            if (ip.equals("auto")) {
                ip = this.geyserApi().bedrockListener().address();

                // This is the most reliable for getting the main local IP
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress("geysermc.org", 80));
                    ip = socket.getLocalAddress().getHostAddress();
                } catch (IOException e1) {
                    try {
                        // Fallback to the normal way of getting the local IP
                        ip = InetAddress.getLocalHost().getHostAddress();
                    } catch (UnknownHostException ignored) {
                    }
                }
            }

            // Get the port to broadcast
            int port = this.geyserApi().bedrockListener().port();
            if (!config.remotePort().equals("auto")) {
                port = Integer.parseInt(config.remotePort());
            }

            // Create the session information based on the Geyser config
            sessionInfo = new SessionInfo();
            sessionInfo.setHostName(this.geyserApi().bedrockListener().primaryMotd());
            sessionInfo.setWorldName(this.geyserApi().bedrockListener().secondaryMotd());
            sessionInfo.setVersion(this.geyserApi().defaultRemoteServer().minecraftVersion());
            sessionInfo.setProtocol(this.geyserApi().defaultRemoteServer().protocolVersion());
            sessionInfo.setPlayers(this.geyserApi().onlineConnections().size());
            sessionInfo.setMaxPlayers(GeyserImpl.getInstance().getConfig().getMaxPlayers()); // TODO Find API equivalent

            sessionInfo.setIp(ip);
            sessionInfo.setPort(port);

            createSession();
        }).start();
    }

    private void createSession() {
        // Create the Xbox session
        sessionManager.restartCallback(this::restart);
        try {
            sessionManager.init(sessionInfo, config.friendSync());
        } catch (SessionCreationException | SessionUpdateException e) {
            sessionManager.logger().error("Failed to create xbox session!", e);
            return;
        }

        // Set up the auto friend sync
        sessionManager.friendManager().initAutoFriend(config.friendSync());

        // Start the update timer
        sessionManager.scheduledThread().scheduleWithFixedDelay(this::tick, config.updateInterval(), config.updateInterval(), TimeUnit.SECONDS);
    }

    private void tick() {
        // Update the player count for the session
        try {
            sessionInfo.setPlayers(this.geyserApi().onlineConnections().size());
            sessionManager.updateSession(sessionInfo);
        } catch (SessionUpdateException e) {
            sessionManager.logger().error("Failed to update session information!", e);
        }
    }
}
