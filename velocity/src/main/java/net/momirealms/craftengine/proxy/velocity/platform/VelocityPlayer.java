package net.momirealms.craftengine.proxy.velocity.platform;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.kyori.adventure.text.Component;
import net.momirealms.craftengine.proxy.common.network.ChannelConnection;
import net.momirealms.craftengine.proxy.common.platform.BackendServer;
import net.momirealms.craftengine.proxy.common.platform.ProxyPlayer;
import net.momirealms.craftengine.proxy.velocity.VelocityCraftEngine;

import java.util.Locale;
import java.util.UUID;

public class VelocityPlayer extends ProxyPlayer {
    private final Player platform;

    public VelocityPlayer(Player platform, ChannelConnection connection) {
        super(connection);
        this.platform = platform;
    }

    public static VelocityPlayer wrap(Player platform, ChannelConnection connection) {
        return VelocityCraftEngine.instance().wrap(platform, connection);
    }

    @Override
    public UUID uuid() {
        return this.platform.getUniqueId();
    }

    @Override
    public Player platform() {
        return platform;
    }

    @Override
    public BackendServer server() {
        return this.platform.getCurrentServer()
                .map(ServerConnection::getServer)
                .map(VelocityBackendServer::wrapper)
                .orElse(null);
    }

    @Override
    public boolean sendServerPluginMessage(String channel, byte[] data) {
        return this.platform.getCurrentServer()
                .map(it -> it.sendPluginMessage(MinecraftChannelIdentifier.from(channel), data))
                .orElse(false);
    }

    @Override
    public Locale locale() {
        return this.platform.getEffectiveLocale();
    }

    @Override
    public void kick(String reason) {
        this.platform.disconnect(Component.text(reason));
    }

}
