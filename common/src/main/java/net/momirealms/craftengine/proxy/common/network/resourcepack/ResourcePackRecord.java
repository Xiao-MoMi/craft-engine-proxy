package net.momirealms.craftengine.proxy.common.network.resourcepack;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// 表示一次实际发往客户端的资源包生命周期.
final class ResourcePackRecord {
    final String hash; // 后端提供的逻辑资源身份
    final @Nullable UUID clientPackId; // 本代资源包实际发送给客户端的 UUID
    final Set<UUID> aliasIds = new HashSet<>(); // 指向本代资源包的请求 UUID
    @Nullable ResourcePackResult result; // 最近一次真实客户端回执

    ResourcePackRecord(String hash, @Nullable UUID clientPackId) {
        this.hash = hash;
        this.clientPackId = clientPackId;
    }
}
