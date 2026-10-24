package com.sighs.baeffect;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sighs.apricityui.init.Document;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Mod(Baeffect.MODID)
public final class Baeffect {
    public static final String MODID = "baeffect";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    private static final String CONFIG_FILE = "baeffect.toml";
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();
    private static final List<String> PRESETS = List.of("classic", "light-background", "soft", "performance", "custom");
    private static final List<String> COMPOSITING = List.of("add", "normal");
    private static final List<String> MOTION = List.of("system", "full", "reduced");
    private static final Map<String, Object> DEFAULTS = createDefaults();
    private static final Map<String, Object> VALUES = new LinkedHashMap<>(DEFAULTS);

    private static int revision = 1;

    public Baeffect() {
        load();
        if (FMLEnvironment.dist.isClient()) {
            MinecraftForge.EVENT_BUS.register(ClientEvents.class);
            MinecraftForge.EVENT_BUS.register(BAClickFxHtmlSettings.class);
            PhiraRespackManager.ensureScanned();
            if ("phira".equalsIgnoreCase(text("fxStyle"))) {
                String id = text("phiraRespack");
                PhiraRespack pack = PhiraRespackManager.get(id);
                if (pack == null && !PhiraRespackManager.isEmpty()) {
                    id = PhiraRespackManager.ids().get(0);
                    applyPatch(Map.of("phiraRespack", id));
                    pack = PhiraRespackManager.get(id);
                    LOGGER.info("[BAEffect] Loading Phira Pack：{}", pack == null ? "null" : pack.name);
                }
                PhiraRespackManager.load(pack);
            }
        }
    }

    public static double num(String key) {
        Object value = VALUES.get(key);
        return value instanceof Number number ? number.doubleValue() : 0.0D;
    }

    public static boolean flag(String key) {
        return VALUES.get(key) instanceof Boolean value && value;
    }

    public static String text(String key) {
        Object value = VALUES.get(key);
        return value instanceof String string ? string : "";
    }

    public static Object value(String key) {
        return VALUES.get(key);
    }

    public static int revision() {
        return revision;
    }

    public static String json() {
        return GSON.toJson(VALUES);
    }

    public static double referenceUnit() {
        double height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        double raw = Math.max(height, 1.0D) / 1080.0D * Math.max(num("scale"), 0.01D);
        return Math.max(0.12D, Math.min(0.72D, raw)) * 0.9D;
    }

    public static void applyPatch(Map<String, Object> patch) {
        if (patch == null || patch.isEmpty()) return;

        boolean changed = false;
        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            String key = entry.getKey();
            Object def = DEFAULTS.get(key);
            if (def == null || entry.getValue() == null) continue;

            Object next = coerced(def, entry.getValue());
            if (next.equals(VALUES.get(key))) continue;

            VALUES.put(key, next);
            changed = true;
        }
        if (!changed) return;

