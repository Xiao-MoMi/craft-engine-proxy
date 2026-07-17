package net.momirealms.craftengine.proxy.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.momirealms.craftengine.proxy.common.network.ChannelConnection;
import net.momirealms.craftengine.proxy.common.ProxyCraftEngine;
import net.momirealms.craftengine.proxy.common.tag.NetworkTagDataSyncService;
import net.momirealms.craftengine.proxy.common.util.AdventureHelper;
import net.momirealms.craftengine.proxy.velocity.network.VelocityPacketListenerManager;
import net.momirealms.craftengine.proxy.velocity.platform.VelocityPlayer;
import net.momirealms.craftengine.proxy.velocity.tag.VelocityNetworkTagDataBridge;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(
        id = "craftengine",
        name = "CraftEngine",
        version = BuildInfo.VERSION,
        authors = {"Catnies"}
)
public class VelocityCraftEngine implements ProxyCraftEngine {
    private static VelocityCraftEngine INSTANCE;
    public final ProxyServer server;
    public final Logger logger;
    public final PluginContainer pluginContainer;
    public final Path dataDirectory;
    private final Map<UUID, VelocityPlayer> players = new ConcurrentHashMap<>();
    private VelocityPacketListenerManager packetListenerManager;
    private VelocityNetworkTagDataBridge networkTagDataBridge;

    @Inject
    public VelocityCraftEngine(ProxyServer server, Logger logger, PluginContainer pluginContainer, @DataDirectory Path dataDirectory) {
        INSTANCE = this;
        this.server = server;
        this.logger = logger;
        this.pluginContainer = pluginContainer;
        this.dataDirectory = dataDirectory;
    }

    public static VelocityCraftEngine instance() {
        return Objects.requireNonNull(INSTANCE, "plugin instance is null!");
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        AdventureHelper.init();
        this.packetListenerManager = new VelocityPacketListenerManager(this);
        this.networkTagDataBridge = new VelocityNetworkTagDataBridge(this);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (this.networkTagDataBridge != null) this.networkTagDataBridge.disable();
        if (this.packetListenerManager != null) this.packetListenerManager.disable();
        if (!this.players.isEmpty()) this.players.clear();
        this.server.getEventManager().unregisterListeners(this);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        this.players.computeIfPresent(event.getPlayer().getUniqueId(), (uuid, current) ->
                current.platform() == event.getPlayer() ? null : current
        );
    }

    @Override
    public @Nullable VelocityPlayer getPlayer(UUID uuid) {
        return this.players.get(uuid);
    }

    @Override
    public void registerChannel(String channel) {
        this.server.getChannelRegistrar().register(MinecraftChannelIdentifier.from(channel));
    }

    public VelocityPlayer wrap(Player platform, ChannelConnection connection) {
        return this.players.compute(platform.getUniqueId(), (uuid, current) -> {
            if (current != null && current.connection() == connection) {
                return current;
            }
            return new VelocityPlayer(platform, connection);
        });
    }

    @Override
    public File dataFolderFile() {
        return this.dataDirectory.toFile();
    }

    @Override
    public Path dataFolderPath() {
        return this.dataDirectory;
    }

    @Override
    public VelocityPacketListenerManager packetListenerManager() {
        return this.packetListenerManager;
    }

    @Override
    public NetworkTagDataSyncService networkTagDataSyncService() {
        return this.networkTagDataBridge.networkTagDataSyncService();
    }
}
