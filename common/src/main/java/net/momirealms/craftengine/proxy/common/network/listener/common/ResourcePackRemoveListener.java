package net.momirealms.craftengine.proxy.common.network.listener.common;

import net.momirealms.craftengine.proxy.common.network.ChannelConnection;
import net.momirealms.craftengine.proxy.common.network.packet.PacketContext;
import net.momirealms.craftengine.proxy.common.network.packet.PacketHandler;
import net.momirealms.craftengine.proxy.common.network.packet.PacketHandlerRegistry;
import net.momirealms.craftengine.proxy.common.network.packet.PacketRoute;
import net.momirealms.craftengine.proxy.common.network.protocol.ConnectionState;
import net.momirealms.craftengine.proxy.common.network.protocol.packettype.PacketType;
import net.momirealms.craftengine.proxy.common.network.protocol.player.ClientVersion;
import net.momirealms.craftengine.proxy.common.network.resourcepack.ResourcePackRemoval;
import net.momirealms.craftengine.proxy.common.network.resourcepack.ResourcePackSession;
import net.momirealms.craftengine.proxy.common.platform.ProxyPlayer;
import net.momirealms.craftengine.proxy.common.util.ProxyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class ResourcePackRemoveListener implements PacketHandler {
    private static final ResourcePackRemoveListener INSTANCE = new ResourcePackRemoveListener();

    private ResourcePackRemoveListener() {
    }

    public static void register(PacketHandlerRegistry registry) {
        registry.registerSince(
                PacketRoute.typed(ConnectionState.PLAY, PacketType.Play.Server.RESOURCE_PACK_REMOVE),
                ClientVersion.V_1_20_3,
                INSTANCE
        );
        registry.registerSince(
                PacketRoute.typed(ConnectionState.CONFIGURATION, PacketType.Configuration.Server.RESOURCE_PACK_REMOVE),
                ClientVersion.V_1_20_3,
                INSTANCE
        );
    }

    /**
     * 先确定 Remove 的真实目标, 完成包改写后再删除对应的整代会话记录.
     */
    @Override
    public void handle(ChannelConnection connection, @Nullable ProxyPlayer player, PacketContext packet) {
        if (player == null) {
            return;
        }
        ResourcePackSession session = player.resourcePackSession();

        // 读取 Remove 的可选 UUID, null 表示 clear-all
        ProxyByteBuf payload = packet.payload();
        UUID removeUniqueId = payload.readBoolean() ? payload.readUUID() : null;
        ResourcePackRemoval removal = session.prepareRemove(removeUniqueId);

        // alias Remove 需要指向客户端真正持有的 UUID, 改写失败时不提前破坏会话索引
        if (removal.rewritten()) {
            packet.rewritePayload(replacement -> {
                replacement.writeVarInt(packet.packetID());
                UUID uniqueId = removal.forwardedId();
                replacement.writeBoolean(uniqueId != null);
                if (uniqueId != null) {
                    replacement.writeUUID(uniqueId);
                }
            });
        }
        session.commitRemove(removal);
    }
}
