package com.realpr.hubplus;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

@Plugin(id = "hubplus", name = "HubPlus", version = "1.0", authors = {"realprinario-ctrl"})
public final class HubPlusPlugin {
    private final ProxyServer proxy;
    private final Path dataDirectory;
    private String targetServer = "Lobby2";
    private String prefix = "&8[&9Hub&b+&8] &8»&r ";

    @Inject
    public HubPlusPlugin(ProxyServer proxy, @com.velocitypowered.api.plugin.annotation.DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loadConfig();
        var manager = proxy.getCommandManager();
        var meta = manager.metaBuilder("hub").aliases("lobby").build();
        manager.register(meta, new HubCommand());
    }

    private void loadConfig() {
        try {
            Files.createDirectories(dataDirectory);
            Path config = dataDirectory.resolve("config.yml");
            if (Files.notExists(config)) {
                try (var input = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                    if (input != null) Files.copy(input, config);
                }
            }
            if (Files.exists(config)) {
                Yaml yaml = new Yaml();
                try (Reader reader = Files.newBufferedReader(config)) {
                    Map<String, Object> values = yaml.load(reader);
                    if (values != null) {
                        Object server = values.get("target-server");
                        if (server != null) targetServer = String.valueOf(server);
                        Object messages = values.get("messages");
                        if (messages instanceof Map<?, ?> map && map.get("prefix") != null) prefix = String.valueOf(map.get("prefix"));
                    }
                }
            }
        } catch (IOException exception) {
            proxy.getConsoleCommandSource().sendMessage(msg("&cCould not load config.yml."));
        }
    }

    private final class HubCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            if (!(invocation.source() instanceof Player player)) {
                invocation.source().sendMessage(msg("&cOnly players can use this command."));
                return;
            }
            Optional<RegisteredServer> destination = proxy.getServer(targetServer);
            if (destination.isEmpty()) {
                player.sendMessage(msg("&cThe target server is unavailable."));
                return;
            }
            boolean alreadyConnected = player.getCurrentServer().map(connection -> connection.getServerInfo().getName().equalsIgnoreCase(targetServer)).orElse(false);
            if (alreadyConnected) {
                player.sendMessage(msg("&eYou are already connected to the target server."));
                return;
            }
            player.createConnectionRequest(destination.get()).connect().thenAccept(result -> {
                if (!result.isSuccessful()) player.sendMessage(msg("&cCould not connect you to the target server."));
            });
        }
    }

    private Component msg(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(prefix + text);
    }
}
