package net.momirealms.craftengine.proxy.common.tag;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

public final class NetworkPendingTagDataRegistry {
    private final Cache<@NotNull String, NetworkPendingTagData> serverPendingData = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    @Nullable
    public NetworkPendingTagData get(String serverName) {
        return this.serverPendingData.getIfPresent(serverName);
    }

    public void put(String serverName, NetworkPendingTagData netWorkTagData) {
        this.serverPendingData.put(serverName, netWorkTagData);
    }

    public void remove(String serverName) {
        this.serverPendingData.invalidate(serverName);
    }

    public void clear() {
        this.serverPendingData.invalidateAll();
    }
}
