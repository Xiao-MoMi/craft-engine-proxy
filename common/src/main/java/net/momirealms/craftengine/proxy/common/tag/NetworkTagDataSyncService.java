package net.momirealms.craftengine.proxy.common.tag;

import io.netty.buffer.Unpooled;
import net.momirealms.craftengine.proxy.common.ProxyCraftEngine;
import net.momirealms.craftengine.proxy.common.platform.ProxyPlayer;
import net.momirealms.craftengine.proxy.common.util.Key;
import net.momirealms.craftengine.proxy.common.util.ProxyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class NetworkTagDataSyncService {
    public static final String TAG_DATA_CHANNEL = "craftengine:tag_data";
    public static final Key TAG_DATA_CHANNEL_KEY = Key.ce("tag_data");
    public static final UUID PROXY_UUID = UUID.randomUUID();
    private final ProxyCraftEngine plugin;
    private final NetworkTagDataRegistry registry;
    private final NetworkPendingTagDataRegistry pendingDataRegistry;

    public NetworkTagDataSyncService(ProxyCraftEngine plugin) {
        this.plugin = plugin;
        this.registry = new NetworkTagDataRegistry();
        this.pendingDataRegistry = new NetworkPendingTagDataRegistry();
        this.plugin.registerChannel(TAG_DATA_CHANNEL); // 必须注册, 否则会在 CustomPayloadListener 处理之前就被 Proxy 拦截.
    }

    public NetworkTagDataRegistry registry() {
        return this.registry;
    }

    @Nullable
    public NetworkTagData getTagData(ProxyPlayer player) {
        return this.registry.get(player);
    }

    @Nullable
    public NetworkTagData getTagData(String serverName) {
        return this.registry.get(serverName);
    }

    public byte[] buildTagDataBytes(@Nullable NetworkTagData networkTagData) {
        long version = networkTagData != null ? networkTagData.version() : -1L;
        ProxyByteBuf buf = new ProxyByteBuf(Unpooled.buffer());
        buf.writeLong(version);
        buf.writeUUID(PROXY_UUID);
        return buf.array();
    }

    public synchronized void receiveTagData(String serverName, ProxyByteBuf in) {
        long version = in.readLong();
        int total = in.readInt();
        int index = in.readInt();
        if (index < 0 || index >= total) return;

        NetworkPendingTagData pendingTagData = this.pendingDataRegistry.get(serverName);
        if (pendingTagData != null && version < pendingTagData.version()) return;
        if (pendingTagData == null || version > pendingTagData.version()) {
            pendingTagData = new NetworkPendingTagData(new byte[total][], version);
            this.pendingDataRegistry.put(serverName,  pendingTagData);
        }

        byte[][] pendingData = pendingTagData.data();
        byte[] data = in.readFixedBytes(in.readableBytes());
        pendingData[index] = data;

        for (byte[] pendingDatum : pendingData) {
            if (pendingDatum == null) return;
        }

        int length = 0;
        for (byte[] pendingDatum : pendingData) {
            length += pendingDatum.length;
        }
        int current = 0;
        byte[] finalData = new byte[length];
        for (byte[] pendingDatum : pendingData) {
            System.arraycopy(pendingDatum, 0, finalData, current, pendingDatum.length);
            current += pendingDatum.length;
        }

        this.pendingDataRegistry.remove(serverName);
        this.registry.put(serverName, NetworkTagDataDeserializer.read(
                version, new ProxyByteBuf(Unpooled.wrappedBuffer(finalData)), this.registry, serverName
        ));
    }

    public void clear() {
        this.pendingDataRegistry.clear();
        this.registry.clear();
    }
}
