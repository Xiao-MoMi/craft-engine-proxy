package net.momirealms.craftengine.proxy.common.network.resourcepack;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

// 保存 Remove 预检查结果, 包改写完成后再据此删除对应资源包代次.
public final class ResourcePackRemoval {
    final ResourcePackSession owner; // 创建该预检查结果的玩家会话
    final @Nullable ResourcePackRecord generation; // Remove 实际命中的资源包代次
    final boolean clearAll; // 无 UUID 的 Remove 表示清空全部资源包
    private final @Nullable UUID requestedId;
    private final @Nullable UUID forwardedId;
    private boolean committed; // 防止同一 Remove 被重复提交

    ResourcePackRemoval(
            ResourcePackSession owner,
            @Nullable UUID requestedId,
            @Nullable UUID forwardedId,
            @Nullable ResourcePackRecord generation,
            boolean clearAll
    ) {
        this.owner = owner;
        this.requestedId = requestedId;
        this.forwardedId = forwardedId;
        this.generation = generation;
        this.clearAll = clearAll;
    }

    @Nullable
    public UUID requestedId() {
        return this.requestedId;
    }

    @Nullable
    public UUID forwardedId() {
        return this.forwardedId;
    }

    public boolean rewritten() {
        return !Objects.equals(this.requestedId, this.forwardedId);
    }

    void claim(ResourcePackSession owner) {
        if (this.owner != owner) {
            throw new IllegalArgumentException("removal belongs to another resource pack session");
        }
        if (this.committed) {
            throw new IllegalStateException("resource pack removal is already committed");
        }
        this.committed = true;
    }
}
