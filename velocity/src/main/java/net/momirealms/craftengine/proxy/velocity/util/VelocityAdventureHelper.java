package net.momirealms.craftengine.proxy.velocity.util;

import com.velocitypowered.api.proxy.Player;
import net.momirealms.sparrow.reflection.clazz.SparrowClass;
import net.momirealms.sparrow.reflection.method.matcher.MethodMatcher;

import java.lang.invoke.MethodHandle;

/**
 * 桥接 Velocity 原生 Adventure API 的反射工具.
 * <p>
 * 插件打包的 Adventure 在构建期被 relocate 到 {@code net.momirealms.craftengine.libraries} 下,
 * 而 Velocity API 方法签名使用的是未 relocate 的 {@code net.kyori.adventure} 类型.
 * 直接把插件内的 Component 传给 Velocity API 会在运行时抛出 NoSuchMethodError,
 * 因此这里通过反射获取 Velocity 加载的原生 Component 类型来完成调用.
 */
public final class VelocityAdventureHelper {
    public static final MethodHandle COMPONENT_TEXT_METHOD; // 原生 Component#text(String)
    public static final MethodHandle PLAYER_DISCONNECT_METHOD; // Player#disconnect(Component)

    static {
        Class<?> componentClass = SparrowClass.findNoRemap("net{}kyori{}adventure{}text{}Component".replace("{}", "."));
        COMPONENT_TEXT_METHOD = SparrowClass.of(componentClass).getDeclaredSparrowMethod(
                        MethodMatcher.named("text")
                                .and(MethodMatcher.staticMethod())
                                .and(MethodMatcher.takeArgument(0, String.class)))
                .unreflect();
        PLAYER_DISCONNECT_METHOD = SparrowClass.of(Player.class).getDeclaredSparrowMethod(
                        MethodMatcher.named("disconnect")
                                .and(MethodMatcher.takeArgument(0, componentClass)))
                .unreflect();
    }

    private VelocityAdventureHelper() {
    }

    /**
     * 以 Velocity 原生 Adventure 组件断开玩家连接.
     */
    public static void disconnect(Player player, String reason) {
        try {
            Object component = VelocityAdventureHelper.COMPONENT_TEXT_METHOD.invoke(reason);
            VelocityAdventureHelper.PLAYER_DISCONNECT_METHOD.invoke(player, component);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
