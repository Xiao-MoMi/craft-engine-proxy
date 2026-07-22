package net.momirealms.craftengine.proxy.velocity.reflection.velocitypowered.proxy.connection.client;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = "com.velocitypowered.proxy.connection.client.ConnectedPlayer")
public interface ConnectedPlayerProxy {
    ConnectedPlayerProxy INSTANCE = ASMProxyFactory.create(ConnectedPlayerProxy.class);

    @MethodInvoker(name = "getConnection")
    Object getConnection(Object target);
}
