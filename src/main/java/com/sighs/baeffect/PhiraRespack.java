package com.sighs.baeffect;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


public final class PhiraRespack {
    //必要文件
    private static final List<String> REQUIRED_IMAGES = List.of(
            "hit_fx.png");

    private static final List<String> OPTIONAL_SOUNDS = List.of(
            "click.ogg", "drag.ogg", "flick.ogg", "ending.ogg");

    public final String id;
    public final String name;
    public final String author;
    public final String description;
    public int hitFxColumns;
    public int hitFxRows;
    public int hitFxWidth;
    public int hitFxHeight;
    public int frameWidth;
    public int frameHeight;
    public int frameCount;
    public boolean ready;
    public String framesKey = "";
    public final double hitFxDuration;
    public final double hitFxScale;
    public final boolean hitFxRotate;
    public final boolean hitFxTinted;
    public final boolean hideParticles;
    public final int[] holdAtlas;
    public final int[] holdAtlasMH;
    public final Map<String, byte[]> images;
    public final Map<String, byte[]> sounds;
    public final Path source;
    public Path extractedPath;
    public byte[] hitFxBytes() {
        return images.get("hit_fx.png");
    }
    public String fileName() {
        return source == null ? id : source.getFileName().toString();
    }

    private PhiraRespack(String id, String name, String author, String description,
                         int hitFxColumns, int hitFxRows, double hitFxDuration, double hitFxScale,
                         boolean hitFxRotate, boolean hitFxTinted, boolean hideParticles,
                         int[] holdAtlas, int[] holdAtlasMH,
                         Map<String, byte[]> images, Map<String, byte[]> sounds, Path source) {
        this.id = id;
        this.name = name;
        this.author = author;
        this.description = description;
        this.hitFxColumns = Math.max(1, hitFxColumns);
        this.hitFxRows = Math.max(1, hitFxRows);
        this.hitFxDuration = hitFxDuration <= 0.0D ? 0.5D : hitFxDuration;
        this.hitFxScale = hitFxScale <= 0.0D ? 1.0D : hitFxScale;
        this.hitFxRotate = hitFxRotate;
        this.hitFxTinted = hitFxTinted;
        this.hideParticles = hideParticles;
        this.holdAtlas = holdAtlas;
        this.holdAtlasMH = holdAtlasMH;
        this.images = images;
        this.sounds = sounds;
        this.source = source;
    }

    public int frameCount() {
        return hitFxColumns * hitFxRows;
    }

    public double frameDurationSeconds() {
        return hitFxDuration / Math.max(1, frameCount());
    }

    public static PhiraRespack parseZip(Path zipPath) throws IOException {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            InputStream infoStream = openEntry(zip, "info.yml");
            if (infoStream == null) throw new IOException("缺少 info.yml");
            Map<String, Object> info;
            try (InputStream stream = infoStream) {
                info = parseYaml(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }

            Map<String, byte[]> images = new LinkedHashMap<>();
            for (String file : REQUIRED_IMAGES) {
                ZipEntry entry = findEntry(zip, file);
                if (entry == null) throw new IOException("缺少必需文件：" + file);
                try (InputStream stream = zip.getInputStream(entry)) {
                    images.put(file, stream.readAllBytes());
                }
            }
            Map<String, byte[]> sounds = new LinkedHashMap<>();
            for (String file : OPTIONAL_SOUNDS) {
                ZipEntry entry = findEntry(zip, file);
                if (entry == null) continue;
                try (InputStream stream = zip.getInputStream(entry)) {
                    sounds.put(file, stream.readAllBytes());
                }
            }
            return build(zipPath.getFileName().toString(), info, images, sounds, zipPath);
        }
    }

    public static PhiraRespack parseDirectory(Path directory) throws IOException {
        Path info = directory.resolve("info.yml");
        if (!Files.isRegularFile(info)) throw new IOException("缺少 info.yml");
        Map<String, Object> parsed = parseYaml(Files.readString(info, StandardCharsets.UTF_8));

        Map<String, byte[]> images = new LinkedHashMap<>();
        for (String file : REQUIRED_IMAGES) {
            Path path = findFile(directory, file);
            if (path == null) throw new IOException("缺少必需文件：" + file);
            images.put(file, Files.readAllBytes(path));
        }
        Map<String, byte[]> sounds = new LinkedHashMap<>();
        for (String file : OPTIONAL_SOUNDS) {
            Path path = findFile(directory, file);
            if (path == null) continue;
            sounds.put(file, Files.readAllBytes(path));
        }
        return build(directory.getFileName().toString(), parsed, images, sounds, directory);
    }

