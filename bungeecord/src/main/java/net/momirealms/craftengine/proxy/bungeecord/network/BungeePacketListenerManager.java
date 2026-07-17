package net.momirealms.craftengine.proxy.bungeecord.network;

import io.netty.channel.Channel;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.momirealms.craftengine.proxy.bungeecord.BungeeCordCraftEngine;
import net.momirealms.craftengine.proxy.bungeecord.network.inject.PacketPipelineInjector;
import net.momirealms.craftengine.proxy.bungeecord.platform.BungeePlayer;
import net.momirealms.craftengine.proxy.common.ProxyCraftEngine;
import net.momirealms.craftengine.proxy.common.network.ChannelConnection;
import net.momirealms.craftengine.proxy.common.network.listener.PacketListenerManager;
import net.momirealms.craftengine.proxy.common.network.packet.PacketRegistration;
import net.momirealms.craftengine.proxy.common.network.protocol.PacketSide;
import net.momirealms.craftengine.proxy.common.network.protocol.packettype.PacketType;

import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class BungeePacketListenerManager extends PacketListenerManager implements Listener {
    private final BungeeCordCraftEngine plugin;
    private final PacketPipelineInjector pipelineInjector; // 负责 Bungee Netty pipeline 注入
    private final PacketListenerManager.ErrorHandler errorHandler;
    private final ConcurrentMap<Channel, ChannelConnection> connectionsByChannel = new ConcurrentHashMap<>(); // Channel 生命周期索引
    private final ConcurrentMap<SocketAddress, ChannelConnection> connectionsByAddress = new ConcurrentHashMap<>(); // 登录事件绑定玩家
    private volatile boolean loaded;

    public BungeePacketListenerManager(BungeeCordCraftEngine plugin) {
        super();
        this.plugin = plugin;
        this.errorHandler = this::handlePacketError;
        this.pipelineInjector = new PacketPipelineInjector(
                plugin,
                this::handle,
                this::addConnection,
                this::removeConnection
        );
        this.load();
    }

    public void load() {
        if (this.loaded) {
            return;
        }
        PacketType.prepare();
        this.loaded = true;

        // 注册内部状态监听器
        super.registerInternalRegistrations();
        // 注册常规监听器
        this.registerPacketListeners();
        // 注册玩家监听器, 注入管道, 接入 Netty 流量
        ProxyServer.getInstance().getPluginManager().registerListener(this.plugin, this);
        this.pipelineInjector.inject();
    }

    public void disable() {
        if (!this.loaded) {
            return;
        }
        this.loaded = false;
        ProxyServer.getInstance().getPluginManager().unregisterListener(this);

        // 解除内部监听, 避免 disable 后继续修改连接状态
        for (PacketRegistration registration : this.internalRegistrations) {
            registration.unregister();
        }
        this.internalRegistrations.clear();
        this.pipelineInjector.uninject();

        // 已经建立的 Channel 不会重新经过 initializer, 需要主动移除 handler
        for (ChannelConnection connection : this.connectionsByChannel.values()) {
            Channel channel = connection.channel();
            if (channel.isOpen()) {
                channel.eventLoop().execute(() -> PacketPipelineInjector.removeHandlers(channel));
            }
        }
        this.connectionsByChannel.clear();
        this.connectionsByAddress.clear();
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        // Netty channel 早于 Bungee player 创建, 登录后再绑定玩家对象
        ChannelConnection connection = this.connectionsByAddress.get(event.getPlayer().getSocketAddress());
        if (connection == null) {
            event.getPlayer().disconnect(new TextComponent("[CraftEngine] Can't initialize ChannelConnection for " + event.getPlayer().getDisplayName()));
            this.plugin.getLogger().severe("Can't initialize ChannelConnection for " + event.getPlayer().getDisplayName());
            return;
        }
        BungeePlayer player = BungeePlayer.wrap(event.getPlayer(), connection);
        connection.bind(player);
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        // 保留连接对象到 Channel 关闭, 这里只解除玩家引用并删除玩家包装缓存
        ChannelConnection connection = this.connectionsByAddress.get(event.getPlayer().getSocketAddress());
        if (connection != null) {
            connection.unbind(event.getPlayer().getUniqueId());
        }
        this.plugin.removePlayer(event.getPlayer());
    }

    private void addConnection(ChannelConnection connection) {
        Channel channel = connection.channel();
        this.connectionsByChannel.put(channel, connection);
        SocketAddress remoteAddress = channel.remoteAddress();
        if (remoteAddress != null) {
            this.connectionsByAddress.put(remoteAddress, connection);
        }
    }

    private void removeConnection(ChannelConnection connection) {
        Channel channel = connection.channel();
        this.connectionsByChannel.remove(channel);
        SocketAddress remoteAddress = channel.remoteAddress();
        if (remoteAddress != null) {
            this.connectionsByAddress.remove(remoteAddress, connection);
        }
    }

    @Override
    public ErrorHandler errorHandler() {
        return this.errorHandler;
    }

    @Override
    public ProxyCraftEngine plugin() {
        return this.plugin;
    }

    private void handlePacketError(int packetId, PacketSide side, Throwable throwable) {
        this.plugin.getLogger().warning("An error occurred when handling packet " + packetId + " (" + side + ")");
    }
}
