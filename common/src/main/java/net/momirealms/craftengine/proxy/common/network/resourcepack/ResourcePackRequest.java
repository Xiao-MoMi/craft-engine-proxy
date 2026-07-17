package net.momirealms.craftengine.proxy.common.network.resourcepack;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// 保存 Resource Pack Send 数据包中的资源身份字段.
public record ResourcePackRequest(
        @Nullable UUID uniqueId,
        String url,
        String hash
) {
}
