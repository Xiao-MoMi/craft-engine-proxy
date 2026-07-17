package net.momirealms.craftengine.proxy.bungeecord;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.momirealms.craftengine.proxy.bungeecord.network.BungeePacketListenerManager;
import net.momirealms.craftengine.proxy.bungeecord.platform.BungeePlayer;
import net.momirealms.craftengine.proxy.bungeecord.tag.BungeeNetworkTagDataBridge;
import net.momirealms.craftengine.proxy.common.ProxyCraftEngine;
import net.momirealms.craftengine.proxy.common.network.ChannelConnection;
import net.momirealms.craftengine.proxy.common.network.listener.PacketListenerManager;
import net.momirealms.craftengine.proxy.common.tag.NetworkTagDataSyncService;
import net.momirealms.craftengine.proxy.common.util.AdventureHelper;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BungeeCordCraftEngine extends Plugin implements ProxyCraftEngine {
    private static BungeeCordCraftEngine INSTANCE;
    private final Map<UUID, BungeePlayer> onlinePlayers = new ConcurrentHashMap<>();
    private BungeePacketListenerManager packetListenerManager;
    private BungeeNetworkTagDataBridge networkTagDataBridge;

    public static BungeeCordCraftEngine instance() {
        return Objects.requireNonNull(INSTANCE, "plugin instance is null!");
    }

    @Override
    public void onEnable() {
        INSTANCE = this;
        AdventureHelper.init();
        this.packetListenerManager = new BungeePacketListenerManager(this);
        this.networkTagDataBridge = new BungeeNetworkTagDataBridge(this);
    }

    @Override
    public void onDisable() {
        if (this.networkTagDataBridge != null) this.networkTagDataBridge.disable();
        if (!this.onlinePlayers.isEmpty()) this.onlinePlayers.clear();
    }

    @Override
    public @Nullable BungeePlayer getPlayer(UUID uuid) {
        return this.onlinePlayers.get(uuid);
    }

    @Override
    public void registerChannel(String channel) {
        this.getProxy().registerChannel(channel);
    }

    public BungeePlayer wrap(ProxiedPlayer platform, ChannelConnection connection) {
        return this.onlinePlayers.compute(platform.getUniqueId(), (uuid, current) -> {
            if (current != null && current.connection() == connection) {
                return current;
            }
            return new BungeePlayer(platform, connection);
        });
    }

    public void removePlayer(ProxiedPlayer platform) {
        this.onlinePlayers.computeIfPresent(platform.getUniqueId(), (uuid, current) ->
                current.platform() == platform ? null : current
        );
    }

    @Override
    public File dataFolderFile() {
        return this.getDataFolder();
    }

    @Override
    public Path dataFolderPath() {
        return this.getDataFolder().toPath();
    }

    @Override
    public PacketListenerManager packetListenerManager() {
        return this.packetListenerManager;
    }

    @Override
    public NetworkTagDataSyncService networkTagDataSyncService() {
        return this.networkTagDataBridge.networkTagDataSyncService();
    }
}
