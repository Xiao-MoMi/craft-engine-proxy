package net.momirealms.craftengine.proxy.common.network;

import net.momirealms.craftengine.proxy.common.network.protocol.ConnectionState;
import net.momirealms.craftengine.proxy.common.network.protocol.PacketSide;
import net.momirealms.craftengine.proxy.common.network.protocol.packettype.PacketType;
import net.momirealms.craftengine.proxy.common.network.protocol.packettype.PacketTypeCommon;
import net.momirealms.craftengine.proxy.common.network.protocol.player.ClientVersion;
import net.momirealms.craftengine.proxy.common.util.ProxyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

public interface ProtocolStateHolder {

    ClientVersion clientVersion();

    int protocolVersion();

    void setProtocolVersion(int protocolVersion);

    default ConnectionState getConnectionState(PacketSide side) {
        Objects.requireNonNull(side, "side");
        return side == PacketSide.CLIENT ? this.decoderState() : this.encoderState();
    }

    @Nullable
    default PacketTypeCommon packetType(PacketSide side, int packetId) {
        return PacketType.getById(side, this.getConnectionState(side), this.clientVersion(), packetId);
    }

    void setConnectionState(ConnectionState connectionState);

    ConnectionState decoderState();

    ConnectionState encoderState();

    void setDecoderState(ConnectionState decoderState);

    void setEncoderState(ConnectionState encoderState);

    boolean sendServerbound(ConnectionState expectedState, PacketTypeCommon packetType, boolean bypassCraftEngine, Consumer<ProxyByteBuf> payloadWriter);

    boolean sendClientbound(ConnectionState expectedState, PacketTypeCommon packetType, boolean bypassCraftEngine, Consumer<ProxyByteBuf> payloadWriter);
}