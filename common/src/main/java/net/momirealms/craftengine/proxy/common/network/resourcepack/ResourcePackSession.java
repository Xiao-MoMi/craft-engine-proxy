package net.momirealms.craftengine.proxy.common.network.resourcepack;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ResourcePackSession {
    private final Map<String, ResourcePackRecord> recordsByHash = new HashMap<>(); // 现代协议按逻辑资源身份查重
    private final Map<UUID, ResourcePackRecord> recordsByAliasId = new HashMap<>(); // 按请求 UUID 定位别名
    private final Map<UUID, ResourcePackRecord> recordsByClientId = new HashMap<>(); // 按客户端实际 UUID 定位真实回执
    private @Nullable ResourcePackRecord legacyRecord; // 1.20-1.20.2 仅维护一个资源包槽位

    // 读取当前会话状态, 检查本次资源包发送请求是否可以直接使用已安装资源包.
    public ResourcePackDecision prepareRequest(ResourcePackRequest request) {
        String hash = request.hash();
        // 产生获取已有的 ResourcePackRecord 记录; 如果记录为空, hash不同, 最近一次真实客户端回执为空, 最近一次回执未下载成功, 均放行下载.
        ResourcePackRecord generation = request.uniqueId() == null ? this.legacyRecord : this.recordsByHash.get(hash);
        if (generation == null || !generation.hash.equals(hash) || generation.result == null || !generation.result.isInstalled()) {
            return new ResourcePackDecision(this, request, null);
        }

        // UUID 已指向另一代资源包时必须正常放行下载, 否则会跳过客户端本应执行的覆盖操作.
        if (request.uniqueId() != null) {
            UUID requestId = request.uniqueId();
            ResourcePackRecord aliasConflict = this.recordsByAliasId.get(requestId);
            ResourcePackRecord clientIdConflict = this.recordsByClientId.get(request.uniqueId());
            // 根据请求 UUID 获取已加载的资源包记录, 如果其中有和当前请求不一样的则直接放行下载覆盖.
            if (
                    (aliasConflict != null && aliasConflict != generation) || (clientIdConflict != null && clientIdConflict != generation)
            ) {
                return new ResourcePackDecision(this, request, null);
            }
        }

        // 拦截请求
        return new ResourcePackDecision(this, request, generation);
    }

    // 在原 Push 确定继续下发后, 将它登记为新的客户端资源包代次.
    public void commitForwarded(ResourcePackDecision decision) {
        decision.claim(this);

        ResourcePackRequest request = decision.request;
        UUID clientPackId = request.uniqueId();
        // For 1.20.2-
        if (clientPackId == null) {
            this.removeGeneration(this.legacyRecord);
            this.legacyRecord = new ResourcePackRecord(decision.request.hash(), null);
            return;
        }
        // For 1.20.3+
        this.detachAlias(clientPackId);
        this.removeGeneration(this.recordsByClientId.get(clientPackId));

        // 同一 hash 的未完成代次由本次真实下发替代, 旧客户端残留不再参与会话推断
        this.removeGeneration(this.recordsByHash.get(decision.request.hash()));

        ResourcePackRecord generation = new ResourcePackRecord(decision.request.hash(), clientPackId);
        this.recordsByHash.put(decision.request.hash(), generation);
        this.recordsByClientId.put(clientPackId, generation);
        this.addAlias(clientPackId, generation);
    }

    // 在合成回执成功进入平台代理链后, 才把本次请求登记为已安装资源包的别名.
    public void commitFiltered(ResourcePackDecision decision) {
        if (!decision.filtered()) {
            throw new IllegalArgumentException("resource pack decision cannot be committed as filtered");
        }
        decision.claim(this);

        ResourcePackRecord generation = decision.generation;
        ResourcePackRequest request = decision.request;
        if (generation == null || request.uniqueId() == null) {
            return;
        }
        if (this.recordsByHash.get(generation.hash) != generation || generation.result == null || !generation.result.isInstalled()) {
            throw new IllegalStateException("resource pack generation changed before filter commit");
        }

        UUID aliasId = request.uniqueId();
        ResourcePackRecord conflict = this.recordsByAliasId.get(aliasId);
        if (conflict != null && conflict != generation) {
            throw new IllegalStateException("resource pack alias changed before filter commit");
        }
        this.addAlias(aliasId, generation);
    }

    // 记录真实客户端状态, 不能证明资源包已安装的结束状态会使整代记录失效.
    public void handleStatus(@Nullable UUID uniqueId, ResourcePackResult result) {
        Objects.requireNonNull(result, "result");

        ResourcePackRecord generation = uniqueId == null ? this.legacyRecord : this.recordsByClientId.get(uniqueId);
        if (generation == null) {
            return;
        }
        if (result.invalidatesGeneration()) {
            this.removeGeneration(generation);
            return;
        }
        generation.result = result;
    }

    // 解析 Remove 的目标和改写 UUID.
    public ResourcePackRemoval prepareRemove(@Nullable UUID uniqueId) {
        if (uniqueId == null) {
            return new ResourcePackRemoval(this, null, null, null, true);
        }

        ResourcePackRecord generation = this.recordsByAliasId.get(uniqueId);
        if (generation == null) {
            generation = this.recordsByClientId.get(uniqueId);
        }

        UUID forwardedId = generation == null || generation.clientPackId == null ? uniqueId : generation.clientPackId;
        return new ResourcePackRemoval(this, uniqueId, forwardedId, generation, false);
    }

    // 在 Remove 数据包完成必要改写后, 删除它命中的整代资源包及全部别名.
    public void commitRemove(ResourcePackRemoval removal) {
        removal.claim(this);
        if (removal.clearAll) {
            this.clear();
            return;
        }
        this.removeGeneration(removal.generation);
    }

    // 清除当前玩家会话的全部资源包推断状态.
    public void clear() {
        this.legacyRecord = null;
        this.recordsByHash.clear();
        this.recordsByAliasId.clear();
        this.recordsByClientId.clear();
    }

    // 按对象身份删除一代资源包, 防止旧代清理误删同 hash 的新代索引.
    private void removeGeneration(@Nullable ResourcePackRecord generation) {
        if (generation != null) {
            if (this.legacyRecord == generation) {
                this.legacyRecord = null;
            }
            this.recordsByHash.remove(generation.hash, generation);
            if (generation.clientPackId != null) {
                this.recordsByClientId.remove(generation.clientPackId, generation);
            }
            for (UUID aliasId : generation.aliasIds) {
                this.recordsByAliasId.remove(aliasId, generation);
            }
            generation.aliasIds.clear();
        }
    }

    private void addAlias(UUID aliasId, ResourcePackRecord generation) {
        ResourcePackRecord previous = this.recordsByAliasId.put(aliasId, generation);
        if (previous != null && previous != generation) {
            previous.aliasIds.remove(aliasId);
        }
        generation.aliasIds.add(aliasId);
    }

    private void detachAlias(UUID aliasId) {
        ResourcePackRecord previous = this.recordsByAliasId.remove(aliasId);
        if (previous != null) {
            previous.aliasIds.remove(aliasId);
        }
    }
}
