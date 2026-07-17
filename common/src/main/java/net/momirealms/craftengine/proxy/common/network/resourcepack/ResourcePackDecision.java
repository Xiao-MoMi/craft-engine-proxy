package net.momirealms.craftengine.proxy.common.network.resourcepack;

import org.jetbrains.annotations.Nullable;

// 保存 Push 预检查结果, 供后续真实下发或过滤提交使用.
public final class ResourcePackDecision {
    final ResourcePackSession owner; // 创建该预检查结果的玩家会话
    final ResourcePackRequest request; // 本次待处理的原始请求
    final @Nullable ResourcePackRecord generation; // 可复用的已安装资源包代次
    private boolean committed; // 防止同一预检查结果被重复提交

    ResourcePackDecision(
            ResourcePackSession owner,
            ResourcePackRequest request,
            @Nullable ResourcePackRecord generation
    ) {
        this.owner = owner;
        this.request = request;
        this.generation = generation;
    }

    public boolean filtered() {
        return this.generation != null;
    }

    @Nullable
    public ResourcePackResult result() {
        return this.filtered() ? ResourcePackResult.SUCCESS_DOWNLOAD : null;
    }

    void claim(ResourcePackSession owner) {
        if (this.owner != owner) {
            throw new IllegalArgumentException("decision belongs to another resource pack session");
        }
        if (this.committed) {
            throw new IllegalStateException("resource pack decision is already committed");
        }
        this.committed = true;
    }
}
