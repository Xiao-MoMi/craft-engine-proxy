package net.momirealms.craftengine.proxy.common.text.font;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

public final class OffsetFont {
    public final Key fontKey;

    public final String NEG_16;
    public final String NEG_32;
    public final String NEG_48;
    public final String NEG_64;
    public final String NEG_128;
    public final String NEG_256;

    public final String POS_16;
    public final String POS_32;
    public final String POS_48;
    public final String POS_64;
    public final String POS_128;
    public final String POS_256;
    public final String[] negativeOffsets;
    public final String[] positiveOffsets;

    private final Cache<Integer, String> fastLookup = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(256)
            .build();

    public OffsetFont(
            net.momirealms.craftengine.proxy.common.util.Key font,
            String neg16, String neg32, String neg48, String neg64, String neg128, String neg256,
            String pos16, String pos32, String pos48, String pos64, String pos128, String pos256,
            String[] negativeOffsets, String[] positiveOffsets
    ) {
        this.fontKey = Key.key(font.namespace(), font.value());
        this.NEG_16 = neg16;
        this.NEG_32 = neg32;
        this.NEG_48 = neg48;
        this.NEG_64 = neg64;
        this.NEG_128 = neg128;
        this.NEG_256 = neg256;
        this.POS_16 = pos16;
        this.POS_32 = pos32;
        this.POS_48 = pos48;
        this.POS_64 = pos64;
        this.POS_128 = pos128;
        this.POS_256 = pos256;
        this.negativeOffsets = negativeOffsets;
        this.positiveOffsets = positiveOffsets;
    }

    public Component createOffset(int offset) {
        if (offset == 0) return Component.empty();
        return Component.text(Objects.requireNonNull(this.fastLookup.get(offset, k -> k > 0 ? createPos(k) : createNeg(-k)))).font(this.fontKey);
    }

    public String createOffset(int offset, BiFunction<String, String, String> tagDecorator) {
        if (offset == 0) return "";
        return tagDecorator.apply(this.fastLookup.get(offset, k -> k > 0 ? createPos(k) : createNeg(-k)), this.fontKey.asString());
    }

    @SuppressWarnings("DuplicatedCode")
    private String createPos(int offset) {
        StringBuilder stringBuilder = new StringBuilder();
        while (offset >= 256) {
            stringBuilder.append(POS_256);
            offset -= 256;
        }
        if (offset >= 128) {
            stringBuilder.append(POS_128);
            offset -= 128;
        }
        if (offset >= 64) {
            stringBuilder.append(POS_64);
            offset -= 64;
        }
        if (offset >= 48) {
            stringBuilder.append(POS_48);
            offset -= 48;
        }
        if (offset >= 32) {
            stringBuilder.append(POS_32);
            offset -= 32;
        }
        if (offset >= 16) {
            stringBuilder.append(POS_16);
            offset -= 16;
        }
        if (offset == 0) return stringBuilder.toString();
        stringBuilder.append(positiveOffsets[offset]);
        return stringBuilder.toString();
    }

    @SuppressWarnings("DuplicatedCode")
    private String createNeg(int offset) {
        StringBuilder stringBuilder = new StringBuilder();
        while (offset >= 256) {
            stringBuilder.append(NEG_256);
            offset -= 256;
        }
        if (offset >= 128) {
            stringBuilder.append(NEG_128);
            offset -= 128;
        }
        if (offset >= 64) {
            stringBuilder.append(NEG_64);
            offset -= 64;
        }
        if (offset >= 48) {
            stringBuilder.append(NEG_48);
            offset -= 48;
        }
        if (offset >= 32) {
            stringBuilder.append(NEG_32);
            offset -= 32;
        }
        if (offset >= 16) {
            stringBuilder.append(NEG_16);
            offset -= 16;
        }
        if (offset == 0) return stringBuilder.toString();
        stringBuilder.append(negativeOffsets[offset]);
        return stringBuilder.toString();
    }
}
