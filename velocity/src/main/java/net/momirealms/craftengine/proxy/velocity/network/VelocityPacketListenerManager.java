package net.momirealms.craftengine.proxy.velocity.network;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import io.netty.channel.Channel;
import net.momirealms.craftengine.proxy.common.ProxyCraftEngine;
import net.momirealms.craftengine.proxy.common.network.ChannelConnection;
import net.momirealms.craftengine.proxy.common.network.listener.PacketListenerManager;
import net.momirealms.craftengine.proxy.common.network.packet.PacketRegistration;
import net.momirealms.craftengine.proxy.common.network.protocol.PacketSide;
import net.momirealms.craftengine.proxy.common.network.protocol.packettype.PacketType;
import net.momirealms.craftengine.proxy.velocity.VelocityCraftEngine;
import net.momirealms.craftengine.proxy.velocity.network.inject.PacketPipelineInjector;
import net.momirealms.craftengine.proxy.velocity.platform.VelocityPlayer;
import net.momirealms.craftengine.proxy.velocity.util.VelocityAdventureHelper;

import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class VelocityPacketListenerManager extends PacketListenerManager {
    private final VelocityCraftEngine plugin;
    private final PacketPipelineInjector pipelineInjector; // 负责 Velocity Netty pipeline 注入
    private final PacketListenerManager.ErrorHandler errorHandler;
    private final ConcurrentMap<Channel, ChannelConnection> connectionsByChannel = new ConcurrentHashMap<>(); // Channel 生命周期索引
    private final ConcurrentMap<SocketAddress, ChannelConnection> connectionsByAddress = new ConcurrentHashMap<>(); // 登录事件绑定玩家
    private volatile boolean loaded;

    public VelocityPacketListenerManager(VelocityCraftEngine plugin) {
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

        // 先注册内部状态监听, 再开始
        super.registerInternalRegistrations();
        // 注册常规监听器
        this.registerPacketListeners();
        // 注册玩家监听器, 注入管道, 接入 Netty 流量
        this.plugin.server.getEventManager().register(this.plugin, this);
        this.pipelineInjector.inject();
    }

    public void disable() {
        if (!this.loaded) {
            return;
        }
        this.loaded = false;
        this.plugin.server.getEventManager().unregisterListener(this.plugin, this);

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

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        // Netty channel 早于 Velocity player 创建, 登录后再绑定玩家对象
        ChannelConnection connection = this.connectionsByAddress.get(event.getPlayer().getRemoteAddress());
        if (connection == null) {
            VelocityAdventureHelper.disconnect(event.getPlayer(), "[CraftEngine] Can't initialize ChannelConnection for " + event.getPlayer().getUsername());
            this.plugin.logger.error("Can't initialize ChannelConnection for {}", event.getPlayer().getUsername());
            return;
        }
        VelocityPlayer player = VelocityPlayer.wrap(event.getPlayer(), connection);
        connection.bind(player);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        // 保留连接对象到 Channel 关闭, 这里只解除玩家引用
        ChannelConnection connection = this.connectionsByAddress.get(event.getPlayer().getRemoteAddress());
        if (connection != null) {
            connection.unbind(event.getPlayer().getUniqueId());
        }
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
        this.plugin.logger.warn("An error occurred when handling packet " + packetId + " (" + side + ")", throwable);
    }
}