    private static PhiraRespack build(String id, Map<String, Object> info,
                                      Map<String, byte[]> images, Map<String, byte[]> sounds,
                                      Path source) {
        int[] grid = toIntArray(info.get("hitFx"), new int[]{1, 1});
        int[] holdAtlas = toIntArray(info.get("holdAtlas"), new int[]{50, 50});
        int[] holdAtlasMH = toIntArray(info.get("holdAtlasMH"), holdAtlas);

        PhiraRespack pack = new PhiraRespack(
                sanitize(id),
                string(info, "name", id),
                string(info, "author", ""),
                string(info, "description", ""),
                grid[0], grid[1],
                number(info, "hitFxDuration", 0.5D),
                number(info, "hitFxScale", 1.0D),
                bool(info, "hitFxRotate", false),
                bool(info, "hitFxTinted", true),
                bool(info, "hideParticles", false),
                holdAtlas, holdAtlasMH,
                images, sounds, source);

        int[] size = readPngSize(images.get("hit_fx.png"));
        if (size != null) {
            pack.hitFxWidth = size[0];
            pack.hitFxHeight = size[1];
            pack.normalizeGrid();
        }
        return pack;
    }

    private void normalizeGrid() {
        if (hitFxWidth <= 0 || hitFxHeight <= 0) return;

        int columns = Math.max(1, Math.min(hitFxColumns, hitFxWidth));
        int rows = Math.max(1, Math.min(hitFxRows, hitFxHeight));

        if (hitFxWidth % columns != 0 || hitFxHeight % rows != 0) {
            int fixedColumns = nearestDivisor(hitFxWidth, columns);
            int fixedRows = nearestDivisor(hitFxHeight, rows);
            Baeffect.LOGGER.warn("资源包 {} 的 hitFx={}x{} 与图集 {}x{} 不整除，已校正为 {}x{}", name, hitFxColumns, hitFxRows, hitFxWidth, hitFxHeight, fixedColumns, fixedRows);
            if (fixedColumns > 0) {
                hitFxColumns = fixedColumns;
            }
            if (fixedRows > 0) {
                hitFxRows = fixedRows;
            }
        }
    }

    private static int nearestDivisor(int total, int target) {
        int best = -1;
        int bestGap = Integer.MAX_VALUE;
        for (int candidate = 1; candidate <= total; candidate++) {
            if (total % candidate != 0) continue;
            int gap = Math.abs(candidate - target);
            if (gap < bestGap) {
                bestGap = gap;
                best = candidate;
            }
        }
        return best;
    }

    private static int[] readPngSize(byte[] png) {
        if (png == null) return null;
        try (var input = new java.io.ByteArrayInputStream(png)) {
            var image = javax.imageio.ImageIO.read(input);
            return image == null ? null : new int[]{image.getWidth(), image.getHeight()};
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static ZipEntry findEntry(ZipFile zip, String name) {
        ZipEntry direct = zip.getEntry(name);
        if (direct != null) return direct;
        return zip.stream()
                .filter(entry -> !entry.isDirectory()
                        && entry.getName().toLowerCase(Locale.ROOT).endsWith("/" + name.toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElse(null);
    }

    private static InputStream openEntry(ZipFile zip, String name) throws IOException {
        ZipEntry entry = findEntry(zip, name);
        return entry == null ? null : zip.getInputStream(entry);
    }

    private static Path findFile(Path directory, String name) throws IOException {
        Path direct = directory.resolve(name);
        if (Files.isRegularFile(direct)) return direct;
        try (var stream = Files.walk(directory, 2)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static String sanitize(String raw) {
        String cleaned = raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}_\\-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return cleaned.isEmpty() ? "respack" : cleaned;
    }

    static Map<String, Object> parseYaml(String text) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (text == null) return map;
        for (String rawLine : text.split("\r?\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("---")) continue;
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            int comment = value.indexOf(" #");
            if (comment > 0 && !value.startsWith("\"")) value = value.substring(0, comment).trim();
            map.put(key, parseScalar(value));
        }
        return map;
    }

    private static Object parseScalar(String value) {
        if (value.startsWith("[") && value.endsWith("]")) {
            List<Object> list = new ArrayList<>();
            String inner = value.substring(1, value.length() - 1).trim();
            if (inner.isEmpty()) return list;
            for (String part : inner.split(",")) {
                String item = part.trim();
                if (!item.isEmpty()) list.add(parseScalar(item));
            }
            return list;
        }
        String unquoted = value;
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            unquoted = value.substring(1, value.length() - 1);
        }
        if (unquoted.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (unquoted.equalsIgnoreCase("false")) return Boolean.FALSE;
        try {
            return Double.parseDouble(unquoted);
        } catch (NumberFormatException ignored) {
            return unquoted;
        }
    }

    private static int[] toIntArray(Object value, int[] fallback) {
        if (!(value instanceof List<?> list) || list.size() < 2) return fallback;
        int[] result = new int[]{fallback[0], fallback[1]};
        for (int i = 0; i < 2; i++) {
            Object item = list.get(i);
            if (item instanceof Number number) result[i] = number.intValue();
        }
        return result;
    }

    private static String string(Map<String, Object> info, String key, String fallback) {
        Object value = info.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static double number(Map<String, Object> info, String key, double fallback) {
        Object value = info.get(key);
        return value instanceof Number num ? num.doubleValue() : fallback;
    }

    private static boolean bool(Map<String, Object> info, String key, boolean fallback) {
        Object value = info.get(key);
        if (value instanceof Boolean result) return result;
        if (value instanceof String text) return Boolean.parseBoolean(text);
        return fallback;
    }
}
