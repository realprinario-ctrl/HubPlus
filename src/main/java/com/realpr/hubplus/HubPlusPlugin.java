package com.realpr.hubplus;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Plugin(id = "hubplus", name = "HubPlus", version = "1.0.0", authors = {"realprinario-ctrl"})
public final class HubPlusPlugin {
    private final ProxyServer proxy;
    private final Path dataDirectory;
    private final Map<String, String> messages = new HashMap<>();
    private String targetServer = "Lobby2";
    private String prefix = "&8[&9Hub&b+&8] &8»&r ";

    @Inject
    public HubPlusPlugin(ProxyServer proxy, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loadConfig();
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("hub").aliases("lobby").build(),
                new HubCommand()
        );
        proxy.getConsoleCommandSource().sendMessage(Component.text("[HubPlus] Enabled. Target server: " + targetServer));
    }

    private void loadConfig() {
        try {
            Files.createDirectories(dataDirectory);
            Path file = dataDirectory.resolve("config.yml");
            if (Files.notExists(file)) {
                try (var input = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                    if (input != null) Files.copy(input, file);
                }
            }
            if (!Files.exists(file)) return;

            boolean inMessages = false;
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.equals("messages:")) {
                    inMessages = true;
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon <= 0) continue;
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1).replace("''", "'");
                } else if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                if (!inMessages && key.equals("target-server")) targetServer = value;
                if (inMessages) messages.put(key, value);
            }
            prefix = messages.getOrDefault("prefix", prefix);
        } catch (IOException exception) {
            proxy.getConsoleCommandSource().sendMessage(Component.text("[HubPlus] Could not load config.yml: " + exception.getMessage()));
        }
    }

    private final class HubCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            if (!(invocation.source() instanceof Player player)) {
                invocation.source().sendMessage(message("players-only", "&cOnly players can use this command."));
                return;
            }

            Optional<RegisteredServer> destination = proxy.getServer(targetServer);
            if (destination.isEmpty()) {
                player.sendMessage(message("unavailable", "&cThe target server is currently unavailable."));
                return;
            }

            boolean alreadyConnected = player.getCurrentServer()
                    .map(connection -> connection.getServerInfo().getName().equalsIgnoreCase(targetServer))
                    .orElse(false);
            if (alreadyConnected) {
                player.sendMessage(message("already-connected", "&eYou are already connected to the target server."));
                return;
            }

            player.createConnectionRequest(destination.get()).connect().thenAccept(result -> {
                if (!result.isSuccessful()) {
                    player.sendMessage(message("connection-failed", "&cCould not connect you to the target server."));
                }
            });
        }
    }

    private Component message(String key, String fallback) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(prefix + messages.getOrDefault(key, fallback));
    }
}
