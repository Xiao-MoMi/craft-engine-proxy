package net.momirealms.craftengine.proxy.bungeecord.network.inject;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import net.momirealms.craftengine.proxy.common.network.ChannelConnection;
import net.momirealms.craftengine.proxy.common.network.packet.PacketSink;
import net.momirealms.craftengine.proxy.common.network.protocol.PacketSide;

@ChannelHandler.Sharable
final class PacketEncoder extends ChannelOutboundHandlerAdapter {
    private final PacketSink packetSink;
    private final ChannelConnection connection;

    PacketEncoder(PacketSink packetSink, ChannelConnection connection) {
        this.packetSink = packetSink;
        this.connection = connection;
    }

    @Override
    public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
        if (!(message instanceof ByteBuf buffer)) {
            super.write(context, message, promise);
            return;
        }

        // packetSink 可能返回原始 buffer、替换 buffer 或空 buffer 来取消 packet
        ByteBuf result = this.packetSink.handle(this.connection, this.connection.player(), PacketSide.SERVER, buffer);
        if (!result.isReadable()) {
            if (result != buffer) {
                ReferenceCountUtil.release(result);
            }
            ReferenceCountUtil.release(buffer);
            return;
        }

        if (result != buffer) {
            ReferenceCountUtil.release(buffer);
        }
        context.write(result, promise);
    }
}
