package com.sighs.baeffect;

import com.mojang.blaze3d.platform.InputConstants;
import com.sighs.apricityui.canvas.CanvasRenderingContext2D;
import com.sighs.apricityui.client.Client;
import com.sighs.apricityui.element.Canvas;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.parser.HTML;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

final class BAClickFxOverlay {
    private static final String DOC_PATH = "baeffect/clickfx.html";
    private static final String CANVAS_ID = "baeffect-fx-canvas";
    private static final String PHIRA_LAYER_ID = "baeffect-phira-layer";
    private static final String DOC_TEMPLATE = """
            <meta name="aui-font-mode" content="web">
            <meta name="aui-viewport" content="mode=gui">
            <body style="position:absolute;left:0;top:0;width:100%;height:100%;pointer-events:none;">
              <canvas id="baeffect-fx-canvas" style="position:absolute;left:0;top:0;width:100%;height:100%;pointer-events:none;"></canvas>
              <div id="baeffect-phira-layer" style="position:absolute;left:0;top:0;width:100%;height:100%;pointer-events:none;"></div>
            </body>
            """;

    private static final double TAU = Math.PI * 2.0D;
    private static final double MAX_FRAME_MS = 100.0D;
    private static final int MAX_TRAIL_POINTS = 256;
    private static final int[] WHITE = {255, 255, 255};
    private static final StringBuilder RGBA = new StringBuilder(48);

    private static final double[][] RING_RADIUS_KEYS = {
            {0.007209778D, 0.42050898D, 2.4004734D, 2.4004734D},
            {0.21392822D, 0.7159773D, 0.9115745D, 0.9115745D},
            {1.0D, 1.0D, 0.0D, 0.0D}};
    private static final double[][] RING_DISSOLVE_KEYS = {
            {0.0D, 1.0D, 0.0D, 0.0D},
            {0.2D, 0.0D, 0.0D, 2.4249368D},
            {1.0D, 1.0D, 0.27735636D, 0.27735636D}};
    private static final double[][] RING_ANGULAR_MIN_KEYS = {{0.14903903D, 1.0D}, {1.0D, 0.45561826D}};
    private static final double[][] RING_ANGULAR_MAX_KEYS = {{0.15865384D, 0.79881656D}, {1.0D, -0.06509134D}};
    private static final double[][] RING_THICKNESS_KEYS = {{0.0072D, 2.4005D}, {0.2139D, 0.9116D}, {1.0D, 0.0D}};
    private static final double[][] DISK_SCALE_KEYS = {{0.0D, 0.3258D}, {0.2139D, 0.7160D}, {1.0D, 1.0D}};
    private static final double[][] DISK_ALPHA_KEYS = {{0.0D, 1.0D}, {0.1088D, 1.0D}, {1.0D, 0.0D}};
    private static final double[][] HIT_ALPHA_KEYS = {{0.0D, 1.0D}, {0.4D, 0.8D}, {1.0D, 0.0D}};
    private static final double[][] FLARE_ALPHA_KEYS = {{0.0D, 0.7D}, {0.5D, 0.3D}, {1.0D, 0.0D}};
    private static final double[][] SHARD_ALPHA_KEYS = {
            {0.0D, 1.0D}, {0.288D, 1.0D}, {0.365D, 0.0D}, {0.471D, 1.0D}, {0.574D, 0.0D},
            {0.668D, 1.0D}, {0.756D, 0.0D}, {0.853D, 1.0D}, {1.0D, 1.0D}};
    private static final double[][] SHARD_SCALE_KEYS = {{0.0D, 0.0D}, {0.1545D, 1.0D}, {1.0D, 0.0D}};

    private static final List<Ring> RINGS = new ArrayList<>();
    private static final List<Shard> SHARDS = new ArrayList<>();
    private static final List<Disk> DISKS = new ArrayList<>();
    private static final List<Flash> FLASHES = new ArrayList<>();
    private static final List<TrailPoint> TRAIL = new ArrayList<>();

    private static boolean mouseDown;
    private static boolean hasTrailAnchor;
    private static double trailAnchorX;
    private static double trailAnchorY;
    private static double trailSpacingAccumulator;
    private static long lastFrameNanos;
    private static boolean canvasDirty;

    private static String themeCacheKey = "";
    private static int[] themeCache = {76, 167, 255};
    private static Canvas layoutCanvas;
    private static int layoutWidth = -1;
    private static int layoutHeight = -1;
    private static int missingBodyTicks;
    private static double layoutScale = -1.0D;

    private BAClickFxOverlay() {
    }

    /** Reads the current GUI position and converts it into the overlay document's CSS space. */
    private static Position mousePosition(Document document) {
        Minecraft minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        long handle = window.getWindow();
        if (handle == 0L || document == null) return null;

        double[] cursorX = {0.0D};
        double[] cursorY = {0.0D};
        GLFW.glfwGetCursorPos(handle, cursorX, cursorY);
        int screenWidth = window.getScreenWidth();
        int screenHeight = window.getScreenHeight();
        int guiWidth = window.getGuiScaledWidth();
        int guiHeight = window.getGuiScaledHeight();
        if (screenWidth <= 0 || screenHeight <= 0 || guiWidth <= 0 || guiHeight <= 0) return null;

        Position guiPosition = new Position(
                cursorX[0] * guiWidth / screenWidth,
                cursorY[0] * guiHeight / screenHeight);
        return document.guiToDocumentPosition(guiPosition);
    }

