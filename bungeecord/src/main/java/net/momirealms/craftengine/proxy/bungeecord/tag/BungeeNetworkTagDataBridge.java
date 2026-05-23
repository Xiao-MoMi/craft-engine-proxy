package net.momirealms.craftengine.proxy.bungeecord.tag;

import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.momirealms.craftengine.proxy.bungeecord.BungeeCordCraftEngine;
import net.momirealms.craftengine.proxy.bungeecord.platform.BungeePlayer;
import net.momirealms.craftengine.proxy.common.tag.NetworkTagData;
import net.momirealms.craftengine.proxy.common.tag.NetworkTagDataSyncService;

public class BungeeNetworkTagDataBridge implements Listener {
    public static final String IDENTIFIER = NetworkTagDataSyncService.TAG_DATA_CHANNEL;

    private final BungeeCordCraftEngine plugin;
    private final NetworkTagDataSyncService networkTagDataSyncService;

    public BungeeNetworkTagDataBridge(BungeeCordCraftEngine plugin) {
        this.plugin = plugin;
        this.networkTagDataSyncService = new NetworkTagDataSyncService(plugin);
        this.load();
    }

    public NetworkTagDataSyncService networkTagDataSyncService() {
        return this.networkTagDataSyncService;
    }

    public void load() {
        this.plugin.getProxy().getPluginManager().registerListener(this.plugin, this);
    }

    public void disable() {
        this.plugin.getProxy().getPluginManager().unregisterListener(this);
        this.networkTagDataSyncService.clear();
    }

    @EventHandler
    public void onServerConnected(ServerConnectedEvent event) {
        BungeePlayer player = this.plugin.getPlayer(event.getPlayer().getUniqueId());
        if (player != null) {
            NetworkTagData tagData = this.networkTagDataSyncService.getTagData(player);
            byte[] data = this.networkTagDataSyncService.buildTagDataBytes(tagData);
            player.sendServerPluginMessage(BungeeNetworkTagDataBridge.IDENTIFIER, data);
        }
    }
}
