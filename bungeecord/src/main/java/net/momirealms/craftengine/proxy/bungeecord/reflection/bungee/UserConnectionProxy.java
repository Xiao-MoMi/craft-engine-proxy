package net.momirealms.craftengine.proxy.bungeecord.reflection.bungee;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = "net.md_5.bungee.UserConnection")
public interface UserConnectionProxy {
    UserConnectionProxy INSTANCE = ASMProxyFactory.create(UserConnectionProxy.class);

    @MethodInvoker(name = "getCh")
    Object getCh(Object target);
}
