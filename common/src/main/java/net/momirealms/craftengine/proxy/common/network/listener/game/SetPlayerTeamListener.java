package net.momirealms.craftengine.proxy.common.network.listener.game;

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

import java.util.List;
import java.util.Map;

public final class SetPlayerTeamListener {
    private SetPlayerTeamListener() {}

    public static void register(PacketHandlerRegistry registry, ProxyCraftEngine plugin) {
        PacketRoute route = PacketRoute.typed(ConnectionState.PLAY, PacketType.Play.Server.TEAMS);
        registry.registerBetween(route, ClientVersion.V_1_20, ClientVersion.V_1_20_2, new V1_20(plugin));
        registry.register(route, ClientVersion.V_1_20_3, new V1_20_3(plugin));
        registry.registerBetween(route, ClientVersion.V_1_20_5, ClientVersion.V_26_1, new V1_20_5(plugin));
        registry.register(route, ClientVersion.V_26_2, new V26_2(plugin));
    }

    private static final class V1_20 implements PacketHandler {
        private final ProxyCraftEngine plugin;

        private V1_20(ProxyCraftEngine plugin) {
            this.plugin = plugin;
        }

        @Override
        public void handle(ChannelConnection connection, @Nullable ProxyPlayer player, PacketContext packet) {
            if (player == null) return;
            NetworkTagData netWorkTagData = this.plugin.networkTagDataSyncService().getTagData(player);
            if (netWorkTagData == null) return;

            ClientVersion clientVersion = packet.clientVersion();
            ProxyByteBuf buf = packet.payload();
            String name = buf.readUtf();
            byte method = buf.readByte();
            if (method != 2 && method != 0) return;
            String displayName = buf.readUtf();
            byte friendlyFlags = buf.readByte();
            String nameTagVisibility = buf.readUtf(40);
            String collisionRule = buf.readUtf(40);
            int color = buf.readVarInt();
            String prefix = buf.readUtf();
            String suffix = buf.readUtf();
            List<String> entities = method == 0 ? buf.readStringList() : null;

            Map<String, ComponentProvider> tokens1 = netWorkTagData.matchNetworkTags(displayName);
            Map<String, ComponentProvider> tokens2 = netWorkTagData.matchNetworkTags(prefix);
            Map<String, ComponentProvider> tokens3 = netWorkTagData.matchNetworkTags(suffix);
            if (tokens1.isEmpty() && tokens2.isEmpty() && tokens3.isEmpty()) return;

            NetworkTextReplaceContext context = new NetworkTextReplaceContext(player, netWorkTagData);
            packet.rewritePayload(replaceBuf -> {
                replaceBuf.writeVarInt(packet.packetID());
                replaceBuf.writeUtf(name);
                replaceBuf.writeByte(method);
                replaceBuf.writeUtf(tokens1.isEmpty() ? displayName : AdventureHelper.componentToJson(clientVersion, AdventureHelper.replaceText(AdventureHelper.jsonToComponent(clientVersion, displayName), tokens1, context)));
                replaceBuf.writeByte(friendlyFlags);
                replaceBuf.writeUtf(nameTagVisibility);
                replaceBuf.writeUtf(collisionRule);
                replaceBuf.writeVarInt(color);
                replaceBuf.writeUtf(tokens2.isEmpty() ? prefix : AdventureHelper.componentToJson(clientVersion, AdventureHelper.replaceText(AdventureHelper.jsonToComponent(clientVersion, prefix), tokens2, context)));
                replaceBuf.writeUtf(tokens3.isEmpty() ? suffix : AdventureHelper.componentToJson(clientVersion, AdventureHelper.replaceText(AdventureHelper.jsonToComponent(clientVersion, suffix), tokens3, context)));
                if (entities != null) {
                    replaceBuf.writeStringList(entities);
                }
            });
        }
    }

    private static final class V1_20_3 implements PacketHandler {
        private final ProxyCraftEngine plugin;

        private V1_20_3(ProxyCraftEngine plugin) {
            this.plugin = plugin;
        }

