package net.momirealms.craftengine.proxy.common.network.listener;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.momirealms.craftengine.proxy.common.ProxyCraftEngine;
import net.momirealms.craftengine.proxy.common.network.ChannelConnection;
import net.momirealms.craftengine.proxy.common.network.listener.common.CustomPayloadListener;
import net.momirealms.craftengine.proxy.common.network.listener.common.ResourcePackRemoveListener;
import net.momirealms.craftengine.proxy.common.network.listener.common.ResourcePackSendListener;
import net.momirealms.craftengine.proxy.common.network.listener.common.ResourcePackStatusListener;
import net.momirealms.craftengine.proxy.common.network.listener.game.*;
import net.momirealms.craftengine.proxy.common.network.packet.*;
import net.momirealms.craftengine.proxy.common.network.protocol.ConnectionState;
import net.momirealms.craftengine.proxy.common.network.protocol.PacketSide;
import net.momirealms.craftengine.proxy.common.network.protocol.packettype.PacketType;
import net.momirealms.craftengine.proxy.common.network.protocol.packettype.PacketTypeCommon;
import net.momirealms.craftengine.proxy.common.network.protocol.player.ClientVersion;
import net.momirealms.craftengine.proxy.common.platform.ProxyPlayer;
import net.momirealms.craftengine.proxy.common.util.ProxyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class PacketListenerManager {
    protected final PacketHandlerRegistry packetRegistry;
    protected final List<PacketRegistration> internalRegistrations = new ArrayList<>(); // 内部协议状态监听

    public PacketListenerManager() {
        this.packetRegistry = PacketHandlerRegistry.create();
    }

    // 注册常规监听器
    protected void registerPacketListeners() {
        CustomPayloadListener.register(this.packetRegistry, this.plugin());
        ResourcePackSendListener.register(this.packetRegistry);
        ResourcePackStatusListener.register(this.packetRegistry);
        ResourcePackRemoveListener.register(this.packetRegistry);
        PlayerInfoUpdateListener.register(this.packetRegistry, this.plugin());
        SetTabListHeaderAndFooterListener.register(this.packetRegistry, this.plugin());
        SetPlayerTeamListener.register(this.packetRegistry, this.plugin());
        SetBossBarListener.register(this.packetRegistry, this.plugin());
        SetTitleTextListener.register(this.packetRegistry, this.plugin());
        SetSubTitleTextListener.register(this.packetRegistry, this.plugin());
        SetActionBarTextListener.register(this.packetRegistry, this.plugin());
        SystemChatListener.register(this.packetRegistry, this.plugin());
        SetScoreListener.register(this.packetRegistry, this.plugin());
        SetObjectiveListener.register(this.packetRegistry, this.plugin());
    }

    // 注册用于同步通用协议状态的内部数据包监听器
    protected void registerInternalRegistrations() {
        this.internalRegistrations.add(this.packetRegistry.register(
                PacketRoute.typed(ConnectionState.HANDSHAKING, PacketType.Handshaking.Client.HANDSHAKE),
                (connection, player, packet) -> {
                    ProxyByteBuf payload = packet.payload();
                    int protocolVersion = payload.readVarInt();
                    payload.readUtf(255);
                    payload.readUnsignedShort();
                    int nextState = payload.readVarInt();

                    connection.setProtocolVersion(protocolVersion);
                    if (nextState == 1) {
                        connection.setConnectionState(ConnectionState.STATUS);
                    } else if (nextState == 2 || nextState == 3) {
                        connection.setConnectionState(ConnectionState.LOGIN);
                    }
                }
        ));
        this.internalRegistrations.add(this.packetRegistry.register(
                PacketRoute.typed(ConnectionState.LOGIN, PacketType.Login.Server.LOGIN_SUCCESS),
                (connection, player, packet) -> {
                    if (connection.clientVersion().isNewerThanOrEquals(ClientVersion.V_1_20_2)) {
                        connection.setEncoderState(ConnectionState.CONFIGURATION);
                    } else {
                        connection.setConnectionState(ConnectionState.PLAY);
                    }
                }
        ));
        this.internalRegistrations.add(this.packetRegistry.register(
                PacketRoute.typed(ConnectionState.LOGIN, PacketType.Login.Client.LOGIN_SUCCESS_ACK),
                (connection, player, packet) -> connection.setDecoderState(ConnectionState.CONFIGURATION)
        ));
        this.internalRegistrations.add(this.packetRegistry.register(
                PacketRoute.typed(ConnectionState.PLAY, PacketType.Play.Server.CONFIGURATION_START),
                (connection, player, packet) -> {
                    // 1.20.2- 没有 UUID 多包模型, 切服进入新配置阶段时旧单包记录不再可信
                    if (connection.clientVersion() == ClientVersion.V_1_20_2 && player != null) {
                        player.resourcePackSession().clear();
                    }
                    connection.setEncoderState(ConnectionState.CONFIGURATION);
                }
        ));
        this.internalRegistrations.add(this.packetRegistry.register(
                PacketRoute.typed(ConnectionState.PLAY, PacketType.Play.Client.CONFIGURATION_ACK),
                (connection, player, packet) -> connection.setDecoderState(ConnectionState.CONFIGURATION)
        ));
        this.internalRegistrations.add(this.packetRegistry.register(
                PacketRoute.typed(ConnectionState.CONFIGURATION, PacketType.Configuration.Server.CONFIGURATION_END),
                (connection, player, packet) -> connection.setEncoderState(ConnectionState.PLAY)
        ));
        this.internalRegistrations.add(this.packetRegistry.register(
                PacketRoute.typed(ConnectionState.CONFIGURATION, PacketType.Configuration.Client.CONFIGURATION_END_ACK),
                (connection, player, packet) -> connection.setDecoderState(ConnectionState.PLAY)
        ));
    }

    // 处理原始数据包 buffer, 将数据包转换为 ProxyPacketContext 并返回最终应继续传递的 buffer
    public ByteBuf handle(ChannelConnection connection, @Nullable ProxyPlayer player, PacketSide side, ByteBuf buffer) {
        if (!buffer.isReadable()) {
            return buffer;
        }

        // 只在命中监听器时消费 packet id, 未命中或未修改时恢复 reader index
        ProxyByteBuf payload = new ProxyByteBuf(buffer);
        int preProcessIndex = payload.readerIndex();
        int preProcessWriterIndex = payload.writerIndex();
        int packetId = -1;
        PacketContext packet = null;
        try {
            packetId = payload.readVarInt();
            int payloadIndex = payload.readerIndex();
            ConnectionState state = connection.getConnectionState(side);
            ClientVersion clientVersion = connection.clientVersion(); // 只处理插件支持的客户端版本 (1.20-ClientVersion.latest())
            PacketHandler packetHandler = this.packetRegistry().getPacketHandler(side, state, clientVersion, packetId);
            if (packetHandler == null) {
                payload.readerIndex(preProcessIndex);
                return buffer;
            }

            PacketTypeCommon packetType = PacketType.getById(side, state, clientVersion, packetId);
            packet = new PacketContext(side, state, clientVersion, packetId, packetType, payload, payloadIndex);
            packetHandler.handle(connection, player, packet);
            if (packet.isCancelled()) {
                payload.clear();
                packet.releaseReplacementPayload();
                return Unpooled.EMPTY_BUFFER;
            }

            ByteBuf replacement = packet.replacementPayloadSource();
            if (replacement != null) {
                replacement.readerIndex(0);
                return replacement;
            }

            if (!packet.changed()) {
                payload.readerIndex(preProcessIndex);
            }
            return buffer;
        } catch (Throwable throwable) {
            if (packet != null) {
                packet.releaseReplacementPayload();
            }
            this.errorHandler().handle(packetId, side, throwable);
            payload.readerIndex(preProcessIndex);
            payload.writerIndex(preProcessWriterIndex);
            return buffer;
        }
    }

    public PacketHandlerRegistry packetRegistry() {
        return this.packetRegistry;
    }

    public static boolean isUnsupportedClientProtocolVersion(int protocolVersion) {
        return !ClientVersion.isRelease(protocolVersion);
    }

    public abstract ErrorHandler errorHandler();

    public abstract ProxyCraftEngine plugin();

    @FunctionalInterface
    public interface ErrorHandler {
        void handle(int packetId, PacketSide side, Throwable throwable);
    }
}
