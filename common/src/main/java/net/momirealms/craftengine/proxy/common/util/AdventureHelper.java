package net.momirealms.craftengine.proxy.common.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.JsonElement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.json.JSONOptions;
import net.kyori.adventure.text.serializer.json.legacyimpl.NBTLegacyHoverEventSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.momirealms.craftengine.proxy.common.context.Context;
import net.momirealms.craftengine.proxy.common.network.protocol.player.ClientVersion;
import net.momirealms.craftengine.proxy.common.text.component.ComponentProvider;
import net.momirealms.sparrow.nbt.Tag;
import net.momirealms.sparrow.nbt.adventure.NBTComponentSerializer;
import net.momirealms.sparrow.nbt.adventure.NBTSerializerOptions;
import net.momirealms.sparrow.reflection.clazz.SparrowClass;
import net.momirealms.sparrow.reflection.field.matcher.FieldMatcher;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class AdventureHelper {
    private static final Cache<String, Pattern> PATTERN_CACHE = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();
    private final MiniMessage miniMessage;
    private final MiniMessage miniMessageStrict;
    private final MiniMessage miniMessageCustom;
    private final Map<ClientVersion, GsonComponentSerializer> gsonComponentSerializers;
    private final Map<ClientVersion, NBTComponentSerializer> nbtComponentSerializers;
    private final LegacyComponentSerializer legacyComponentSerializer;

    static {
        // Shadow
        SparrowClass.of(SparrowClass.findNoRemap("net.kyori.adventure.text.TextComponentImpl")).getDeclaredSparrowField(FieldMatcher.named("WARN_WHEN_LEGACY_FORMATTING_DETECTED")).mh().set(null, false);
        // Velocity
        Class<?> textComponentImplClass = SparrowClass.findNoRemap("net{}kyori{}adventure{}text{}TextComponentImpl".replace("{}", "."));
        if (textComponentImplClass != null) {
            SparrowClass.of(textComponentImplClass).getDeclaredSparrowField(FieldMatcher.named("WARN_WHEN_LEGACY_FORMATTING_DETECTED")).mh().set(null, false);
        }
    }

    private AdventureHelper() {
        this.miniMessage = MiniMessage.builder().build();
        this.miniMessageStrict = MiniMessage.builder().strict(true).build();
        this.miniMessageCustom = MiniMessage.builder().tags(TagResolver.empty()).build();
        this.legacyComponentSerializer = LegacyComponentSerializer.builder().build();
        this.gsonComponentSerializers = createGsonSerializers();
        this.nbtComponentSerializers = createNBTSerializers();
    }

    public static void init() {}

    public static Component replaceText(Component text, Map<String, ComponentProvider> replacements, Context context) {
        int size = replacements.size();
        if (size == 0) return text;
        final Pattern pattern;
        if (size == 1) {
            pattern = Pattern.compile(Pattern.quote(replacements.keySet().iterator().next()));
        } else {
            String patternString = replacements.keySet().stream()
                    .map(Pattern::quote)
                    .collect(Collectors.joining("|"));
            pattern = Objects.requireNonNull(PATTERN_CACHE.get(patternString, Pattern::compile));
        }
        return replaceText(text, pattern, result ->
                Optional.ofNullable(replacements.get(result.group())).orElseThrow(() -> new IllegalStateException("Could not find tag '" + result.group() + "'")).apply(context)
        );
    }

    private static Component replaceText(Component text, Pattern pattern, Function<MatchResult, Component> replacement) {
        return FixedTextReplacementRenderer.INSTANCE.render(text, new FixedTextReplacementRenderer.State(pattern, replacement));
    }

    private static class SingletonHolder {
        private static final AdventureHelper INSTANCE = new AdventureHelper();
    }

    public static AdventureHelper getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public static MiniMessage miniMessage() {
        return getInstance().miniMessage;
    }

    public static MiniMessage customMiniMessage() {
        return getInstance().miniMessageCustom;
    }

    public static MiniMessage strictMiniMessage() {
        return getInstance().miniMessageStrict;
    }

    public static LegacyComponentSerializer getLegacy() {
        return getInstance().legacyComponentSerializer;
    }

    public static GsonComponentSerializer getGson(ClientVersion clientVersion) {
        return getInstance().gsonComponentSerializers.get(normalizeVersion(clientVersion));
    }

    public static NBTComponentSerializer getNBT(ClientVersion clientVersion) {
        return getInstance().nbtComponentSerializers.get(normalizeVersion(clientVersion));
    }

    /**
     * Converts a JSON string to a MiniMessage string.
     *
     * @param json the JSON string
     * @return the MiniMessage string representation
     */
    public static String jsonToMiniMessage(String json, ClientVersion clientVersion) {
        return getInstance().miniMessageStrict.serialize(getGson(clientVersion).deserialize(json));
    }

    public static String componentToMiniMessage(Component component) {
        return getInstance().miniMessageStrict.serialize(component);
    }

    /**
     * Converts a JSON string to a Component.
     *
     * @param json the JSON string
     * @return the resulting Component
     */
    public static Component jsonToComponent(ClientVersion clientVersion, String json) {
        return getGson(clientVersion).deserialize(json);
    }

    public static Component jsonElementToComponent(ClientVersion clientVersion, JsonElement json) {
        return getGson(clientVersion).deserializeFromTree(json);
    }

    public static Component nbtToComponent(ClientVersion clientVersion, Tag tag) {
        return getNBT(clientVersion).deserialize(tag);
    }

    public static Tag componentToNbt(ClientVersion clientVersion, Component component) {
        return getNBT(clientVersion).serialize(component);
    }

    /**
     * Converts a Component to a JSON string.
     *
     * @param component the Component to convert
     * @return the JSON string representation
     */
    public static String componentToJson(ClientVersion clientVersion, Component component) {
        return getGson(clientVersion).serialize(component);
    }

    public static JsonElement componentToJsonElement(ClientVersion clientVersion, Component component) {
        return getGson(clientVersion).serializeToTree(component);
    }

    public static Tag componentToTag(ClientVersion clientVersion, Component component) {
        return getNBT(clientVersion).serialize(component);
    }

    public static Component tagToComponent(ClientVersion clientVersion, Tag tag) {
        return getNBT(clientVersion).deserialize(tag);
    }

    private static ClientVersion normalizeVersion(ClientVersion clientVersion) {
        if (clientVersion == null || !clientVersion.isRelease()) {
            return ClientVersion.getLatest();
        }
        return clientVersion;
    }

    private static Map<ClientVersion, GsonComponentSerializer> createGsonSerializers() {
        GsonComponentSerializer legacySerializer = createGsonSerializer(ClientVersion.V_1_20_3);
        GsonComponentSerializer preModernEventSerializer = createGsonSerializer(ClientVersion.V_1_21_4);
        GsonComponentSerializer latestSerializer = createGsonSerializer(ClientVersion.getLatest());
        Map<ClientVersion, GsonComponentSerializer> serializers = new EnumMap<>(ClientVersion.class);
        for (ClientVersion version : ClientVersion.values()) {
            if (!version.isRelease()) {
                continue;
            }
            if (version.isOlderThanOrEquals(ClientVersion.V_1_20_3)) {
                serializers.put(version, legacySerializer);
            } else if (version.isOlderThanOrEquals(ClientVersion.V_1_21_4)) {
                serializers.put(version, preModernEventSerializer);
            } else {
                serializers.put(version, latestSerializer);
            }
        }
        return serializers;
    }

    private static Map<ClientVersion, NBTComponentSerializer> createNBTSerializers() {
        NBTComponentSerializer legacySerializer = createNBTSerializer(ClientVersion.V_1_20_2);
        NBTComponentSerializer legacyUuidSerializer = createNBTSerializer(ClientVersion.V_1_20_3);
        NBTComponentSerializer preModernEventSerializer = createNBTSerializer(ClientVersion.V_1_21_4);
        NBTComponentSerializer latestSerializer = createNBTSerializer(ClientVersion.getLatest());
        Map<ClientVersion, NBTComponentSerializer> serializers = new EnumMap<>(ClientVersion.class);
        for (ClientVersion version : ClientVersion.values()) {
            if (!version.isRelease()) {
                continue;
            }
            if (version.isOlderThanOrEquals(ClientVersion.V_1_20_2)) {
                serializers.put(version, legacySerializer);
            } else if (version.isOlderThanOrEquals(ClientVersion.V_1_20_3)) {
                serializers.put(version, legacyUuidSerializer);
            } else if (version.isOlderThanOrEquals(ClientVersion.V_1_21_4)) {
                serializers.put(version, preModernEventSerializer);
            } else {
                serializers.put(version, latestSerializer);
            }
        }
        return serializers;
    }

    private static GsonComponentSerializer createGsonSerializer(ClientVersion clientVersion) {
        GsonComponentSerializer.Builder gsonBuilder = GsonComponentSerializer.builder();
        if (clientVersion.isOlderThan(ClientVersion.V_1_20_5)) {
            gsonBuilder.legacyHoverEventSerializer(NBTLegacyHoverEventSerializer.get());
            gsonBuilder.editOptions((builder) -> builder.value(JSONOptions.EMIT_HOVER_SHOW_ENTITY_ID_AS_INT_ARRAY, false));
        }
        if (clientVersion.isOlderThan(ClientVersion.V_1_21_5)) {
            gsonBuilder.editOptions((builder) -> {
                builder.value(JSONOptions.EMIT_CLICK_EVENT_TYPE, JSONOptions.ClickEventValueMode.CAMEL_CASE);
                builder.value(JSONOptions.EMIT_HOVER_EVENT_TYPE, JSONOptions.HoverEventValueMode.CAMEL_CASE);
                builder.value(JSONOptions.EMIT_HOVER_SHOW_ENTITY_KEY_AS_TYPE_AND_UUID_AS_ID, true);
            });
        }
        return gsonBuilder.build();
    }

    private static NBTComponentSerializer createNBTSerializer(ClientVersion clientVersion) {
        return NBTComponentSerializer.builder()
                .editOptions((builder) -> {
                    if (clientVersion.isOlderThan(ClientVersion.V_1_21_5)) {
                        builder.value(NBTSerializerOptions.MODERN_EVENT_TYPE, false);
                    }
                    if (clientVersion.isOlderThan(ClientVersion.V_1_20_5)) {
                        builder.value(NBTSerializerOptions.DATA_COMPONENT_RELEASE, false);
                    }
                    if (clientVersion.isOlderThan(ClientVersion.V_1_20_3)) {
                        builder.value(NBTSerializerOptions.INT_ARRAY_UUID, false);
                    }
                    builder.value(NBTSerializerOptions.SERIALIZE_COMPONENT_TYPE, false);
                }).build();
    }
}
