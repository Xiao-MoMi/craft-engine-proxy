package net.momirealms.craftengine.proxy.bungeecord.network;

import io.netty.channel.Channel;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
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
import net.momirealms.sparrow.reflection.clazz.SparrowClass;
import net.momirealms.sparrow.reflection.method.matcher.MethodMatcher;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

public class BungeePacketListenerManager extends PacketListenerManager implements Listener {
    private static final String USER_CONNECTION_CLASS_NAME = "net.md_5.bungee.UserConnection";
    private static final String CHANNEL_WRAPPER_CLASS_NAME = "net.md_5.bungee.netty.ChannelWrapper";
    private static final MethodHandle GET_PLAYER_CHANNEL_WRAPPER_METHOD; // UserConnection#getCh()
    private static final MethodHandle GET_WRAPPER_CHANNEL_METHOD; // ChannelWrapper#getHandle()

    private final BungeeCordCraftEngine plugin;
    private final PacketPipelineInjector pipelineInjector; // 负责 Bungee Netty pipeline 注入
    private final PacketListenerManager.ErrorHandler errorHandler;
    private final ConcurrentMap<Channel, ChannelConnection> connectionsByChannel = new ConcurrentHashMap<>(); // Channel 生命周期索引
    private volatile boolean loaded;

    static {
        Class<?> userConnectionClass = SparrowClass.findNoRemap(USER_CONNECTION_CLASS_NAME);
        GET_PLAYER_CHANNEL_WRAPPER_METHOD = SparrowClass.of(userConnectionClass).getDeclaredSparrowMethod(MethodMatcher.named("getCh")).unreflect();
        Class<?> channelWrapperClass = SparrowClass.findNoRemap(CHANNEL_WRAPPER_CLASS_NAME);
        GET_WRAPPER_CHANNEL_METHOD = SparrowClass.of(channelWrapperClass).getDeclaredSparrowMethod(MethodMatcher.named("getHandle")).unreflect();
    }

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
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        // Netty channel 早于 Bungee player 创建, 登录后再绑定玩家对象.
        ProxiedPlayer player = event.getPlayer();
        ChannelConnection connection = this.connectionByPlayer(player);
        if (connection == null) {
            player.disconnect(new TextComponent("[CraftEngine] Can't initialize ChannelConnection for " + player.getDisplayName()));
            this.plugin.getLogger().severe("Can't initialize ChannelConnection for " + player.getDisplayName());
            return;
        }
        BungeePlayer bungeePlayer = BungeePlayer.wrap(player, connection);
        connection.bind(bungeePlayer);
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        // 保留连接对象到 Channel 关闭, 这里只解除玩家引用并删除玩家包装缓存
        ChannelConnection connection = this.connectionByPlayer(event.getPlayer());
        if (connection == null) {
            this.plugin.getLogger().warning("Failed to access Netty channel of player " + event.getPlayer().getDisplayName() + ", player connections will not be tracked");
        } else {
            connection.unbind(event.getPlayer().getUniqueId());
        }
        this.plugin.removePlayer(event.getPlayer());
    }

    private void addConnection(ChannelConnection connection) {
        this.connectionsByChannel.put(connection.channel(), connection);
    }

    private void removeConnection(ChannelConnection connection) {
        this.connectionsByChannel.remove(connection.channel());
    }

    // 通过玩家底层的 ChannelWrapper 找到对应的 ChannelConnection
    @Nullable
    private ChannelConnection connectionByPlayer(ProxiedPlayer player) {
        try {
            Object channelWrapper = GET_PLAYER_CHANNEL_WRAPPER_METHOD.invoke(player);
            Channel channel = (Channel) GET_WRAPPER_CHANNEL_METHOD.invoke(channelWrapper);
            return this.connectionsByChannel.get(channel);
        } catch (Throwable e) {
            this.plugin.getLogger().log(Level.WARNING, "Failed to access Netty channel of player " + player.getDisplayName() + ", player connections will not be tracked", e);
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
        this.plugin.getLogger().log(Level.WARNING, "An error occurred when handling packet " + packetId + " (" + side + ")", throwable);
    }
}
