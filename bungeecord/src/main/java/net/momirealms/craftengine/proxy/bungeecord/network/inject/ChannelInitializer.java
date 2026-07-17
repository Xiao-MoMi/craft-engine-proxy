package net.momirealms.craftengine.proxy.bungeecord.network.inject;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import net.momirealms.craftengine.proxy.bungeecord.BungeeCordCraftEngine;
import net.momirealms.craftengine.proxy.common.network.ChannelConnection;
import net.momirealms.craftengine.proxy.common.network.packet.PacketSink;
import net.momirealms.craftengine.proxy.common.util.ReflectionUtils;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.function.Consumer;

final class ChannelInitializer extends io.netty.channel.ChannelInitializer<Channel> {
    private static volatile Method initChannelMethod;

    private final PacketPipelineInjector injector;
    private final io.netty.channel.ChannelInitializer<Channel> wrappedInitializer;
    private final PacketSink packetSink;
    private final Consumer<ChannelConnection> connectionRegisterer;
    private final Consumer<ChannelConnection> connectionUnregister;

    ChannelInitializer(
            PacketPipelineInjector injector,
            io.netty.channel.ChannelInitializer<Channel> wrappedInitializer,
            PacketSink packetSink,
            Consumer<ChannelConnection> connectionRegisterer,
            Consumer<ChannelConnection> connectionUnregister
    ) {
        this.injector = injector;
        this.wrappedInitializer = wrappedInitializer;
        this.packetSink = packetSink;
        this.connectionRegisterer = connectionRegisterer;
        this.connectionUnregister = connectionUnregister;
    }

    @Override
    protected void initChannel(@NotNull Channel channel) throws Exception {
        // 先让 Bungee 完成自己的 pipeline 构建, 再添加自定义的 handler
        this.invokeWrappedInitializer(channel);
        if (!this.injector.injected()) {
            return;
        }
        // 连接状态从 channel 创建时开始记录, 后续再绑定到 ProxyPlayer
        ChannelConnection connection = new ChannelConnection(channel, BungeeCordCraftEngine.instance().packetListenerManager());
        this.connectionRegisterer.accept(connection);
        PacketPipelineInjector.addTo(channel, this.packetSink, connection);
        channel.closeFuture().addListener((ChannelFutureListener) future -> this.connectionUnregister.accept(connection));
    }

    boolean belongsTo(PacketPipelineInjector injector) {
        return this.injector == injector;
    }

    io.netty.channel.ChannelInitializer<Channel> wrappedInitializer() {
        return this.wrappedInitializer;
    }

    // 调用 Bungee 原始 protected initializer 上的 ChannelInitializer#initChannel
    private void invokeWrappedInitializer(Channel channel) throws Exception {
        Method method = ChannelInitializer.initChannelMethod;
        if (method == null) {
            method = ReflectionUtils.setAccessible(io.netty.channel.ChannelInitializer.class.getDeclaredMethod("initChannel", Channel.class));
            ChannelInitializer.initChannelMethod = method;
        }
        method.invoke(this.wrappedInitializer, channel);
    }
}
