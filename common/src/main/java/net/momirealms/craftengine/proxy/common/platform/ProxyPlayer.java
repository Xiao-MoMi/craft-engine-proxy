package net.momirealms.craftengine.proxy.common.platform;

import net.momirealms.craftengine.proxy.common.network.ChannelConnection;
import net.momirealms.craftengine.proxy.common.network.resourcepack.ResourcePackSession;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 提供平台代理玩家共用的连接状态和操作定义.
 */
public abstract class ProxyPlayer {
    private final ChannelConnection connection; // 玩家当前使用的代理连接
    private final ResourcePackSession resourcePackSession; // 本次登录期间的资源包处理记录

    protected ProxyPlayer(ChannelConnection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.resourcePackSession = new ResourcePackSession();
    }

    public abstract UUID uuid();

    public abstract Object platform();

    public abstract BackendServer server();

    public final ChannelConnection connection() {
        return this.connection;
    }

    public final ResourcePackSession resourcePackSession() {
        return this.resourcePackSession;
    }

    public abstract boolean sendServerPluginMessage(String channel, byte[] data);

    public abstract Locale locale();

    public abstract void kick(String reason);
}