        @Override
        public void handle(ChannelConnection connection, @Nullable ProxyPlayer player, PacketContext packet) {
            if (player == null) return;
            NetworkTagData netWorkTagData = this.plugin.networkTagDataSyncService().getTagData(player);
            if (netWorkTagData == null) return;

            ClientVersion clientVersion = packet.clientVersion();
            ProxyByteBuf buf = packet.payload();
            String name = buf.readUtf();
            byte method = buf.readByte();
            if (method != 2 && method != 0) return;
            Tag displayName = buf.readNbt(false);
            if (displayName == null) return;
            byte friendlyFlags = buf.readByte();
            String visibility = buf.readUtf(40);
            String collisionRule = buf.readUtf(40);
            int color = buf.readVarInt();
            Tag prefix = buf.readNbt(false);
            if (prefix == null) return;
            Tag suffix = buf.readNbt(false);
            if (suffix == null) return;
            List<String> entities = method == 0 ? buf.readStringList() : null;

            Map<String, ComponentProvider> tokens1 = netWorkTagData.matchNetworkTags(displayName);
            Map<String, ComponentProvider> tokens2 = netWorkTagData.matchNetworkTags(prefix);
            Map<String, ComponentProvider> tokens3 = netWorkTagData.matchNetworkTags(suffix);
            if (tokens1.isEmpty() && tokens2.isEmpty() && tokens3.isEmpty()) return;

            NetworkTextReplaceContext context = new NetworkTextReplaceContext(player, netWorkTagData);
            packet.rewritePayload(replaceBuf -> {
                replaceBuf.writeVarInt(packet.packetID());
                replaceBuf.writeUtf(name);
                replaceBuf.writeByte(method);
                replaceBuf.writeNbt(tokens1.isEmpty() ? displayName : AdventureHelper.componentToTag(clientVersion, AdventureHelper.replaceText(AdventureHelper.tagToComponent(clientVersion, displayName), tokens1, context)), false);
                replaceBuf.writeByte(friendlyFlags);
                replaceBuf.writeUtf(visibility);
                replaceBuf.writeUtf(collisionRule);
                replaceBuf.writeVarInt(color);
                replaceBuf.writeNbt(tokens2.isEmpty() ? prefix : AdventureHelper.componentToTag(clientVersion, AdventureHelper.replaceText(AdventureHelper.tagToComponent(clientVersion, prefix), tokens2, context)), false);
                replaceBuf.writeNbt(tokens3.isEmpty() ? suffix : AdventureHelper.componentToTag(clientVersion, AdventureHelper.replaceText(AdventureHelper.tagToComponent(clientVersion, suffix), tokens3, context)), false);
                if (entities != null) {
                    replaceBuf.writeStringList(entities);
                }
            });
        }
    }

    private static final class V1_20_5 implements PacketHandler {
        private final ProxyCraftEngine plugin;

        private V1_20_5(ProxyCraftEngine plugin) {
            this.plugin = plugin;
        }

        @Override
        public void handle(ChannelConnection connection, @Nullable ProxyPlayer player, PacketContext packet) {
            if (player == null) return;
            NetworkTagData netWorkTagData = this.plugin.networkTagDataSyncService().getTagData(player);
            if (netWorkTagData == null) return;

            ClientVersion clientVersion = packet.clientVersion();
            ProxyByteBuf buf = packet.payload();
            String name = buf.readUtf();
            byte method = buf.readByte();
            if (method != 2 && method != 0) return;
            Tag displayName = buf.readNbt(false);
            if (displayName == null) return;
            byte friendlyFlags = buf.readByte();
            int visibility = buf.readVarInt();
            int collisionRule = buf.readVarInt();
            int color = buf.readVarInt();
            Tag prefix = buf.readNbt(false);
            if (prefix == null) return;
            Tag suffix = buf.readNbt(false);
            if (suffix == null) return;
            List<String> entities = method == 0 ? buf.readStringList() : null;

            Map<String, ComponentProvider> tokens1 = netWorkTagData.matchNetworkTags(displayName);
            Map<String, ComponentProvider> tokens2 = netWorkTagData.matchNetworkTags(prefix);
            Map<String, ComponentProvider> tokens3 = netWorkTagData.matchNetworkTags(suffix);
            if (tokens1.isEmpty() && tokens2.isEmpty() && tokens3.isEmpty()) return;

            NetworkTextReplaceContext context = new NetworkTextReplaceContext(player, netWorkTagData);
            packet.rewritePayload(replaceBuf -> {
                replaceBuf.writeVarInt(packet.packetID());
                replaceBuf.writeUtf(name);
                replaceBuf.writeByte(method);
                replaceBuf.writeNbt(tokens1.isEmpty() ? displayName : AdventureHelper.componentToTag(clientVersion, AdventureHelper.replaceText(AdventureHelper.tagToComponent(clientVersion, displayName), tokens1, context)), false);
                replaceBuf.writeByte(friendlyFlags);
                replaceBuf.writeVarInt(visibility);
                replaceBuf.writeVarInt(collisionRule);
                replaceBuf.writeVarInt(color);
                replaceBuf.writeNbt(tokens2.isEmpty() ? prefix : AdventureHelper.componentToTag(clientVersion, AdventureHelper.replaceText(AdventureHelper.tagToComponent(clientVersion, prefix), tokens2, context)), false);
                replaceBuf.writeNbt(tokens3.isEmpty() ? suffix : AdventureHelper.componentToTag(clientVersion, AdventureHelper.replaceText(AdventureHelper.tagToComponent(clientVersion, suffix), tokens3, context)), false);
                if (entities != null) {
                    replaceBuf.writeStringList(entities);
                }
            });
        }
    }

