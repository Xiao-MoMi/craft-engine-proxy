package net.momirealms.craftengine.proxy.velocity.network;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
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
import net.momirealms.sparrow.reflection.clazz.SparrowClass;
import net.momirealms.sparrow.reflection.method.matcher.MethodMatcher;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class VelocityPacketListenerManager extends PacketListenerManager {
    private static final String CONNECTED_PLAYER_CLASS_NAME = "com.velocitypowered.proxy.connection.client.ConnectedPlayer";
    private static final String MINECRAFT_CONNECTION_CLASS_NAME = "com.velocitypowered.proxy.connection.MinecraftConnection";
    private static final MethodHandle GET_PLAYER_CONNECTION_METHOD; // ConnectedPlayer#getConnection()
    private static final MethodHandle GET_CONNECTION_CHANNEL_METHOD; // MinecraftConnection#getChannel()

    private final VelocityCraftEngine plugin;
    private final PacketPipelineInjector pipelineInjector; // 负责 Velocity Netty pipeline 注入
    private final PacketListenerManager.ErrorHandler errorHandler;
    private final ConcurrentMap<Channel, ChannelConnection> connectionsByChannel = new ConcurrentHashMap<>(); // Channel 生命周期索引
    private volatile boolean loaded;

    static {
        Class<?> connectedPlayerClass = SparrowClass.findNoRemap(CONNECTED_PLAYER_CLASS_NAME);
        GET_PLAYER_CONNECTION_METHOD = SparrowClass.of(connectedPlayerClass).getDeclaredSparrowMethod(MethodMatcher.named("getConnection")).unreflect();
        Class<?> minecraftConnectionClass = SparrowClass.findNoRemap(MINECRAFT_CONNECTION_CLASS_NAME);
        GET_CONNECTION_CHANNEL_METHOD = SparrowClass.of(minecraftConnectionClass).getDeclaredSparrowMethod(MethodMatcher.named("getChannel")).unreflect();
    }

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
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        // Netty channel 早于 Velocity player 创建, 登录后再绑定玩家对象.
        // 玩家远程地址可能被 PROXY protocol 改写 (如 haproxy-detector), 因此按 channel 关联而不是地址
        Player player = event.getPlayer();
        ChannelConnection connection = this.connectionByPlayer(player);
        if (connection == null) {
            VelocityAdventureHelper.disconnect(player, "[CraftEngine] Can't initialize ChannelConnection for " + player.getUsername());
            this.plugin.logger.error("Can't initialize ChannelConnection for {}", player.getUsername());
            return;
        }
        VelocityPlayer velocityPlayer = VelocityPlayer.wrap(player, connection);
        connection.bind(velocityPlayer);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        // 保留连接对象到 Channel 关闭, 这里只解除玩家引用
        ChannelConnection connection = this.connectionByPlayer(event.getPlayer());
        if (connection == null) {
            this.plugin.logger.warn("Failed to access Netty channel of player {}, player connections will not be tracked", event.getPlayer().getUsername());
            return;
        }
        connection.unbind(event.getPlayer().getUniqueId());
    }

    private void addConnection(ChannelConnection connection) {
        this.connectionsByChannel.put(connection.channel(), connection);
    }

    private void removeConnection(ChannelConnection connection) {
        this.connectionsByChannel.remove(connection.channel());
    }

    // 通过玩家底层的 MinecraftConnection 找到对应的 ChannelConnection
    @Nullable
    private ChannelConnection connectionByPlayer(Player player) {
        try {
            Object connection = GET_PLAYER_CONNECTION_METHOD.invoke(player);
            Channel channel = (Channel) GET_CONNECTION_CHANNEL_METHOD.invoke(connection);
            return this.connectionsByChannel.get(channel);
        } catch (Throwable e) {
            this.plugin.logger.warn("Failed to access Netty channel of player {}, player connections will not be tracked", player.getUsername(), e);
        }
        return null;
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
