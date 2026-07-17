package net.momirealms.craftengine.proxy.common.network.listener.common;

import net.momirealms.craftengine.proxy.common.network.ChannelConnection;
import net.momirealms.craftengine.proxy.common.network.packet.PacketContext;
import net.momirealms.craftengine.proxy.common.network.packet.PacketHandler;
import net.momirealms.craftengine.proxy.common.network.packet.PacketHandlerRegistry;
import net.momirealms.craftengine.proxy.common.network.packet.PacketRoute;
import net.momirealms.craftengine.proxy.common.network.protocol.ConnectionState;
import net.momirealms.craftengine.proxy.common.network.protocol.packettype.PacketType;
import net.momirealms.craftengine.proxy.common.network.protocol.packettype.PacketTypeCommon;
import net.momirealms.craftengine.proxy.common.network.protocol.player.ClientVersion;
import net.momirealms.craftengine.proxy.common.network.resourcepack.ResourcePackDecision;
import net.momirealms.craftengine.proxy.common.network.resourcepack.ResourcePackRequest;
import net.momirealms.craftengine.proxy.common.network.resourcepack.ResourcePackResult;
import net.momirealms.craftengine.proxy.common.network.resourcepack.ResourcePackSession;
import net.momirealms.craftengine.proxy.common.platform.ProxyPlayer;
import net.momirealms.craftengine.proxy.common.util.ProxyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class ResourcePackSendListener implements PacketHandler {
    private static final ResourcePackSendListener INSTANCE = new ResourcePackSendListener();

    private ResourcePackSendListener() {
    }

    public static void register(PacketHandlerRegistry registry) {
        registry.register(
                PacketRoute.typed(ConnectionState.PLAY, PacketType.Play.Server.RESOURCE_PACK_SEND),
                INSTANCE
        );
        registry.registerSince(
                PacketRoute.typed(ConnectionState.CONFIGURATION, PacketType.Configuration.Server.RESOURCE_PACK_SEND),
                ClientVersion.V_1_20_2,
                INSTANCE
        );
    }

    // 预检查 Send, 并在真实下发或过滤结果确定后提交对应会话状态.
    @Override
    public void handle(ChannelConnection connection, @Nullable ProxyPlayer player, PacketContext packet) {
        // ProxyPlayer 和 ChannelConnection 尚未绑定时保持原请求正常下发
        if (player == null) {
            return;
        }
        ResourcePackSession session = player.resourcePackSession();

        // 解码逻辑资源身份, prepare 阶段不会提前添加 alias
        ResourcePackRequest request = readRequest(packet.payload(), packet.clientVersion());
        ResourcePackDecision decision = session.prepareRequest(request);
        if (!decision.filtered() || decision.result() == null) {
            session.commitForwarded(decision);
            return;
        }

        // 返回的状态包必须使用本次请求所属的协议阶段和 UUID
        PacketTypeCommon statusPacketType = packet.state() == ConnectionState.CONFIGURATION
                ? PacketType.Configuration.Client.RESOURCE_PACK_STATUS
                : PacketType.Play.Client.RESOURCE_PACK_STATUS;

        // 只有回执确实进入平台解码链后, 才提交 alias 并取消原 Push
        boolean success = connection.sendServerbound(packet.state(), statusPacketType, true,
                payload -> writeStatus(payload, packet.clientVersion(), request.uniqueId(), decision.result())
        );
        if (success) {
            session.commitFiltered(decision);
            packet.setCancelled(true);
        } else {
            session.commitForwarded(decision);
        }
    }

    // 读取 Send 数据包前缀, 其余 forced 和 prompt 字段由平台代理继续处理.
    private static final int MAX_URL_LENGTH = 32767;
    private static final int MAX_HASH_LENGTH = 40;

    private static ResourcePackRequest readRequest(ProxyByteBuf payload, ClientVersion version) {
        UUID uniqueId = version.isNewerThanOrEquals(ClientVersion.V_1_20_3) ? payload.readUUID() : null;
        String url = payload.readUtf(MAX_URL_LENGTH);
        String hash = payload.readUtf(MAX_HASH_LENGTH);
        return new ResourcePackRequest(uniqueId, url, hash);
    }

    // 写入可交由平台代理原生 decoder 解析的状态 payload
    public static void writeStatus(
            ProxyByteBuf payload,
            ClientVersion version,
            @Nullable UUID uniqueId,
            ResourcePackResult result
    ) {
        if (version.isNewerThanOrEquals(ClientVersion.V_1_20_3)) {
            if (uniqueId == null) {
                throw new IllegalArgumentException("uniqueId is required since Minecraft 1.20.3");
            }
            payload.writeUUID(uniqueId);
        }
        if (result == ResourcePackResult.UNKNOWN) {
            throw new IllegalArgumentException("UNKNOWN resource pack result cannot be encoded");
        }
        payload.writeVarInt(result.ordinal());
    }
}
