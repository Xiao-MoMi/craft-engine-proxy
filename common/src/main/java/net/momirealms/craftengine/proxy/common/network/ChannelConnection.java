package net.momirealms.craftengine.proxy.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import net.momirealms.craftengine.proxy.common.network.listener.PacketListenerManager;
import net.momirealms.craftengine.proxy.common.network.protocol.ConnectionState;
import net.momirealms.craftengine.proxy.common.network.protocol.PacketSide;
import net.momirealms.craftengine.proxy.common.network.protocol.packettype.PacketTypeCommon;
import net.momirealms.craftengine.proxy.common.network.protocol.player.ClientVersion;
import net.momirealms.craftengine.proxy.common.platform.ProxyPlayer;
import net.momirealms.craftengine.proxy.common.util.ProxyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public class ChannelConnection implements ProtocolStateHolder {
    private static final String PACKET_DECODER_NAME = "craftengine_proxy_packet_decoder"; // 合成入站包从此 handler 后进入平台代理解码链
    private static final String PACKET_ENCODER_NAME = "craftengine_proxy_packet_encoder"; // 自定义出站包从此 handler 向客户端写出
    private final Channel channel;
    private final PacketListenerManager packetListenerManager;
    private volatile @Nullable ProxyPlayer player; // 登录完成后绑定的玩家
    private volatile int protocolVersion = -1; // handshake 读取到的原始协议号
    private volatile ClientVersion clientVersion = ClientVersion.UNKNOWN; // 由协议号映射出的客户端版本
    private volatile ConnectionState decoderState = ConnectionState.HANDSHAKING; // 客户端到服务端方向状态
    private volatile ConnectionState encoderState = ConnectionState.HANDSHAKING; // 服务端到客户端方向状态

    public ChannelConnection(Channel channel, PacketListenerManager packetListenerManager) {
        this.channel = channel;
        this.packetListenerManager = packetListenerManager;
    }

    public Channel channel() {
        return this.channel;
    }

    // 返回已绑定的玩家, BungeeCord 登录流程未完成时为 null
    @Nullable
    public ProxyPlayer player() {
        return this.player;
    }

    // 绑定平台玩家身份, 协议状态仍由 ChannelConnection 持有.
    public void bind(ProxyPlayer player) {
        this.player = player;
    }

    // 当 BungeeCord 上报匹配的断开连接事件时解除玩家绑定
    public void unbind(UUID uuid) {
        ProxyPlayer current = this.player;
        if (current != null && Objects.equals(current.uuid(), uuid)) {
            this.player = null;
        }
    }

    @Override
    public ClientVersion clientVersion() {
        return this.clientVersion;
    }

    @Override
    public int protocolVersion() {
        return this.protocolVersion;
    }

    @Override
    public void setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
        this.clientVersion = protocolVersion < 0 ? ClientVersion.UNKNOWN : ClientVersion.getById(protocolVersion);
    }

    @Override
    public void setConnectionState(ConnectionState connectionState) {
        ConnectionState state = Objects.requireNonNull(connectionState, "connectionState");
        this.decoderState = state;
        this.encoderState = state;
    }

    @Override
    public ConnectionState decoderState() {
        return this.decoderState;
    }

    @Override
    public ConnectionState encoderState() {
        return this.encoderState;
    }

    @Override
    public void setDecoderState(ConnectionState decoderState) {
        this.decoderState = Objects.requireNonNull(decoderState, "decoderState");
    }

    @Override
    public void setEncoderState(ConnectionState encoderState) {
        this.encoderState = Objects.requireNonNull(encoderState, "encoderState");
    }

    /**
     * 构造一个客户端上行数据包, 交由平台代理原生解码和后端路由流程处理.
     * 当 {@code bypassCraftEngine} 为 false 时, 数据包会先经过本插件的包处理器.
     */
    @Override
    public boolean sendServerbound(ConnectionState expectedState, PacketTypeCommon packetType, boolean bypassCraftEngine, Consumer<ProxyByteBuf> payloadWriter) {
        if (packetType.getSide() != PacketSide.CLIENT || this.decoderState != expectedState || !this.channel.isActive()) {
            return false;
        }

        int packetId = packetType.getId(this.clientVersion);
        ChannelHandlerContext injectionPoint = this.channel.pipeline().context(PACKET_DECODER_NAME); // TODO 优化?
        if (packetId < 0 || injectionPoint == null || !injectionPoint.executor().inEventLoop()) {
            return false;
        }

        ByteBuf packet = this.createPacket(packetId, payloadWriter, PacketSide.CLIENT, bypassCraftEngine);
        if (packet == null) {
            return false;
        }

        injectionPoint.fireChannelRead(packet);
        return true;
    }

    /**
     * 构造一个服务端下行数据包, 从当前玩家连接写入客户端.
     * 当 {@code bypassCraftEngine} 为 false 时, 数据包会先经过本插件的包处理器.
     */
    @Override
    public boolean sendClientbound(ConnectionState expectedState, PacketTypeCommon packetType, boolean bypassCraftEngine, Consumer<ProxyByteBuf> payloadWriter) {
        if (packetType.getSide() != PacketSide.SERVER || this.encoderState != expectedState || !this.channel.isActive()) {
            return false;
        }

        int packetId = packetType.getId(this.clientVersion);
        ChannelHandlerContext injectionPoint = this.channel.pipeline().context(PACKET_ENCODER_NAME); // TODO 优化?
        if (packetId < 0 || injectionPoint == null || !injectionPoint.executor().inEventLoop()) {
            return false;
        }

        ByteBuf packet = this.createPacket(packetId, payloadWriter, PacketSide.SERVER, bypassCraftEngine);
        if (packet == null) {
            return false;
        }

        injectionPoint.writeAndFlush(packet);
        return true;
    }

    // 编码完整数据包, 并按需交给包处理器执行取消或改写逻辑.
    @Nullable
    private ByteBuf createPacket(int packetId, Consumer<ProxyByteBuf> payloadWriter, PacketSide side, boolean bypassCraftEngine) {
        ProxyByteBuf packet = new ProxyByteBuf(this.channel.alloc().buffer());
        try {
            packet.writeVarInt(packetId);
            payloadWriter.accept(packet);
        } catch (Throwable throwable) {
            packet.release();
            throw throwable;
        }

        ByteBuf source = packet.source();
        if (bypassCraftEngine) {
            return source;
        }

        // 如果不跳过自身的包处理器, 那么就手动调用 handle 方法去处理, 然后拿到处理后的Buf.
        // 如果处理完后Buf被替换了, 那么记得要释放旧的Buf.
        ByteBuf processed = this.packetListenerManager.handle(this, this.player, side, source);
        if (!processed.isReadable()) {
            if (processed != source) {
                ReferenceCountUtil.release(processed);
            }
            ReferenceCountUtil.release(source);
            return null;
        }
        if (processed != source) {
            ReferenceCountUtil.release(source);
        }
        return processed;
    }
}