        normalize();
        save();
    }

    public static void applyPreset(String name) {
        String preset = PRESETS.contains(name) ? name : "custom";
        switch (preset) {
            case "classic" -> presetValues("#4ca7ff", 1.0D, 1.0D, 2.0D, true, "add");
            case "light-background" -> presetValues("#4ca7ff", 1.0D, 1.0D, 2.0D, true, "normal");
            case "soft" -> presetValues("#8edcff", 0.35D, 0.9D, 1.0D, false, "add");
            case "performance" -> presetValues("#4ca7ff", 0.45D, 1.0D, 1.0D, false, "add");
            default -> {
            }
        }
        VALUES.put("preset", preset);
        normalize();
        save();
    }

    private static void presetValues(String color, double opacity, double scale, double maxDpr, boolean bloom, String compositing) {
        VALUES.put("color", color);
        VALUES.put("opacity", opacity);
        VALUES.put("scale", scale);
        VALUES.put("maxDpr", maxDpr);
        VALUES.put("bloom", bloom);
        VALUES.put("compositing", compositing);
    }

    public static void reset() {
        VALUES.clear();
        VALUES.putAll(DEFAULTS);
        normalize();
        save();
    }

    public static void load() {
        VALUES.clear();
        VALUES.putAll(DEFAULTS);

        Path path = configPath();
        if (!Files.isRegularFile(path)) {
            save();
            return;
        }

        try {
            for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("[")) continue;

                int split = line.indexOf('=');
                if (split <= 0) continue;

                String key = unquote(line.substring(0, split).trim());
                Object def = DEFAULTS.get(key);
                if (def == null) continue;

                VALUES.put(key, parseValue(def, line.substring(split + 1).trim()));
            }
        } catch (IOException exception) {
            LOGGER.warn("[BAEffect] config is null：{}", exception.getMessage());
        }

        normalize();
        save();
    }

    public static void save() {
        revision++;

        StringBuilder builder = new StringBuilder(4096);

        for (Map.Entry<String, Object> entry : DEFAULTS.entrySet()) {
            builder.append(formatKey(entry.getKey()))
                    .append(" = ")
                    .append(formatValue(VALUES.getOrDefault(entry.getKey(), entry.getValue())))
                    .append('\n');
        }

        try {
            Path path = configPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, builder.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.warn("[BAEffect] save config is error：{}", exception.getMessage());
        }
    }

    private static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE);
    }

    private static String formatKey(String key) {
        return key.indexOf('.') < 0 && key.indexOf('-') < 0 ? key : '"' + key + '"';
    }

    private static String formatValue(Object value) {
        if (value instanceof Boolean bool) return bool.toString();
        if (value instanceof Number number) {
            double number2 = number.doubleValue();
            if (number2 == Math.rint(number2) && Math.abs(number2) < 1.0e15D) {
                return Long.toString((long) number2);
            }
            return String.format(Locale.ROOT, "%.6f", number2);
        }
        return '"' + String.valueOf(value) + '"';
    }

    private static Object parseValue(Object def, String raw) {
        if (def instanceof Boolean) return Boolean.parseBoolean(raw);
        if (def instanceof String) return unquote(raw);
        try {
            return Double.parseDouble(unquote(raw));
        } catch (NumberFormatException exception) {
            return def;
        }
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2
                && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'")))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static Object coerced(Object def, Object value) {
        if (def instanceof Boolean) {
            return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
        }
        if (def instanceof String) {
            return String.valueOf(value);
        }
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return def;
        }
    }

    private static void normalize() {
        clamp01("opacity");
        clamp01("trail.trailOpacity");
        clamp01("rings.bandToOuterRadius");
        clamp01("shards.roundness");
        clamp01("bloom.ringAlpha");
        clamp01("bloom.diskAlpha");
        clamp01("bloom.trailAlpha");
        clamp01("overlayAlphaLimit");

        clamp("scale", 0.01D, 5.0D);
        clamp("phiraScale", 1.0D, 5.0D);
        clamp("rotationSpeed", 0.0D, 1440.0D);
        clamp("maxDpr", 1.0D, 3.0D);
        clamp("clickTimeScale", 0.01D, 4.0D);
        clamp("trailTimeScale", 0.01D, 4.0D);
        clamp("rings.widthStart", 0.0D, 8.0D);
        clamp("rings.widthEnd", 0.0D, 8.0D);
        clamp("rings.hdrIntensity", 0.0D, 64.0D);
        clamp("shards.hdrIntensity", 0.0D, 64.0D);
        clamp("rings.angularVelocityMultiplier", 0.0D, 100.0D);
        clamp("rings.rotationDirection", -1.0D, 1.0D);
        clamp("rings.arcSamples", 3.0D, 1024.0D);
        clamp("rings.radialSamples", 1.0D, 32.0D);
        clamp("rings.dissolveDirection", -1.0D, 1.0D);
        clamp("trail.numCornerVertices", 0.0D, 64.0D);
        clamp("trail.numCapVertices", 0.0D, 64.0D);
        clamp("shards.maxCount", 0.0D, 10000.0D);

        orderPair("rings.radiusMin", "rings.radiusMax");
        orderPair("shards.clickSpeedMin", "shards.clickSpeedMax");
        orderPair("shards.clickLifetimeMinMs", "shards.clickLifetimeMaxMs");
        orderPair("shards.trailSpeedMin", "shards.trailSpeedMax");
        orderPair("shards.trailLifetimeMinMs", "shards.trailLifetimeMaxMs");
        orderPair("shards.sizeMin", "shards.sizeMax");

        if (!PRESETS.contains(text("preset"))) VALUES.put("preset", "custom");
        if (!COMPOSITING.contains(text("compositing"))) VALUES.put("compositing", "add");
        if (!MOTION.contains(text("motionMode"))) VALUES.put("motionMode", "system");
        if (!text("color").matches("^#[0-9a-fA-F]{6}$")) VALUES.put("color", "#4ca7ff");
    }

    private static void clamp(String key, double min, double max) {
        Object value = VALUES.get(key);
        if (!(value instanceof Number number)) return;
        VALUES.put(key, Math.max(min, Math.min(max, number.doubleValue())));
    }

    private static void clamp01(String key) {
        clamp(key, 0.0D, 1.0D);
    }

    private static void orderPair(String lowKey, String highKey) {
        double low = num(lowKey);
        double high = num(highKey);
        if (low <= high) return;
        VALUES.put(lowKey, high);
        VALUES.put(highKey, low);
    }

    private static Map<String, Object> createDefaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();

        // 开关与外观
        defaults.put("enabled", true);
        defaults.put("cursorEnabled", true);
        defaults.put("clickEnabled", true);
        defaults.put("fxStyle", "baeffect");
        defaults.put("phiraRespack", "");
        defaults.put("phiraTint", false);
        defaults.put("phiraScale", 2.0D);
        defaults.put("phiraSound", true);
        defaults.put("rotationEnabled", false);
        defaults.put("rotationSpeed", 180.0D);
        defaults.put("trailEnabled", true);
        defaults.put("trailAlways", false);
        defaults.put("motionMode", "system");
        defaults.put("preset", "classic");
        defaults.put("color", "#4ca7ff");
        defaults.put("opacity", 1.0D);
        defaults.put("scale", 1.0D);
        defaults.put("maxDpr", 2.0D);
        defaults.put("bloom", true);
        defaults.put("compositing", "add");
        defaults.put("overlayAlphaLimit", 250.0D / 255.0D);
        defaults.put("clickTimeScale", 1.0D);
        defaults.put("trailTimeScale", 1.0D);

        // hit / flare
        defaults.put("hit.enabled", false);
        defaults.put("hit.lifetimeMs", 80.0D);
        defaults.put("hit.radius", 24.0D);
        defaults.put("flare.enabled", false);
        defaults.put("flare.lifetimeMs", 150.0D);
        defaults.put("flare.radius", 36.0D);
        defaults.put("flare.rayCount", 6.0D);

        // 中心光盘
        defaults.put("disk.lifetimeMs", 200.0D);
        defaults.put("disk.radius", 64.8D);

        // 圆环
        defaults.put("rings.count", 2.0D);
        defaults.put("rings.lifetimeMs", 600.0D);
        defaults.put("rings.radiusMin", 68.923D);
        defaults.put("rings.radiusMax", 80.411D);
        defaults.put("rings.bandToOuterRadius", 0.059857D);
        defaults.put("rings.widthStart", 1.0D);
        defaults.put("rings.widthEnd", 1.0D);
        defaults.put("rings.angularVelocityMultiplier", 11.170107D);
        defaults.put("rings.rotationDirection", -1.0D);
        defaults.put("rings.hdrIntensity", 5.992157D);
        defaults.put("rings.arcSamples", 64.0D);
        defaults.put("rings.radialSamples", 1.0D);
        defaults.put("rings.dissolveDirection", 1.0D);

        // 三角碎片
        defaults.put("shards.hdrIntensity", 5.992157D);
        defaults.put("shards.clickCount", 4.0D);
        defaults.put("shards.clickLifetimeMinMs", 600.0D);
        defaults.put("shards.clickLifetimeMaxMs", 700.0D);
        defaults.put("shards.clickRadius", 49.877D);
        defaults.put("shards.clickSpeedMin", 49.877D);
        defaults.put("shards.clickSpeedMax", 66.503D);
        defaults.put("shards.roundness", 0.0D);
        defaults.put("shards.trailLifetimeMinMs", 200.0D);
        defaults.put("shards.trailLifetimeMaxMs", 400.0D);
        defaults.put("shards.trailRadius", 24.938D);
        defaults.put("shards.trailSpeedMin", 33.251D);
        defaults.put("shards.trailSpeedMax", 49.877D);
        defaults.put("shards.sizeMin", 16.626D);
        defaults.put("shards.sizeMax", 33.251D);
        defaults.put("shards.trailSpacing", 108.0D);
        defaults.put("shards.maxCount", 50.0D);

        // 轨迹
        defaults.put("trail.lifetimeMs", 300.0D);
        defaults.put("trail.width", 2.7D);
        defaults.put("trail.geometryWidth", 2.7D);
        defaults.put("trail.minVertexDistance", 5.4D);
        defaults.put("trail.outerGlowWidth", 9.0D);
        defaults.put("trail.trailOpacity", 1.0D);
        defaults.put("trail.numCornerVertices", 4.0D);
        defaults.put("trail.numCapVertices", 1.0D);

        // 原生辉光
        defaults.put("bloom.intensity", 1.7D);
        defaults.put("bloom.ringBlur", 80.0D);
        defaults.put("bloom.ringAlpha", 0.35D);
        defaults.put("bloom.diskBlur", 65.0D);
        defaults.put("bloom.diskAlpha", 0.65D);
        defaults.put("bloom.trailAlpha", 0.18D);

        return defaults;
    }

    public static final class ClientEvents {
        private ClientEvents() {
        }

        @SubscribeEvent
        public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
            if (event.getDispatcher().getRoot().getChild("baeffect") != null) {
                return;
            }

            event.getDispatcher().register(Commands.literal("baeffect")
                    .executes(context -> {
                        Minecraft.getInstance().execute(BAClickFxHtmlSettings::toggle);
                        return 1;
                    }));
        }

        @SubscribeEvent
        public static void onMouseButton(InputEvent.MouseButton.Pre event) {
            BAClickFxOverlay.handleMouseButton(event);
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            BAClickFxOverlay.handleClientTick(event);
        }

        @SubscribeEvent
        public static void onScreenRender(ScreenEvent.Render.Post event) {
            BAClickFxOverlay.renderFrame();
        }
    }

    static Document createOverlay(String path) {
        try {
            Document document = com.sighs.apricityui.ApricityUI.createDocument(path);
            if (document != null) return document;
            LOGGER.warn("ApricityUI.createDocument({}) 返回 null，退回 Document.create", path);
        } catch (Throwable throwable) {
            LOGGER.warn("ApricityUI.createDocument({}) 异常：{}", path, throwable);
        }
        return Document.create(path);
    }

    static List<Document> documents(String path) {
        List<Document> documents = Document.get(path);
        if (!documents.isEmpty()) return documents;

        try {
            ArrayList<?> raw = com.sighs.apricityui.ApricityUI.getDocument(path);
            List<Document> result = new ArrayList<>();
            for (Object value : raw) {
                if (value instanceof Document document) result.add(document);
            }
            return result;
        } catch (Throwable throwable) {
            return documents;
        }
    }

    public static Map<String, Object> parsePatch(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, Object> parsed = GSON.fromJson(json, MAP_TYPE);
            return parsed == null ? Map.of() : parsed;
        } catch (RuntimeException exception) {
            return Map.of();
        }
    }
}
