package net.momirealms.craftengine.proxy.bungeecord.reflection.bungee.netty;

import io.netty.channel.Channel;
import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = "net.md_5.bungee.netty.ChannelWrapper")
public interface ChannelWrapperProxy {
    ChannelWrapperProxy INSTANCE = ASMProxyFactory.create(ChannelWrapperProxy.class);

    @MethodInvoker(name = "getHandle")
    Channel getHandle(Object target);
}
