package net.momirealms.craftengine.proxy.bungeecord.platform;

import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.momirealms.craftengine.proxy.bungeecord.BungeeCordCraftEngine;
import net.momirealms.craftengine.proxy.common.network.ChannelConnection;
import net.momirealms.craftengine.proxy.common.platform.BackendServer;
import net.momirealms.craftengine.proxy.common.platform.ProxyPlayer;

import java.util.Locale;
import java.util.UUID;

public class BungeePlayer extends ProxyPlayer {
    private final ProxiedPlayer platform;

    public BungeePlayer(ProxiedPlayer platform, ChannelConnection connection) {
        super(connection);
        this.platform = platform;
    }

    public static BungeePlayer wrap(ProxiedPlayer platform, ChannelConnection connection) {
        return BungeeCordCraftEngine.instance().wrap(platform, connection);
    }

    @Override
    public UUID uuid() {
        return platform.getUniqueId();
    }

    @Override
    public ProxiedPlayer platform() {
        return this.platform;
    }

    @Override
    public BackendServer server() {
        Server server = platform.getServer();
        return server != null ? BungeeBackendServer.wrapper(server) : null;
    }

    @Override
    public boolean sendServerPluginMessage(String channel, byte[] data) {
        Server server = platform.getServer();
        if (server != null) {
            server.sendData(channel, data);
            return true;
        }
        return false;
    }

    @Override
    public Locale locale() {
        return platform.getLocale();
    }

    @Override
    public void kick(String reason) {
        this.platform.disconnect(new TextComponent(reason));
    }

}