    private static final class V26_2 implements PacketHandler {
        private final ProxyCraftEngine plugin;

        private V26_2(ProxyCraftEngine plugin) {
            this.plugin = plugin;
        }

        @Override
        public void handle(ChannelConnection connection, @Nullable ProxyPlayer player, PacketContext packet) {
            if (player == null) return;
            NetworkTagData netWorkTagData = this.plugin.networkTagDataSyncService().getTagData(player);
            if (netWorkTagData == null) return;

            ClientVersion clientVersion = packet.clientVersion();
            ProxyByteBuf buf = packet.payload();
            String name = buf.readUtf();
            byte method = buf.readByte();
            if (method != 2 && method != 0) return;
            Tag displayName = buf.readNbt(false);
            if (displayName == null) return;
            Tag prefix = buf.readNbt(false);
            if (prefix == null) return;
            Tag suffix = buf.readNbt(false);
            if (suffix == null) return;
            int visibility = buf.readVarInt();
            int collisionRule = buf.readVarInt();
            @Nullable Integer color = buf.readNullable(ProxyByteBuf::readVarInt);
            byte options = buf.readByte();
            List<String> entities = method == 0 ? buf.readStringList() : null;

            Map<String, ComponentProvider> tokens1 = netWorkTagData.matchNetworkTags(displayName);
            Map<String, ComponentProvider> tokens2 = netWorkTagData.matchNetworkTags(prefix);
            Map<String, ComponentProvider> tokens3 = netWorkTagData.matchNetworkTags(suffix);
            if (tokens1.isEmpty() && tokens2.isEmpty() && tokens3.isEmpty()) return;

            NetworkTextReplaceContext context = new NetworkTextReplaceContext(player, netWorkTagData);
            packet.rewritePayload(replaceBuf -> {
                replaceBuf.writeVarInt(packet.packetID());
                replaceBuf.writeUtf(name);
                replaceBuf.writeByte(method);
                replaceBuf.writeNbt(tokens1.isEmpty() ? displayName : AdventureHelper.componentToTag(clientVersion, AdventureHelper.replaceText(AdventureHelper.tagToComponent(clientVersion, displayName), tokens1, context)), false);
                replaceBuf.writeNbt(tokens2.isEmpty() ? prefix : AdventureHelper.componentToTag(clientVersion, AdventureHelper.replaceText(AdventureHelper.tagToComponent(clientVersion, prefix), tokens2, context)), false);
                replaceBuf.writeNbt(tokens3.isEmpty() ? suffix : AdventureHelper.componentToTag(clientVersion, AdventureHelper.replaceText(AdventureHelper.tagToComponent(clientVersion, suffix), tokens3, context)), false);
                replaceBuf.writeVarInt(visibility);
                replaceBuf.writeVarInt(collisionRule);
                replaceBuf.writeNullable(color, ProxyByteBuf::writeVarInt);
                replaceBuf.writeByte(options);
                if (entities != null) {
                    replaceBuf.writeStringList(entities);
                }
            });
        }
    }
}
