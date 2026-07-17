package net.momirealms.craftengine.proxy.common.network.listener.common;

import net.momirealms.craftengine.proxy.common.network.ChannelConnection;
import net.momirealms.craftengine.proxy.common.network.packet.PacketContext;
import net.momirealms.craftengine.proxy.common.network.packet.PacketHandler;
import net.momirealms.craftengine.proxy.common.network.packet.PacketHandlerRegistry;
import net.momirealms.craftengine.proxy.common.network.packet.PacketRoute;
import net.momirealms.craftengine.proxy.common.network.protocol.ConnectionState;
import net.momirealms.craftengine.proxy.common.network.protocol.packettype.PacketType;
import net.momirealms.craftengine.proxy.common.network.protocol.player.ClientVersion;
import net.momirealms.craftengine.proxy.common.network.resourcepack.ResourcePackResult;
import net.momirealms.craftengine.proxy.common.network.resourcepack.ResourcePackSession;
import net.momirealms.craftengine.proxy.common.network.resourcepack.ResourcePackStatus;
import net.momirealms.craftengine.proxy.common.platform.ProxyPlayer;
import net.momirealms.craftengine.proxy.common.util.ProxyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class ResourcePackStatusListener implements PacketHandler {
    private static final ResourcePackStatusListener INSTANCE = new ResourcePackStatusListener();

    private ResourcePackStatusListener() {
    }

    public static void register(PacketHandlerRegistry registry) {
        registry.register(
                PacketRoute.typed(ConnectionState.PLAY, PacketType.Play.Client.RESOURCE_PACK_STATUS),
                INSTANCE
        );
        registry.registerSince(
                PacketRoute.typed(ConnectionState.CONFIGURATION, PacketType.Configuration.Client.RESOURCE_PACK_STATUS),
                ClientVersion.V_1_20_2,
                INSTANCE
        );
    }

    /**
     * 记录真实客户端状态, 数据包本身始终继续交由平台代理转发.
     */
    @Override
    public void handle(ChannelConnection connection, @Nullable ProxyPlayer player, PacketContext packet) {
        if (player == null) {
            return;
        }
        ResourcePackSession session = player.resourcePackSession();
        ProxyByteBuf payload = packet.payload();
        UUID uniqueId = packet.clientVersion().isNewerThanOrEquals(ClientVersion.V_1_20_3) ? payload.readUUID() : null;
        ResourcePackStatus status = new ResourcePackStatus(uniqueId, ResourcePackResult.fromOrdinal(payload.readVarInt()));
        session.handleStatus(status.uniqueId(), status.result());
    }
}
