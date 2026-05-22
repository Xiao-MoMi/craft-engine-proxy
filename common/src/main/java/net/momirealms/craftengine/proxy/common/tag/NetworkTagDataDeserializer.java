package net.momirealms.craftengine.proxy.common.tag;

import net.momirealms.craftengine.proxy.common.text.font.*;
import net.momirealms.craftengine.proxy.common.text.locale.ServerLangData;
import net.momirealms.craftengine.proxy.common.util.Key;
import net.momirealms.craftengine.proxy.common.util.LazyReference;
import net.momirealms.craftengine.proxy.common.util.ProxyByteBuf;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class NetworkTagDataDeserializer {
    private NetworkTagDataDeserializer() {}

    public static NetworkTagData read(long version, ProxyByteBuf buf, NetworkTagDataRegistry registry, String serverName) {
        OffsetFont offsetFont = readOffsetFont(buf);
        Map<Key, Image> images = readImage(buf, registry, serverName);
        Map<String, ServerLangData> l10n = readL10n(buf);
        Map<String, String> global = readGlobal(buf);
        return new NetworkTagData(serverName, version, offsetFont, images, l10n, global);
    }

    private static OffsetFont readOffsetFont(ProxyByteBuf buf) {
        Key font = buf.readKey();

        String[] negativeOffsets = new String[16];
        for (int i = 1; i <= 15; i++) {
            negativeOffsets[i] = buf.readUtf();
        }
        String NEG_16 = buf.readUtf();
        String NEG_24 = buf.readUtf();
        String NEG_32 = buf.readUtf();
        String NEG_48 = buf.readUtf();
        String NEG_64 = buf.readUtf();
        String NEG_128 = buf.readUtf();
        String NEG_256 = buf.readUtf();

        String[] positiveOffsets = new String[16];
        for (int i = 1; i <= 15; i++) {
            positiveOffsets[i] = buf.readUtf();
        }
        String POS_16 = buf.readUtf();
        String POS_24 = buf.readUtf();
        String POS_32 = buf.readUtf();
        String POS_48 = buf.readUtf();
        String POS_64 = buf.readUtf();
        String POS_128 = buf.readUtf();
        String POS_256 = buf.readUtf();

        return new OffsetFont(
                font,
                NEG_16, NEG_24, NEG_32, NEG_48, NEG_64, NEG_128, NEG_256,
                POS_16, POS_24, POS_32, POS_48, POS_64, POS_128, POS_256,
                negativeOffsets, positiveOffsets
        );
    }

    private static Map<Key, Image> readImage(ProxyByteBuf buf, NetworkTagDataRegistry registry, String serverName) {
        Map<Key, Image> images = new HashMap<>();
        int imageSize = buf.readVarInt();
        for (int i = 0; i < imageSize; i++) {
            Key key = buf.readKey();
            byte type = buf.readByte();
            Image image = type == 0 ? readBitmapImage(buf) : (type == 1 ? readReferenceImage(buf, registry, serverName): null);
            if (image != null) {
                images.put(key, image);
            }
        }
        return images;
    }

    private static BitmapImage readBitmapImage(ProxyByteBuf buf) {
        Key id = buf.readKey();
        Key font = buf.readKey();

        int[][] codepointGrid = null;
        int rows = buf.readInt();
        if (rows != -1) {
            int cols = buf.readInt();
            codepointGrid = new int[rows][cols];
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    codepointGrid[i][j] = buf.readInt();
                }
            }
        }

        return new BitmapImage(id, font, codepointGrid);
    }

    private static ReferenceImage readReferenceImage(ProxyByteBuf buf, NetworkTagDataRegistry registry, String serverName) {
        Key refId = buf.readKey();
        return new ReferenceImage(LazyReference.lazyReference(() -> {
            NetworkTagData netWorkTagData = registry.get(serverName);
            if (netWorkTagData != null) {
                Image img = netWorkTagData.images().get(refId);
                if (img instanceof BitmapImage bitmapImage) {
                    return bitmapImage;
                }
            }
            return DummyImage.INSTANCE;
        }), refId, 0, 0);
    }

    private static Map<String, ServerLangData> readL10n(ProxyByteBuf buf) {
        return buf.readMap(ProxyByteBuf::readUtf, NetworkTagDataDeserializer::readServerLangData);
    }

    private static ServerLangData readServerLangData(ProxyByteBuf buf) {
        String fallback = buf.readBoolean() ? buf.readUtf() : null;
        ServerLangData serverLangData = new ServerLangData(fallback);
        Map<Locale, String> translations = buf.readMap(b -> Locale.forLanguageTag(b.readUtf()), ProxyByteBuf::readUtf);
        serverLangData.addTranslations(translations);
        return serverLangData;
    }

    private static Map<String, String> readGlobal(ProxyByteBuf buf) {
        return buf.readMap(ProxyByteBuf::readUtf, ProxyByteBuf::readUtf);
    }
}
