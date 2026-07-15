package net.momirealms.craftengine.proxy.common.network.listener.game;

import net.kyori.adventure.text.Component;
import net.momirealms.craftengine.proxy.common.ProxyCraftEngine;
import net.momirealms.craftengine.proxy.common.context.NetworkTextReplaceContext;
import net.momirealms.craftengine.proxy.common.network.ChannelConnection;
import net.momirealms.craftengine.proxy.common.network.packet.PacketContext;
import net.momirealms.craftengine.proxy.common.network.packet.PacketHandler;
import net.momirealms.craftengine.proxy.common.network.packet.PacketHandlerRegistry;
import net.momirealms.craftengine.proxy.common.network.packet.PacketRoute;
import net.momirealms.craftengine.proxy.common.network.protocol.ConnectionState;
import net.momirealms.craftengine.proxy.common.network.protocol.packettype.PacketType;
import net.momirealms.craftengine.proxy.common.network.protocol.player.ClientVersion;
import net.momirealms.craftengine.proxy.common.platform.ProxyPlayer;
import net.momirealms.craftengine.proxy.common.tag.NetworkTagData;
import net.momirealms.craftengine.proxy.common.text.component.ComponentProvider;
import net.momirealms.craftengine.proxy.common.util.AdventureHelper;
import net.momirealms.craftengine.proxy.common.util.ProxyByteBuf;
import net.momirealms.sparrow.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PlayerInfoUpdateListener {
    private static final int ADD_PLAYER = 1;
    private static final int INITIALIZE_CHAT = 1 << 1;
    private static final int UPDATE_GAME_MODE = 1 << 2;
    private static final int UPDATE_LISTED = 1 << 3;
    private static final int UPDATE_LATENCY = 1 << 4;
    private static final int UPDATE_DISPLAY_NAME = 1 << 5;
    private static final int UPDATE_LIST_ORDER = 1 << 6;
    private static final int UPDATE_HAT = 1 << 7;

    private PlayerInfoUpdateListener() {}

    public static void register(PacketHandlerRegistry registry, ProxyCraftEngine plugin) {
        PacketRoute route = PacketRoute.typed(ConnectionState.PLAY, PacketType.Play.Server.PLAYER_INFO_UPDATE);
        registry.register(route, new Handler(plugin));
    }

    private static final class Handler implements PacketHandler {
        private final ProxyCraftEngine plugin;

        private Handler(ProxyCraftEngine plugin) {
            this.plugin = plugin;
        }

        @Override
        public void handle(ChannelConnection connection, @Nullable ProxyPlayer player, PacketContext packet) {
            if (player == null) return;
            NetworkTagData networkTagData = this.plugin.networkTagDataSyncService().getTagData(player);
            if (networkTagData == null) return;

            ClientVersion clientVersion = packet.clientVersion();
            ProxyByteBuf buf = packet.payload();
            int actions = buf.readUnsignedByte();
            if ((actions & UPDATE_DISPLAY_NAME) == 0) return;

            int entryCount = buf.readVarInt();
            List<EntryPayload> entries = new ArrayList<>(entryCount);
            NetworkTextReplaceContext context = new NetworkTextReplaceContext(player, networkTagData);
            boolean changed = false;

            for (int i = 0; i < entryCount; i++) {
                int entryStart = buf.readerIndex();
                skipFieldsBeforeDisplayName(buf, actions);

                int displayNameStart = buf.readerIndex();
                Component replacement = readReplacement(buf, clientVersion, networkTagData, context);
                int displayNameEnd = buf.readerIndex();

                skipFieldsAfterDisplayName(buf, actions);
                int entryEnd = buf.readerIndex();

                entries.add(new EntryPayload(
                        copyBytes(buf, entryStart, displayNameStart),
                        copyBytes(buf, displayNameStart, displayNameEnd),
                        copyBytes(buf, displayNameEnd, entryEnd),
                        replacement
                ));
                changed |= replacement != null;
            }

            if (!changed) return;
            packet.rewritePayload(replaceBuf -> {
                replaceBuf.writeVarInt(packet.packetID());
                replaceBuf.writeByte(actions);
                replaceBuf.writeVarInt(entryCount);
                for (EntryPayload entry : entries) {
                    replaceBuf.writeBytes(entry.beforeDisplayName());
                    if (entry.replacementDisplayName() == null) {
                        replaceBuf.writeBytes(entry.displayName());
                    } else {
                        replaceBuf.writeBoolean(true);
                        replaceBuf.writeComponent(clientVersion, entry.replacementDisplayName());
                    }
                    replaceBuf.writeBytes(entry.afterDisplayName());
                }
            });
        }
    }

    @Nullable
    private static Component readReplacement(
            ProxyByteBuf buf,
            ClientVersion clientVersion,
            NetworkTagData networkTagData,
            NetworkTextReplaceContext context
    ) {
        if (!buf.readBoolean()) return null;

        if (clientVersion.isOlderThan(ClientVersion.V_1_20_3)) {
            String json = buf.readUtf();
            Map<String, ComponentProvider> tokens = networkTagData.matchNetworkTags(json);
            if (tokens.isEmpty()) return null;
            return AdventureHelper.replaceText(
                    AdventureHelper.jsonToComponent(clientVersion, json),
                    tokens,
                    context
            );
        }

        Tag nbt = buf.readNbt(false);
        if (nbt == null) return null;
        Map<String, ComponentProvider> tokens = networkTagData.matchNetworkTags(nbt);
        if (tokens.isEmpty()) return null;
        return AdventureHelper.replaceText(
                AdventureHelper.tagToComponent(clientVersion, nbt),
                tokens,
                context
        );
    }

    private static void skipFieldsBeforeDisplayName(ProxyByteBuf buf, int actions) {
        buf.skipBytes(16);

        if ((actions & ADD_PLAYER) != 0) {
            buf.readUtf(16);
            int propertyCount = buf.readVarInt();
            for (int i = 0; i < propertyCount; i++) {
                buf.readUtf();
                buf.readUtf();
                if (buf.readBoolean()) {
                    buf.readUtf();
                }
            }
        }
        if ((actions & INITIALIZE_CHAT) != 0 && buf.readBoolean()) {
            buf.skipBytes(16);
            buf.skipBytes(Long.BYTES);
            skipByteArray(buf);
            skipByteArray(buf);
        }
        if ((actions & UPDATE_GAME_MODE) != 0) {
            buf.readVarInt();
        }
        if ((actions & UPDATE_LISTED) != 0) {
            buf.skipBytes(1);
        }
        if ((actions & UPDATE_LATENCY) != 0) {
            buf.readVarInt();
        }
    }

    private static void skipFieldsAfterDisplayName(ProxyByteBuf buf, int actions) {
        if ((actions & UPDATE_LIST_ORDER) != 0) {
            buf.readVarInt();
        }
        if ((actions & UPDATE_HAT) != 0) {
            buf.skipBytes(1);
        }
    }

    private static void skipByteArray(ProxyByteBuf buf) {
        buf.skipBytes(buf.readVarInt());
    }

    private static byte[] copyBytes(ProxyByteBuf buf, int start, int end) {
        byte[] bytes = new byte[end - start];
        buf.getBytes(start, bytes);
        return bytes;
    }

    private record EntryPayload(
            byte[] beforeDisplayName,
            byte[] displayName,
            byte[] afterDisplayName,
            @Nullable Component replacementDisplayName
    ) {}
}
