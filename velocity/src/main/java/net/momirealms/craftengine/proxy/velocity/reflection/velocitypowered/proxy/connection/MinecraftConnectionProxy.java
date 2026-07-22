package net.momirealms.craftengine.proxy.velocity.reflection.velocitypowered.proxy.connection;

import io.netty.channel.Channel;
import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = "com.velocitypowered.proxy.connection.MinecraftConnection")
public interface MinecraftConnectionProxy {
    MinecraftConnectionProxy INSTANCE = ASMProxyFactory.create(MinecraftConnectionProxy.class);

    @MethodInvoker(name = "getChannel")
    Channel getChannel(Object target);
}
