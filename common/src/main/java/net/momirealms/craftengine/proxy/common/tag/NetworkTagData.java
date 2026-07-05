package net.momirealms.craftengine.proxy.common.tag;

import net.kyori.adventure.text.minimessage.internal.parser.Token;
import net.kyori.adventure.text.minimessage.internal.parser.TokenParser;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.momirealms.craftengine.proxy.common.text.component.ComponentProvider;
import net.momirealms.craftengine.proxy.common.text.font.BitmapImage;
import net.momirealms.craftengine.proxy.common.text.font.Image;
import net.momirealms.craftengine.proxy.common.text.font.OffsetFont;
import net.momirealms.craftengine.proxy.common.text.locale.ServerLangData;
import net.momirealms.craftengine.proxy.common.text.minimessage.GlobalVariableTag;
import net.momirealms.craftengine.proxy.common.text.minimessage.ImageTag;
import net.momirealms.craftengine.proxy.common.text.minimessage.ShiftTag;
import net.momirealms.craftengine.proxy.common.util.Key;
import net.momirealms.craftengine.proxy.common.util.MiscUtils;
import net.momirealms.craftengine.proxy.common.util.StringValueOnlyTagVisitor;
import net.momirealms.sparrow.nbt.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class NetworkTagData {
    public static final Set<String> NETWORK_TAGS = Set.of("image", "l10n", "shift", "global");
    public static final Locale SYSTEM_LOCALE = Locale.getDefault();

    private final String serverName;
    private final long version;
    private final OffsetFont offset;
    private final Map<Key, Image> images;
    private final Map<String, Image> imageByIdValue;
    private final Map<String, ServerLangData> l10n;
    private final Map<String, String> globalVariables;
    private final TagResolver[] tagResolvers;
    private final Map<String, ComponentProvider> networkTagMapper;

    public NetworkTagData(
            String serverName,
            long version,
            OffsetFont offset,
            Map<Key, Image> images,
            Map<String, ServerLangData> l10n,
            Map<String, String> globalVariables
    ) {
        this.serverName = serverName;
        this.version = version;
        this.offset = offset;
        this.images = images;
        this.imageByIdValue = images.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().value,
                        Map.Entry::getValue,
                        (oldValue, newValue) -> newValue
                ));
        this.l10n = l10n;
        this.globalVariables = globalVariables;
        this.tagResolvers = new TagResolver[] {
                new ShiftTag(this),
                new ImageTag(this),
                new GlobalVariableTag(this)
        };
        this.networkTagMapper = MiscUtils.init(new HashMap<>(), it -> {
            for (int i = -256; i <= 256; i++) {
                it.put(shiftTag(i), ComponentProvider.constant(NetworkTagData.this.offset.createOffset(i)));
            }
            for (String key : this.l10n.keySet()) {
                it.put(l10nTag(key), ComponentProvider.l10n(key, this));
            }
            for (Map.Entry<String, String> entry : this.globalVariables.entrySet()) {
                String globalTag = globalTag(entry.getKey());
                it.put(globalTag, ComponentProvider.miniMessageOrConstant(entry.getValue()));
            }
            for (Image image : this.images.values()) {
                Key key = image.id();
                String id = key.toString();
                String simpleImageTag = imageTag(id);
                it.put(simpleImageTag, ComponentProvider.constant(image.componentAt(0, 0)));
                String simplerImageTag = imageTag(key.value());
                it.put(simplerImageTag, ComponentProvider.constant(image.componentAt(0, 0)));
                if (image instanceof BitmapImage bitmapImage) {
                    for (int i = 0; i < bitmapImage.rows(); i++) {
                        String partialArgs = id + ":" + i;
                        it.put(imageTag(partialArgs), ComponentProvider.constant(image.componentAt(i, 0)));
                        for (int j = 0; j < bitmapImage.columns(); j++) {
                            String imageArgs = id + ":" + i + ":" + j;
                            String imageTag = imageTag(imageArgs);
                            it.put(imageTag, ComponentProvider.constant(image.componentAt(i, j)));
                        }
                    }
                }
            }
        });
    }

    @SuppressWarnings("UnstableApiUsage")
    public Map<String, ComponentProvider> matchNetworkTags(String text) {
        Map<String, ComponentProvider> tags = new HashMap<>();
        List<Token> root = TokenParser.tokenize(text, true);
        for (final Token token : root) {
            switch (token.type()) {
                case TEXT: break;
                case OPEN_TAG:
                case CLOSE_TAG:
                case OPEN_CLOSE_TAG:
                    if (token.childTokens().isEmpty()) {
                        continue;
                    }
                    final String sanitized = TokenParser.TagProvider.sanitizePlaceholderName(token.childTokens().getFirst().get(text).toString());
                    if (NETWORK_TAGS.contains(sanitized)) {
                        String tag = text.substring(token.startIndex(), token.endIndex());
                        tags.computeIfAbsent(tag, k -> Optional
                                .ofNullable(this.networkTagMapper.get(k))
                                .orElse(ComponentProvider.miniMessage(k))
                        );
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported token type " + token.type());
            }
        }
        return tags;
    }

    public Map<String, ComponentProvider> matchNetworkTags(Tag nbt) {
        return this.matchNetworkTags(new StringValueOnlyTagVisitor().visit(nbt));
    }

    @Nullable
    public Image imageById(Key id) {
        return this.images.get(id);
    }

    @Nullable
    public Image imageByIdValue(String id) {
        return this.imageByIdValue.get(id);
    }

    @Nullable
    public String getGlobalVariable(String key) {
        return this.globalVariables.get(key);
    }

    @Nullable
    public ServerLangData getServerLangData(String key) {
        return this.l10n.get(key);
    }

    @NotNull
    public String miniMessageTranslation(String key, @Nullable Locale locale) {
        ServerLangData serverLangData = this.getServerLangData(key);
        if (serverLangData == null) {
            return key;
        }
        if (locale == null) {
            locale = SYSTEM_LOCALE;
        }
        return Optional.ofNullable(serverLangData.translate(locale)).orElse(key);
    }

    public String serverName() {
        return this.serverName;
    }

    public long version() {
        return this.version;
    }

    public OffsetFont offset() {
        return this.offset;
    }

    public Map<Key, Image> images() {
        return Collections.unmodifiableMap(images);
    }

    public Map<String, String> global() {
        return Collections.unmodifiableMap(globalVariables);
    }

    public Map<String, ServerLangData> l10n() {
        return Collections.unmodifiableMap(l10n);
    }

    public TagResolver[] tagResolvers() {
        return this.tagResolvers;
    }

    private static String imageTag(String text) {
        return "<image:" + text + ">";
    }

    private static String globalTag(String text) {
        return "<global:" + text + ">";
    }

    private static String l10nTag(String text) {
        return "<l10n:" + text + ">";
    }

    private static String shiftTag(int offset) {
        return "<shift:" + offset + ">";
    }
}
