package net.momirealms.craftengine.proxy.common.network.listener.common;

import net.momirealms.craftengine.proxy.common.ProxyCraftEngine;
import net.momirealms.craftengine.proxy.common.network.ChannelConnection;
import net.momirealms.craftengine.proxy.common.network.packet.PacketContext;
import net.momirealms.craftengine.proxy.common.network.packet.PacketHandler;
import net.momirealms.craftengine.proxy.common.network.packet.PacketHandlerRegistry;
import net.momirealms.craftengine.proxy.common.network.packet.PacketRoute;
import net.momirealms.craftengine.proxy.common.network.protocol.ConnectionState;
import net.momirealms.craftengine.proxy.common.network.protocol.packettype.PacketType;
import net.momirealms.craftengine.proxy.common.platform.BackendServer;
import net.momirealms.craftengine.proxy.common.platform.ProxyPlayer;
import net.momirealms.craftengine.proxy.common.tag.NetworkTagDataSyncService;
import net.momirealms.craftengine.proxy.common.util.Key;
import net.momirealms.craftengine.proxy.common.util.ProxyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class CustomPayloadListener {
    private CustomPayloadListener() {}

    public static void register(PacketHandlerRegistry registry, ProxyCraftEngine plugin) {
        PacketHandler serverbound = new ChannelDispatcher(serverboundChannelHandlers(plugin));
        PacketHandler clientbound = new ChannelDispatcher(clientboundChannelHandlers(plugin));
        registry.register(PacketRoute.typed(ConnectionState.CONFIGURATION, PacketType.Configuration.Client.PLUGIN_MESSAGE), serverbound);
        registry.register(PacketRoute.typed(ConnectionState.CONFIGURATION, PacketType.Configuration.Server.PLUGIN_MESSAGE), clientbound);
        registry.register(PacketRoute.typed(ConnectionState.PLAY, PacketType.Play.Client.PLUGIN_MESSAGE), serverbound);
        registry.register(PacketRoute.typed(ConnectionState.PLAY, PacketType.Play.Server.PLUGIN_MESSAGE), clientbound);
    }

    // Serverbound (Client -> Proxy -> Backend), 在此登记需要处理的频道
    private static Map<Key, ChannelHandler> serverboundChannelHandlers(ProxyCraftEngine plugin) {
        return Map.of(
                // NetworkTagData -> 拦截客户端发送给 Proxy 的伪造包
                NetworkTagDataSyncService.TAG_DATA_CHANNEL_KEY, ( connection, player, payload, packet) -> {
                    packet.setCancelled(true);
                }
        );
    }

    // Clientbound (Backend -> Proxy -> Client), 在此登记需要处理的频道
    private static Map<Key, ChannelHandler> clientboundChannelHandlers(ProxyCraftEngine plugin) {
        return Map.of(
                // NetworkTagData -> 接收子服同步过来的 TagData
                NetworkTagDataSyncService.TAG_DATA_CHANNEL_KEY, (connection, player, payload, packet) -> {
                    if (player == null) return;
                    BackendServer backendServer = player.server();
                    if (backendServer == null) return;
                    plugin.networkTagDataSyncService().receiveTagData(backendServer.name(), payload);
                    packet.setCancelled(true);
                }
        );
    }

    // 单个 CustomPayload 频道的处理逻辑, payload 的 reader index 已定位到频道标识之后
    @FunctionalInterface
    private interface ChannelHandler {
        void handle(ChannelConnection connection, @Nullable ProxyPlayer player, ProxyByteBuf payload, PacketContext packet);
    }

    // 读取频道标识并分发给对应的 ChannelHandler, 未登记的频道直接放行
    private record ChannelDispatcher(Map<Key, ChannelHandler> channelHandlers) implements PacketHandler {
        @Override
        public void handle(ChannelConnection connection, @Nullable ProxyPlayer player, PacketContext packet) {
            ProxyByteBuf payload = packet.payload();
            ChannelHandler handler = this.channelHandlers.get(payload.readKey());
            if (handler != null) {
                handler.handle(connection, player, payload, packet);
            }
        }
    }
}
