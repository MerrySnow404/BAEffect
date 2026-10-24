package com.sighs.baeffect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class PhiraHitFx {
    static final class Hit {
        final double x;
        final double y;
        final double ageMs;
        final double lifeMs;
        final double size;
        final int columns;
        final int rows;
        final int imageWidth;
        final int imageHeight;
        final String texture;
        final double rotation;

        Hit(double x, double y, double size, int columns, int rows,
            int imageWidth, int imageHeight, String texture,
            double lifeMs, double rotation) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.columns = columns;
            this.rows = rows;
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
            this.texture = texture;
            this.lifeMs = lifeMs;
            this.rotation = rotation;
            this.ageMs = 0.0D;
        }

        private Hit(Hit source, double ageMs) {
            this.x = source.x;
            this.y = source.y;
            this.size = source.size;
            this.columns = source.columns;
            this.rows = source.rows;
            this.imageWidth = source.imageWidth;
            this.imageHeight = source.imageHeight;
            this.texture = source.texture;
            this.lifeMs = source.lifeMs;
            this.rotation = source.rotation;
            this.ageMs = ageMs;
        }

        Hit advance(double deltaMs) {
            return new Hit(this, ageMs + deltaMs);
        }

        boolean expired() {
            return ageMs >= lifeMs;
        }

        int frameIndex() {
            int total = Math.max(1, columns * rows);
            double progress = lifeMs <= 0.0D ? 1.0D : ageMs / lifeMs;
            int index = (int) (Math.min(1.0D, Math.max(0.0D, progress)) * total);
            return Math.min(total - 1, Math.max(0, index));
        }
    }

    private static final int MAX_HITS = 24;

    private static final List<Hit> HITS = new ArrayList<>();
    private static final Map<String, Boolean> LOADED = new ConcurrentHashMap<>();

    private PhiraHitFx() {
    }

    static boolean hasEffects() {
        return !HITS.isEmpty();
    }

    static void clear() {
        HITS.clear();
    }

    static boolean isActive() {
        PhiraRespack pack = selected();
        return pack != null && pack.ready;
    }

    static PhiraRespack selected() {
        if (!"phira".equalsIgnoreCase(Baeffect.text("fxStyle"))) return null;
        String id = Baeffect.text("phiraRespack");
        if (id == null || id.isEmpty()) return null;
        return PhiraRespackManager.get(id);
    }

    static void spawn(double x, double y, double unit) {
        PhiraRespack pack = selected();
        if (pack == null) return;
        if (!pack.ready) {
            Baeffect.LOGGER.warn("资源包尚未就绪");
            return;
        }

        double guiMin = Math.min(
                net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight());
        double base = Math.max(12.0D, guiMin * 0.20D);
        double size = base * Baeffect.num("phiraScale");

        String texture = PhiraRespackManager.framesDir(pack);

        double lifeMs = Math.max(80.0D, pack.hitFxDuration * 1000.0D
                / Math.max(0.05D, Baeffect.num("clickTimeScale")));

        double direction = Baeffect.flag("rotationEnabled")
                ? (java.util.concurrent.ThreadLocalRandom.current().nextBoolean() ? 1.0D : -1.0D)
                : 0.0D;

        HITS.add(new Hit(x, y, size, pack.hitFxColumns, pack.hitFxRows,
                pack.frameWidth, pack.frameHeight, texture, lifeMs, direction));
        while (HITS.size() > MAX_HITS) {
            HITS.remove(0);
        }
        PhiraSound.playHit(pack);
    }

    static void update(double deltaMs) {
        if (HITS.isEmpty()) return;
        Iterator<Hit> iterator = HITS.iterator();
        List<Hit> survivors = new ArrayList<>(HITS.size());
        while (iterator.hasNext()) {
            Hit next = iterator.next().advance(deltaMs);
            if (!next.expired()) survivors.add(next);
        }
        HITS.clear();
        HITS.addAll(survivors);
    }

    static List<Hit> hits() {
        return HITS;
    }

    static String textureOf(Hit hit) {
        return hit.texture;
    }

    static void markLoaded(String path, boolean success) {
        LOADED.put(path, success);
    }

    static boolean isLoaded(String path) {
        return Boolean.TRUE.equals(LOADED.get(path));
    }
}
