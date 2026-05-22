package net.momirealms.craftengine.proxy.common.tag;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NetworkPendingTagDataRegistry {
    private final Map<String, NetworkPendingTagData> serverPendingData = new ConcurrentHashMap<>();

    @Nullable
    public NetworkPendingTagData get(String serverName) {
        return this.serverPendingData.get(serverName);
    }

    public void put(String serverName, NetworkPendingTagData netWorkTagData) {
        this.serverPendingData.put(serverName, netWorkTagData);
    }

    public void remove(String serverName) {
        this.serverPendingData.remove(serverName);
    }

    public void clear() {
        this.serverPendingData.clear();
    }
}