    static void handleMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != 0) return;
        if (Minecraft.getInstance().screen == null) return;
        if (!Baeffect.flag("enabled")) return;

        Position position = mousePosition(ensureOverlayLoaded());
        if (event.getAction() == InputConstants.PRESS) {
            mouseDown = true;
            hasTrailAnchor = false;
            if (position != null && Baeffect.flag("clickEnabled")) {
                if (PhiraHitFx.isActive()) {
                    PhiraHitFx.spawn(position.x, position.y, Baeffect.referenceUnit());
                } else {
                    boom(position.x, position.y);
                }
            }
            return;
        }
        if (event.getAction() == InputConstants.RELEASE) {
            mouseDown = false;
            hasTrailAnchor = false;
        }
    }

    static void handleClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        if (Minecraft.getInstance().screen == null) {
            mouseDown = false;
            hasTrailAnchor = false;
        }

        Document document = ensureOverlayLoaded();
        Canvas canvas = resolveCanvas(document);
        if (canvas == null) return;
        updateLayout(document, canvas);

        if (Minecraft.getInstance().screen == null && (hasEffects() || canvasDirty)) {
            renderFrame();
        }
    }

    private static boolean hasEffects() {
        return !RINGS.isEmpty() || !SHARDS.isEmpty() || !DISKS.isEmpty()
                || !FLASHES.isEmpty() || PhiraHitFx.hasEffects() || TRAIL.size() >= 2;
    }

    static void renderFrame() {
        if (Minecraft.getInstance().screen == null && !hasEffects() && !canvasDirty) return;

        try {
            Document document = ensureOverlayLoaded();
            Canvas canvas = resolveCanvas(document);
            if (canvas == null) return;
            updateLayout(document, canvas);

            long now = System.nanoTime();
            double deltaMs = lastFrameNanos == 0L ? 0.0D : Math.min((now - lastFrameNanos) / 1_000_000.0D, MAX_FRAME_MS);
            lastFrameNanos = now;

            updateState(deltaMs);
            syncPhiraLayer(document);
            render(canvas);
        } catch (Throwable throwable) {
            Baeffect.LOGGER.error("特效渲染异常，已清空当前特效：{}", String.valueOf(throwable));
            RINGS.clear();
            SHARDS.clear();
            DISKS.clear();
            FLASHES.clear();
            TRAIL.clear();
            lastFrameNanos = 0L;
        }
    }

    /** 环的视口缩放：以上游 1080 高度为基准，保留分辨率自适应。 */
    private static double ringScale() {
        double height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        double scale = height / 1080.0D * Math.max(0.01D, Baeffect.num("scale"));
        return Math.max(0.35D, Math.min(3.0D, scale));
    }

    static void boom(double x, double y) {
        double unit = Baeffect.referenceUnit();

        DISKS.add(new Disk(x, y));

        int ringCount = (int) Math.round(Math.max(0.0D, Baeffect.num("rings.count")));
        for (int index = 0; index < ringCount; index++) {
            RINGS.add(new Ring(x, y, index, ringCount, unit));
        }

        spawnShards(x, y, unit);

        if (Baeffect.flag("hit.enabled")) {
            FLASHES.add(Flash.hit(x, y, unit));
        }
        if (Baeffect.flag("flare.enabled")) {
            FLASHES.add(Flash.flare(x, y, unit));
        }
    }

    private static void spawnShards(double x, double y, double unit) {
        int count = (int) Math.round(Math.max(0.0D, Baeffect.num("shards.clickCount")));
        for (int index = 0; index < count; index++) {
            double angle = randomAngle();
            double radius = Baeffect.num("shards.clickRadius") * unit;
            double speed = lerp(Baeffect.num("shards.clickSpeedMin"), Baeffect.num("shards.clickSpeedMax"), random()) * unit;

            Shard shard = new Shard();
            shard.x = x + Math.cos(angle) * radius;
            shard.y = y + Math.sin(angle) * radius;
            shard.vx = Math.cos(angle) * speed;
            shard.vy = Math.sin(angle) * speed;
            shard.rotation = randomAngle();
            if (Baeffect.flag("rotationEnabled")) {
                double degPerSecond = Baeffect.num("rotationSpeed");
                shard.spin = Math.toRadians(degPerSecond) * (random() < 0.5D ? -1.0D : 1.0D);
            } else {
                shard.spin = (random() < 0.5D ? -1.0D : 1.0D) * 2.1621501D * (0.75D + random() * 0.5D);
            }
            shard.size = lerp(Baeffect.num("shards.sizeMin"), Baeffect.num("shards.sizeMax"), random()) * unit;
            shard.life = Math.max(50.0D, lerp(
                    Baeffect.num("shards.clickLifetimeMinMs"),
                    Baeffect.num("shards.clickLifetimeMaxMs"), random()));
            shard.timeScale = Baeffect.num("clickTimeScale");
            SHARDS.add(shard);
        }
        capShards();
    }

    private static void spawnTrailShard(double x, double y, double unit) {
        double angle = randomAngle();
        double radius = Baeffect.num("shards.trailRadius") * unit;
        double speed = lerp(
                Baeffect.num("shards.trailSpeedMin"),
                Baeffect.num("shards.trailSpeedMax"), random()) * unit;

        Shard shard = new Shard();
        shard.x = x + Math.cos(angle) * radius;
        shard.y = y + Math.sin(angle) * radius;
        shard.vx = Math.cos(angle) * speed;
        shard.vy = Math.sin(angle) * speed;
        shard.rotation = randomAngle();
        shard.spin = (random() < 0.5D ? -1.0D : 1.0D) * 2.1621501D;
        shard.size = lerp(Baeffect.num("shards.sizeMin"), Baeffect.num("shards.sizeMax"), random()) * unit * 0.7D;
        shard.life = Math.max(50.0D, lerp(
                Baeffect.num("shards.trailLifetimeMinMs"),
                Baeffect.num("shards.trailLifetimeMaxMs"), random()));
        shard.timeScale = Baeffect.num("trailTimeScale");
        SHARDS.add(shard);
        capShards();
    }

    private static void capShards() {
        int maxCount = (int) Math.round(Baeffect.num("shards.maxCount"));
        if (maxCount <= 0) {
            SHARDS.clear();
            return;
        }
        while (SHARDS.size() > maxCount) {
            SHARDS.remove(0);
        }
    }

    private static Document ensureOverlayLoaded() {
        List<Document> documents = Baeffect.documents(DOC_PATH);
        if (!documents.isEmpty()) {
            Document document = documents.get(0);
            document.setReloadPersistent(true);
            return document;
        }

        Document document = Baeffect.createOverlay(DOC_PATH);
        if (document == null || document.body == null) {
            if (document != null) document.remove();
            HTML.putTemple(DOC_PATH, DOC_TEMPLATE);
            document = Baeffect.createOverlay(DOC_PATH);
        }
        if (document == null) {
            Baeffect.LOGGER.error("特效画布创建失败：" + DOC_PATH);
        } else {
            document.setReloadPersistent(true);
        }
        return document;
    }

    private static Canvas resolveCanvas(Document document) {
        if (document == null) return null;
        return document.getElementById(CANVAS_ID) instanceof Canvas canvas ? canvas : null;
    }

    private static void updateLayout(Document document, Canvas canvas) {
        if (document == null || document.body == null) {
            if (++missingBodyTicks % 100 == 0) {
                Baeffect.LOGGER.error("特效画布缺少 body，无法定位尺寸（document={}）", document != null);
            }
            return;
        }
        missingBodyTicks = 0;

        var viewportSize = document.getViewportSize();
        int logicalWidth = Math.max(1, (int) Math.round(viewportSize.width()));
        int logicalHeight = Math.max(1, (int) Math.round(viewportSize.height()));

        double guiScale = Math.max(0.5D, Minecraft.getInstance().getWindow().getGuiScale());
        double renderScale = Math.max(0.5D, Math.min(guiScale, Math.max(1.0D, Baeffect.num("maxDpr"))));

        if (canvas == layoutCanvas
                && logicalWidth == layoutWidth
                && logicalHeight == layoutHeight
                && Math.abs(renderScale - layoutScale) < 1.0e-4D) {
            return;
        }

        layoutCanvas = canvas;
        layoutWidth = logicalWidth;
        layoutHeight = logicalHeight;
        layoutScale = renderScale;

        int pixelWidth = Math.max(1, (int) Math.round(logicalWidth * renderScale));
        int pixelHeight = Math.max(1, (int) Math.round(logicalHeight * renderScale));

        document.body.setAttribute("style",
                "position:absolute;left:0;top:0;width:" + logicalWidth + "px;height:" + logicalHeight
                        + "px;pointer-events:none;");
        canvas.setAttribute("style",
                "position:absolute;left:0;top:0;width:" + logicalWidth + "px;height:" + logicalHeight
                        + "px;pointer-events:none;");

        Element phiraLayer = document.getElementById(PHIRA_LAYER_ID);
        if (phiraLayer != null) {
            phiraLayer.setAttribute("style",
                    "position:absolute;left:0;top:0;width:" + logicalWidth + "px;height:" + logicalHeight
                            + "px;pointer-events:none;overflow:visible;");
        }

        if (canvas.getWidth() != pixelWidth) {
            canvas.setWidth(pixelWidth);
        }
        if (canvas.getHeight() != pixelHeight) {
            canvas.setHeight(pixelHeight);
        }
        CanvasRenderingContext2D context = canvas.getContext("2d");
        if (context != null) {
            context.setTransform(renderScale, 0, 0, renderScale, 0, 0);
        }

        // 画布尺寸更新后同步该文档的 viewport，避免窗口最大化后鼠标映射仍使用旧尺寸。
        document.applyViewport(true);
        com.sighs.apricityui.init.Window.window.fireResizeEvent();
    }

    private static void updateState(double deltaMs) {
        double frameMs = deltaMs <= 0.0D ? 16.6D : deltaMs;
        double unit = Baeffect.referenceUnit();
        double clickScale = Baeffect.num("clickTimeScale");
        double trailScale = Baeffect.num("trailTimeScale");

        if (!Baeffect.flag("enabled")) {
            RINGS.clear();
            SHARDS.clear();
            DISKS.clear();
            FLASHES.clear();
            TRAIL.clear();
            PhiraHitFx.clear();
            return;
        }

        sampleTrail(frameMs * trailScale, unit, trailScale);

        double trailLife = Math.max(50.0D, Baeffect.num("trail.lifetimeMs"));
        for (Iterator<TrailPoint> iterator = TRAIL.iterator(); iterator.hasNext(); ) {
            TrailPoint point = iterator.next();
            point.ageMs += frameMs * trailScale;
            if (point.ageMs >= trailLife) {
                iterator.remove();
            }
        }

        if (PhiraHitFx.isActive()) {
            RINGS.clear();
            SHARDS.clear();
            DISKS.clear();
            FLASHES.clear();
            PhiraHitFx.update(frameMs * clickScale);
            return;
        }
        PhiraHitFx.clear();

        double clickDelta = frameMs * clickScale;
        for (Iterator<Ring> iterator = RINGS.iterator(); iterator.hasNext(); ) {
            Ring ring = iterator.next();
            ring.advance(clickDelta);
            if (ring.age >= ring.life) {
                iterator.remove();
            }
        }

        for (Iterator<Disk> iterator = DISKS.iterator(); iterator.hasNext(); ) {
            Disk disk = iterator.next();
            disk.age += clickDelta;
            if (disk.age >= disk.life) {
                iterator.remove();
            }
        }

        for (Iterator<Flash> iterator = FLASHES.iterator(); iterator.hasNext(); ) {
            Flash flash = iterator.next();
            flash.age += clickDelta;
            if (flash.age >= flash.life) {
                iterator.remove();
            }
        }

        for (Iterator<Shard> iterator = SHARDS.iterator(); iterator.hasNext(); ) {
            Shard shard = iterator.next();
            double shardDelta = frameMs * shard.timeScale;
            shard.age += shardDelta;
            shard.x += shard.vx * shardDelta / 1000.0D;
            shard.y += shard.vy * shardDelta / 1000.0D;
            shard.rotation += shard.spin * shardDelta / 1000.0D;
            if (shard.age >= shard.life) {
                iterator.remove();
            }
        }
    }

    private static void sampleTrail(double trailDeltaMs, double unit, double trailScale) {
        if (!Baeffect.flag("trailEnabled")) {
            TRAIL.clear();
            hasTrailAnchor = false;
            return;
        }

        boolean always = Baeffect.flag("trailAlways") && !"reduced".equals(Baeffect.text("motionMode"));
        if (!mouseDown && !always) {
            hasTrailAnchor = false;
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) return;

        Position position = mousePosition(ensureOverlayLoaded());
        if (position == null) return;

        if (!hasTrailAnchor) {
            trailAnchorX = position.x;
            trailAnchorY = position.y;
            hasTrailAnchor = true;
            if (always) {
                TrailPoint point = new TrailPoint();
                point.x = position.x - 0.5D;
                point.y = position.y;
                point.ageMs = 0.0D;
                TRAIL.add(point);
            }
            return;
        }

        double deltaX = position.x - trailAnchorX;
        double deltaY = position.y - trailAnchorY;
        double distance = Math.hypot(deltaX, deltaY);
        double minStep = Math.max(0.5D, Baeffect.num("trail.minVertexDistance") * unit);
        if (distance < minStep) return;

        int steps = Math.max(1, Math.min(512, (int) Math.ceil(distance / minStep)));
        double spacing = Math.max(1.0D, Baeffect.num("shards.trailSpacing") * unit);
        double stepDistance = distance / steps;
        for (int step = 1; step <= steps; step++) {
            double fraction = (double) step / steps;
            double sampleX = trailAnchorX + deltaX * fraction;
            double sampleY = trailAnchorY + deltaY * fraction;

            TrailPoint point = new TrailPoint();
            point.x = sampleX;
            point.y = sampleY;
            point.ageMs = 0.0D;
            TRAIL.add(point);
            while (TRAIL.size() > MAX_TRAIL_POINTS) {
                TRAIL.remove(0);
            }

            trailSpacingAccumulator += stepDistance;
            while (trailSpacingAccumulator >= spacing) {
                trailSpacingAccumulator -= spacing;
                spawnTrailShard(sampleX, sampleY, unit);
            }
        }

        trailAnchorX = position.x;
        trailAnchorY = position.y;
        if (trailDeltaMs <= 0.0D || trailScale <= 0.0D) {
            trailSpacingAccumulator = Math.min(trailSpacingAccumulator, spacing * 4.0D);
        }
    }

    //绘制
    private static void render(Canvas canvas) {
        CanvasRenderingContext2D context = canvas.getContext("2d");
        if (context == null) return;

        boolean active = !RINGS.isEmpty() || !SHARDS.isEmpty() || !DISKS.isEmpty()
                || !FLASHES.isEmpty() || TRAIL.size() >= 2;

        if (!active && !canvasDirty) return;

        context.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        canvasDirty = active;
        if (!active) {
            lastFrameNanos = 0L;
            return;
        }

        boolean additive = !"normal".equals(Baeffect.text("compositing"));
        context.setGlobalCompositeOperation(additive ? "lighter" : "source-over");

        double unit = Baeffect.referenceUnit();
        renderTrail(context, unit);
        renderDisks(context, unit);
        renderRings(context, unit);
        renderFlashes(context, unit);
        renderShards(context, unit);

        context.setGlobalCompositeOperation("source-over");
    }

    private static void syncPhiraLayer(Document document) {
        if (document == null) return;
        Element layer = document.getElementById(PHIRA_LAYER_ID);
        if (layer == null) {
            if (System.nanoTime() - phiraLayerLogged > 5_000_000_000L) {
                phiraLayerLogged = System.nanoTime();
            }
            return;
        }

        List<PhiraHitFx.Hit> hits = PhiraHitFx.hits();
        while (layer.children.size() > hits.size()) {
            layer.children.get(layer.children.size() - 1).remove();
        }
        while (layer.children.size() < hits.size()) {
            Element node = document.createElement("DIV");
            node.setAttribute("style", "position:absolute;pointer-events:none;"
                    + "background-repeat:no-repeat;");
            layer.append(node);
        }

        for (int index = 0; index < hits.size(); index++) {
            PhiraHitFx.Hit hit = hits.get(index);
            Element node = layer.children.get(index);
            if (node == null) continue;

            int frame = hit.frameIndex();

            double width = hit.size;
            double height = hit.imageWidth > 0 && hit.imageHeight > 0
                    ? hit.size * hit.imageHeight / hit.imageWidth
                    : hit.size;

            double progress = hit.lifeMs <= 0.0D ? 1.0D : Math.min(1.0D, hit.ageMs / hit.lifeMs);
            double alpha = progress > 0.75D ? Math.max(0.0D, (1.0D - progress) / 0.25D) : 1.0D;

            double angle = hit.rotation == 0.0D ? 0.0D
                    : hit.rotation * Baeffect.num("rotationSpeed") * hit.ageMs / 1000.0D;
            String style = "position:absolute;pointer-events:none;"
                    + "left:" + (hit.x - width / 2.0D) + "px;"
                    + "top:" + (hit.y - height / 2.0D) + "px;"
                    + "width:" + width + "px;height:" + height + "px;"
                    + "opacity:" + alpha + ";"
                    + (angle == 0.0D ? "" : "transform:rotate(" + angle + "deg);")
                    + "background-image:url(" + hit.texture + "/frame_" + frame + ".png);"
                    + "background-repeat:no-repeat;"
                    + "background-size:100% 100%;";
            node.setAttribute("style", style);
        }
    }

    private static long phiraLayerLogged;

    private static void renderRings(CanvasRenderingContext2D context, double unit) {
        double intensity = Math.min(8.0D, Baeffect.num("rings.hdrIntensity")) / 8.0D;
        double band = Baeffect.num("rings.bandToOuterRadius");
        double widthStart = Baeffect.num("rings.widthStart");
        double widthEnd = Baeffect.num("rings.widthEnd");
        double arcSamples = Math.max(3.0D, Math.min(1024.0D, Baeffect.num("rings.arcSamples")));
        int direction = Baeffect.num("rings.dissolveDirection") >= 0.0D ? 1 : -1;

        for (Ring ring : RINGS) {
            double progress = clamp01(ring.age / ring.life);

            double outer = ring.baseRadius * hermite(RING_RADIUS_KEYS, progress);
            double width = outer * band * lerp(widthStart, widthEnd, progress);
            if (width <= 0.001D) continue;
            double radius = outer - width * 0.5D;
            if (radius <= 0.5D) continue;

            double threshold = clamp01(hermite(RING_DISSOLVE_KEYS, progress));
            double alpha = 0.45D + intensity * 0.9D;

            double circumference = TAU * radius;
            int samples = (int) Math.max(32.0D, Math.max(Math.ceil(circumference), arcSamples));

            context.save();
            context.translate(ring.x, ring.y);
            context.rotate(ring.angle);

            for (int index = 0; index < samples; index++) {
                double start = (double) index / samples;
                double end = (double) (index + 1) / samples;
                double center = (start + end) * 0.5D;
                double position = direction >= 0 ? center : 1.0D - center;
                if (position < threshold) continue;

                context.beginPath();
                context.arc(0.0D, 0.0D, radius, start * TAU, end * TAU, false);
                context.setLineWidth(Math.max(0.5D, width));
                context.setStrokeStyle(rgba(themeColor(), alpha));
                context.stroke();
            }
            context.restore();
        }
    }

    private static double smooth(double[][] keys, double t) {
        if (keys == null || keys.length == 0) return 0.0D;
        double clamped = clamp01(t);
        if (clamped <= keys[0][0]) return keys[0][1];
        for (int index = 1; index < keys.length; index++) {
            double[] previous = keys[index - 1];
            double[] current = keys[index];
            if (clamped > current[0]) continue;
            double span = current[0] - previous[0];
            double local = span > 0.0D ? (clamped - previous[0]) / span : 1.0D;
            double eased = local * local * (3.0D - 2.0D * local);
            return lerp(previous[1], current[1], eased);
        }
        return keys[keys.length - 1][1];
    }

    private static double hermite(double[][] keys, double t) {
        if (keys == null || keys.length == 0) return 0.0D;
        double clamped = clamp01(t);
        if (clamped <= keys[0][0]) return keys[0][1];
        for (int index = 1; index < keys.length; index++) {
            double[] previous = keys[index - 1];
            double[] current = keys[index];
            if (clamped > current[0]) continue;

            double span = current[0] - previous[0];
            double local = span > 0.0D ? (clamped - previous[0]) / span : 1.0D;
            double squared = local * local;
            double cubed = squared * local;
            double outTangent = previous.length > 3 ? previous[3] : 0.0D;
            double inTangent = current.length > 2 ? current[2] : 0.0D;
            double h00 = 2.0D * cubed - 3.0D * squared + 1.0D;
            double h10 = cubed - 2.0D * squared + local;
            double h01 = -2.0D * cubed + 3.0D * squared;
            double h11 = cubed - squared;
            return h00 * previous[1] + h10 * outTangent * span
                    + h01 * current[1] + h11 * inTangent * span;
        }
        return keys[keys.length - 1][1];
    }

    private static void pathArc(CanvasRenderingContext2D context, double centerX, double centerY,
                                double radius, double start, double sweep, int samples) {
        context.beginPath();
        if (samples >= 32) {
            context.arc(centerX, centerY, radius, start, start + sweep, false);
            return;
        }

        int steps = Math.max(3, samples);
        for (int index = 0; index <= steps; index++) {
            double angle = start + sweep * index / steps;
            double pointX = centerX + Math.cos(angle) * radius;
            double pointY = centerY + Math.sin(angle) * radius;
            if (index == 0) {
                context.moveTo(pointX, pointY);
            } else {
                context.lineTo(pointX, pointY);
            }
        }
    }

    private static void renderDisks(CanvasRenderingContext2D context, double unit) {
        boolean bloom = Baeffect.flag("bloom");

        for (Disk disk : DISKS) {
            double progress = clamp01(disk.age / disk.life);
            double alpha = key(progress, DISK_ALPHA_KEYS);
            double radius = Baeffect.num("disk.radius") * unit * key(progress, DISK_SCALE_KEYS);
            if (radius <= 0.5D || alpha <= 0.01D) continue;

            if (bloom) {
                context.beginPath();
                context.arc(disk.x, disk.y, radius * 1.9D, 0.0D, TAU, false);
                context.setFillStyle(rgba(themeColor(),
                        alpha * 0.10D * bloomGain() * Baeffect.num("bloom.diskAlpha") * 1.6D));
                context.fill();

                context.beginPath();
                context.arc(disk.x, disk.y, radius * 1.35D, 0.0D, TAU, false);
                context.setFillStyle(rgba(themeColor(),
                        alpha * 0.18D * bloomGain() * Baeffect.num("bloom.diskAlpha") * 1.6D));
                context.fill();
            }

            context.beginPath();
            context.arc(disk.x, disk.y, radius, 0.0D, TAU, false);
            context.setFillStyle(rgba(themeColor(), alpha * 0.4D));
            context.fill();

            context.beginPath();
            context.arc(disk.x, disk.y, Math.max(0.5D, radius * 0.9D), 0.0D, TAU, false);
            context.setFillStyle(rgba(WHITE, alpha * 0.92D));
            context.fill();
        }
    }

    private static void renderFlashes(CanvasRenderingContext2D context, double unit) {
        for (Flash flash : FLASHES) {
            double progress = clamp01(flash.age / flash.life);
            double alpha = key(progress, flash.rayCount > 0 ? FLARE_ALPHA_KEYS : HIT_ALPHA_KEYS);
            if (alpha <= 0.01D) continue;

            if (flash.rayCount <= 0) {
                context.beginPath();
                context.arc(flash.x, flash.y, Math.max(0.5D, flash.radius), 0.0D, TAU, false);
                context.setFillStyle(rgba(themeColor(), alpha * 0.5D));
                context.fill();
                context.beginPath();
                context.arc(flash.x, flash.y, Math.max(0.5D, flash.radius * 0.62D), 0.0D, TAU, false);
                context.setFillStyle(rgba(WHITE, alpha));
                context.fill();
                continue;
            }

            double length = flash.radius * (0.55D + 0.45D * progress);
            context.setLineWidth(Math.max(0.8D, 1.6D * unit * 2.0D));
            context.setStrokeStyle(rgba(themeColor(), alpha));
            for (double angle : flash.rays) {
                context.beginPath();
                context.moveTo(flash.x, flash.y);
                context.lineTo(flash.x + Math.cos(angle) * length, flash.y + Math.sin(angle) * length);
                context.stroke();
            }
        }
    }

    private static void renderShards(CanvasRenderingContext2D context, double unit) {
        double intensity = Math.min(8.0D, Baeffect.num("shards.hdrIntensity")) / 8.0D;

        for (Shard shard : SHARDS) {
            double progress = clamp01(shard.age / shard.life);
            double alpha = key(progress, SHARD_ALPHA_KEYS) * (0.55D + intensity * 0.75D);
            double scale = key(progress, SHARD_SCALE_KEYS);
            if (alpha <= 0.01D || scale <= 0.001D) continue;

            double size = shard.size * scale;
            if (size <= 0.3D) continue;
            int[] color = progress < 0.24D ? WHITE : themeColor();

            context.save();
            context.translate(shard.x, shard.y);
            context.rotate(shard.rotation);
            shardPath(context, size, Baeffect.num("shards.roundness"));
            context.setFillStyle(rgba(color, alpha));
            context.fill();
            context.restore();
        }
    }

    private static void shardPath(CanvasRenderingContext2D context, double size, double roundness) {
        double radius = clamp01(roundness);
        if (radius >= 0.99D) {
            context.beginPath();
            context.arc(0.0D, 0.0D, size * 0.72D, 0.0D, TAU, false);
            return;
        }

        double[] xs = {0.0D, size * 0.62D, -size * 0.62D};
        double[] ys = {-size, size * 0.62D, size * 0.62D};
        context.beginPath();

        if (radius <= 0.01D) {
            context.moveTo(xs[0], ys[0]);
            context.lineTo(xs[1], ys[1]);
            context.lineTo(xs[2], ys[2]);
            context.closePath();
            return;
        }

        double cut = radius * 0.5D;
        for (int index = 0; index < 3; index++) {
            int previous = (index + 2) % 3;
            int next = (index + 1) % 3;
            double inX = xs[index] + (xs[previous] - xs[index]) * cut;
            double inY = ys[index] + (ys[previous] - ys[index]) * cut;
            double outX = xs[index] + (xs[next] - xs[index]) * cut;
            double outY = ys[index] + (ys[next] - ys[index]) * cut;

            if (index == 0) {
                context.moveTo(inX, inY);
            } else {
                context.lineTo(inX, inY);
            }
            context.quadraticCurveTo(xs[index], ys[index], outX, outY);
        }
        context.closePath();
    }

    private static void renderTrail(CanvasRenderingContext2D context, double unit) {
        if (TRAIL.size() < 2) return;

        double lifetime = Math.max(50.0D, Baeffect.num("trail.lifetimeMs"));
        double opacity = Math.max(0.05D, Baeffect.num("trail.trailOpacity"));
        double scale = ringScale();
        double width = Math.max(1.5D, Baeffect.num("trail.width") * scale);
        double glowWidth = Math.max(3.0D, Baeffect.num("trailGlowWidth") * scale);
        double glowOpacity = Math.max(0.0D, Math.min(1.0D, Baeffect.num("trailGlowOpacity")));
        double emission = Math.max(0.0D, Math.min(1.0D, Baeffect.num("bloom.trailAlpha")));
        String glowMode = Baeffect.text("trailGlowMode");

        double glowStrength = glowOpacity * (0.35D + emission * 1.3D);
        boolean glowOn = !"off".equals(glowMode) && glowStrength > 0.004D;

        if (glowOn) {
            if ("fine".equals(glowMode)) {
                renderTrailGlowFine(context, lifetime, opacity, width, glowWidth, glowStrength);
            } else {
                renderTrailGlowMedium(context, lifetime, opacity, width, glowWidth, glowStrength);
            }
        }

        for (int index = 1; index < TRAIL.size(); index++) {
            TrailPoint previous = TRAIL.get(index - 1);
            TrailPoint current = TRAIL.get(index);
            double alpha = segmentAlpha(previous, current, lifetime) * opacity;
            if (alpha <= 0.004D) continue;
            context.beginPath();
            context.moveTo(previous.x, previous.y);
            context.lineTo(current.x, current.y);
            context.setLineWidth(width);
            context.setLineCap("round");
            context.setLineJoin("round");
            context.setStrokeStyle(rgba(themeColor(), alpha));
            context.stroke();
        }
    }

    private static void renderTrailGlowMedium(CanvasRenderingContext2D context, double lifetime,
                                              double opacity, double width, double glowWidth, double strength) {
        double[] spread = {1.0D, 0.62D, 0.32D};
        double[] weight = {1.0D, 1.45D, 2.0D};
        for (int layer = 0; layer < spread.length; layer++) {
            double gain = Math.min(1.0D, strength * weight[layer] * 0.55D);
            if (gain <= 0.004D) continue;
            for (int index = 1; index < TRAIL.size(); index++) {
                TrailPoint previous = TRAIL.get(index - 1);
                TrailPoint current = TRAIL.get(index);
                double alpha = segmentAlpha(previous, current, lifetime) * opacity * gain;
                if (alpha <= 0.004D) continue;
                context.beginPath();
                context.moveTo(previous.x, previous.y);
                context.lineTo(current.x, current.y);
                context.setLineWidth(Math.max(width, glowWidth * spread[layer]));
                context.setLineCap("round");
                context.setLineJoin("round");
                context.setStrokeStyle(rgba(themeColor(), alpha));
                context.stroke();
            }
        }
    }

    private static void renderTrailGlowFine(CanvasRenderingContext2D context, double lifetime,
                                            double opacity, double width, double glowWidth, double strength) {
        double[] spread = {1.0D, 0.88D, 0.77D, 0.66D, 0.56D, 0.47D, 0.39D, 0.32D, 0.25D, 0.18D};
        for (int layer = spread.length - 1; layer >= 0; layer--) {
            double falloff = 1.0D - (double) layer / spread.length;
            double gaussian = Math.exp(-3.2D * (1.0D - falloff) * (1.0D - falloff));
            double gain = Math.min(1.0D, strength * (0.22D + gaussian * 1.35D));
            if (gain <= 0.002D) continue;
            double layerWidth = Math.max(width, glowWidth * spread[layer]);
            for (int index = 1; index < TRAIL.size(); index++) {
                TrailPoint previous = TRAIL.get(index - 1);
                TrailPoint current = TRAIL.get(index);
                double alpha = segmentAlpha(previous, current, lifetime) * opacity * gain;
                if (alpha <= 0.002D) continue;
                context.beginPath();
                context.moveTo(previous.x, previous.y);
                context.lineTo(current.x, current.y);
                context.setLineWidth(layerWidth);
                context.setLineCap("round");
                context.setLineJoin("round");
                context.setStrokeStyle(rgba(themeColor(), alpha));
                context.stroke();
            }
        }

        double coreGain = Math.min(1.0D, strength * 1.9D);
        for (int index = 1; index < TRAIL.size(); index++) {
            TrailPoint previous = TRAIL.get(index - 1);
            TrailPoint current = TRAIL.get(index);
            double alpha = segmentAlpha(previous, current, lifetime) * opacity * coreGain;
            if (alpha <= 0.002D) continue;
            context.beginPath();
            context.moveTo(previous.x, previous.y);
            context.lineTo(current.x, current.y);
            context.setLineWidth(Math.max(0.8D, width * 0.55D));
            context.setLineCap("round");
            context.setLineJoin("round");
            context.setStrokeStyle(rgba(WHITE, alpha * 0.55D));
            context.stroke();
        }
    }

    private static double segmentAlpha(TrailPoint previous, TrailPoint current, double lifetime) {
        double previousFade = clamp01(1.0D - previous.ageMs / lifetime);
        double currentFade = clamp01(1.0D - current.ageMs / lifetime);
        return (previousFade + currentFade) * 0.5D;
    }

    private static int[] themeColor() {
        String hex = Baeffect.text("color");
        if (hex.equals(themeCacheKey)) return themeCache;

        try {
            int rgb = Integer.parseInt(hex.substring(1), 16);
            themeCache = new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
        } catch (RuntimeException exception) {
            themeCache = new int[]{76, 167, 255};
        }
        themeCacheKey = hex;
        return themeCache;
    }

    private static double bloomGain() {
        return Math.max(0.0D, Math.min(2.0D, Baeffect.num("bloom.intensity") / 1.7D));
    }

    private static String rgba(int[] color, double alpha) {
        return rgba(color[0], color[1], color[2], alpha);
    }

    private static String rgba(int red, int green, int blue, double alpha) {
        double value = clamp01(alpha * Baeffect.num("opacity"));
        if ("normal".equals(Baeffect.text("compositing"))) {
            value = Math.min(value, clamp01(Baeffect.num("overlayAlphaLimit")));
        }

        int scaled = (int) Math.round(value * 1000.0D);
        RGBA.setLength(0);
        RGBA.append("rgba(").append(red).append(',').append(green).append(',').append(blue).append(',');
        RGBA.append(scaled / 1000).append('.');
        int rest = scaled % 1000;
        if (rest < 100) RGBA.append('0');
        if (rest < 10) RGBA.append('0');
        RGBA.append(rest).append(')');
        return RGBA.toString();
    }

    private static double key(double time, double[][] keys) {
        double t = clamp01(time);
        if (t <= keys[0][0]) return keys[0][1];
        for (int index = 1; index < keys.length; index++) {
            if (t <= keys[index][0]) {
                double span = keys[index][0] - keys[index - 1][0];
                double local = span <= 0.0D ? 0.0D : (t - keys[index - 1][0]) / span;
                return lerp(keys[index - 1][1], keys[index][1], local);
            }
        }
        return keys[keys.length - 1][1];
    }

    private static double lerp(double start, double end, double amount) {
        return start + (end - start) * amount;
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static double random() {
        return ThreadLocalRandom.current().nextDouble();
    }

    private static double randomAngle() {
        return random() * TAU;
    }

    private static final class Ring {
        private final double x;
        private final double y;
        private final double baseRadius;
        private final double life;
        private final double angularBlend;
        private double angularVelocity;
        private double angle;
        private double age;

        private Ring(double x, double y, int index, int count, double unit) {
            this.x = x;
            this.y = y;
            // 上游：radius = random(radiusMin, radiusMax)，再乘视口缩放。
            this.baseRadius = Math.max(0.5D, lerp(
                    Baeffect.num("rings.radiusMin"),
                    Baeffect.num("rings.radiusMax"),
                    random()) * unit);
            this.life = Math.max(50.0D, Baeffect.num("rings.lifetimeMs"));
            this.angle = randomAngle();
            this.angularBlend = random();
            this.angularVelocity = angularVelocity(this.angularBlend, 0.0D);
        }

        private void advance(double deltaMs) {
            double previous = age;
            age += deltaMs;
            double progress = life <= 0.0D ? 1.0D : (previous + age) * 0.5D / life;
            angularVelocity = angularVelocity(angularBlend, progress);
            angle += angularVelocity * (deltaMs / 1000.0D);
        }

        //上游 tc()：速度 = lerp(minKeys, maxKeys, blend) × multiplier × direction。
        private static double angularVelocity(double blend, double progress) {
            double min = smooth(RING_ANGULAR_MIN_KEYS, progress);
            double max = smooth(RING_ANGULAR_MAX_KEYS, progress);
            double direction = Baeffect.num("rings.rotationDirection") < 0.0D ? -1.0D : 1.0D;
            return lerp(min, max, clamp01(blend))
                    * Baeffect.num("rings.angularVelocityMultiplier") * direction;
        }
    }

    private static final class Shard {
        private double x;
        private double y;
        private double vx;
        private double vy;
        private double rotation;
        private double spin;
        private double size;
        private double age;
        private double life;
        private double timeScale;
    }

    private static final class Disk {
        private final double x;
        private final double y;
        private final double life;
        private double age;

        private Disk(double x, double y) {
            this.x = x;
            this.y = y;
            this.life = Math.max(50.0D, Baeffect.num("disk.lifetimeMs"));
        }
    }

    private static final class Flash {
        private final double x;
        private final double y;
        private final double radius;
        private final double life;
        private final int rayCount;
        private final double[] rays;
        private double age;

        private Flash(double x, double y, double radius, double life, int rayCount) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.life = life;
            this.rayCount = rayCount;
            this.rays = new double[Math.max(0, rayCount)];
            for (int index = 0; index < this.rays.length; index++) {
                this.rays[index] = (TAU * index / Math.max(1, rayCount)) + (random() - 0.5D) * 0.25D;
            }
        }

        private static Flash hit(double x, double y, double unit) {
            return new Flash(x, y, Baeffect.num("hit.radius") * unit,
                    Math.max(20.0D, Baeffect.num("hit.lifetimeMs")), 0);
        }

        private static Flash flare(double x, double y, double unit) {
            return new Flash(x, y, Baeffect.num("flare.radius") * unit,
                    Math.max(20.0D, Baeffect.num("flare.lifetimeMs")),
                    (int) Math.round(Math.max(1.0D, Baeffect.num("flare.rayCount"))));
        }
    }

    private static final class TrailPoint {
        private double x;
        private double y;
        private double ageMs;
    }
}
