package net.momirealms.craftengine.proxy.common.network.resourcepack;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * 保存从客户端 Resource Pack Status 数据包读取的版本化字段.
 */
public record ResourcePackStatus(@Nullable UUID uniqueId, ResourcePackResult result) {

    public ResourcePackStatus {
        Objects.requireNonNull(result, "result");
    }
}
