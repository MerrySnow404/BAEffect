package com.sighs.baeffect;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class PhiraRespackManager {
    private static final Map<String, PhiraRespack> LOADED = new LinkedHashMap<>();
    private static final List<String> ERRORS = new java.util.ArrayList<>();
    private static boolean scanned;
    private static volatile double progress;
    private static volatile String progressText = "";

    public static double progress() {
        return progress;
    }

    public static String progressText() {
        return progressText;
    }

    private PhiraRespackManager() {
    }

    public static Collection<PhiraRespack> all() {
        return LOADED.values();
    }

    public static PhiraRespack get(String id) {
        return id == null ? null : LOADED.get(id);
    }

    public static boolean isEmpty() {
        return LOADED.isEmpty();
    }

    public static List<String> ids() {
        return List.copyOf(LOADED.keySet());
    }

    public static List<String> errors() {
        return List.copyOf(ERRORS);
    }

    public static Path resourcePackDir() {
        return FMLPaths.CONFIGDIR.get().resolve("baeffect/phira_resourcepacks");
    }

    public static String resourceRoot(String id) {
        return "baeffect/phira/" + id;
    }

    public static void ensureScanned() {
        if (scanned) return;
        reload();
    }

    public static void reload() {
        scanned = true;
        LOADED.clear();
        ERRORS.clear();
        PhiraSound.reset();

        Path directory = resourcePackDir();
        if (!Files.isDirectory(directory)) {
            try {
                Files.createDirectories(directory);
            } catch (IOException exception) {
                Baeffect.LOGGER.warn("无法创建资源包目录：{}：{}", directory, exception);
                return;
            }
        }

        ensureDefaultPack(directory);

        scanDirectory(directory);

        if (LOADED.isEmpty()) {
            Baeffect.LOGGER.warn("未找到资源包。请把资源包目录或 zip 放进：{}", directory);
        } else {
            Baeffect.LOGGER.info("已加载资源包 {} 个：{}", LOADED.size(), LOADED.keySet());
        }
    }

    private static void scanDirectory(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) return;
        try (Stream<Path> stream = Files.list(directory)) {
            List<Path> entries = stream.sorted().toList();
            Baeffect.LOGGER.info("该目录下有 {} 个条目", entries.size());
            for (Path path : entries) {
                String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                try {
                    PhiraRespack pack;
                    if (Files.isDirectory(path)) {
                        pack = PhiraRespack.parseDirectory(path);
                    } else if (fileName.endsWith(".zip")) {
                        pack = PhiraRespack.parseZip(path);
                    } else {
                        Baeffect.LOGGER.info("跳过非资源包条目：{}", path.getFileName());
                        continue;
                    }
                    if (LOADED.containsKey(pack.id)) {
                        Baeffect.LOGGER.warn("资源包重名，已跳过：{}（id={}）", path.getFileName(), pack.id);
                        continue;
                    }
                    LOADED.put(pack.id, pack);
                    validateSounds(pack);
                } catch (Throwable throwable) {
                    String message = path.getFileName() + "：" + throwable.getMessage();
                    ERRORS.add(message);
                    Baeffect.LOGGER.warn("资源包导入失败：{}", message);
                }
            }
        } catch (IOException exception) {
            Baeffect.LOGGER.warn("扫描资源包目录失败：{}：{}", directory, exception);
        }
    }

    private static boolean ensureDefaultPack(Path directory) {
        if (Files.isDirectory(directory.resolve("phi_origin"))
                || Files.isRegularFile(directory.resolve("phi_origin.zip"))) {
            return false;
        }
        try (var stream = PhiraRespackManager.class.getResourceAsStream("/assets/baeffect/default_pack/phi_origin.zip")) {
            if (stream != null) {
                Files.createDirectories(directory);
                Files.copy(stream, directory.resolve("phi_origin.zip"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Baeffect.LOGGER.info("已放出内置默认资源包");
                return true;
            }
        } catch (Throwable throwable) {
            Baeffect.LOGGER.warn("放出内置资源包失败：{}", String.valueOf(throwable));
        }
        return false;
    }

    public static void load(PhiraRespack pack) {
        if (pack == null || pack.extractedPath != null) return;
        progress = 0.0D;
        progressText = "准备加载 " + pack.name;
        extract(pack);
        if (pack.extractedPath != null) {
            progress = 1.0D;
            progressText = "已加载 " + pack.name;
        } else {
            progress = 0.0D;
            progressText = "加载失败：" + pack.name;
        }
    }

    private static void extract(PhiraRespack pack) {
        Path target = FMLPaths.GAMEDIR.get().resolve("apricity").resolve(resourceRoot(pack.id));
        try {
            Files.createDirectories(target);
            byte[] hitFx = pack.hitFxBytes();
            if (hitFx == null) {
                ERRORS.add(pack.name + "：缺少 hit_fx.png");
                progressText = "缺少 hit_fx.png";
                return;
            }
            Files.write(target.resolve("hit_fx.png"), hitFx);
            progress = 0.5D;
            progressText = "正在写入贴图…";

            int index = 0;
            int total = Math.max(1, pack.images.size());
            for (Map.Entry<String, byte[]> entry : pack.images.entrySet()) {
                if (!"hit_fx.png".equals(entry.getKey())) {
                    Files.write(target.resolve(entry.getKey()), entry.getValue());
                }
                index++;
                progress = 0.5D + 0.5D * index / total;
            }
            pack.extractedPath = target;
            splitHold(pack, target, "hold.png", pack.holdAtlas);
            splitHold(pack, target, "hold_mh.png", pack.holdAtlasMH);
            ensureFrames(pack);
        } catch (IOException exception) {
            String message = pack.name + "：" + exception.getMessage();
            ERRORS.add(message);
            progressText = "写入失败";
            Baeffect.LOGGER.warn("资源包贴图缓存写入失败：{}：{}", target, exception);
        }
    }

    private static boolean splitFrames(PhiraRespack pack, Path target, byte[] atlasBytes, String tint) {
        try (var input = new java.io.ByteArrayInputStream(atlasBytes)) {
            java.awt.image.BufferedImage raw = javax.imageio.ImageIO.read(input);
            if (raw == null) {
                ERRORS.add(pack.name + "：hit_fx.png 无法解码");
                return false;
            }

            java.awt.image.BufferedImage atlas = new java.awt.image.BufferedImage(
                    raw.getWidth(), raw.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = atlas.createGraphics();
            g.setComposite(java.awt.AlphaComposite.Src);
            g.drawImage(raw, 0, 0, null);
            g.dispose();

            if (!raw.getColorModel().hasAlpha()) {
                int removed = removeSolidBackground(atlas);
                Baeffect.LOGGER.info("资源包图集无 alpha 通道，已按纯色背景抠除 {} 像素：{}", removed, pack.name);
            }

            int columns = Math.max(1, pack.hitFxColumns);
            int rows = Math.max(1, pack.hitFxRows);

            if (atlas.getWidth() % columns != 0 || atlas.getHeight() % rows != 0) {
                ERRORS.add(pack.name + "：hit_fx.png 尺寸 " + atlas.getWidth() + "x" + atlas.getHeight()
                        + " 不能被网格 " + columns + "x" + rows + " 整除");
                return false;
            }

            int frameW = atlas.getWidth() / columns;
            int frameH = atlas.getHeight() / rows;
            if (frameW <= 0 || frameH <= 0) {
                ERRORS.add(pack.name + "：图集 " + atlas.getWidth() + "x" + atlas.getHeight()
                        + " 无法切成 " + columns + "x" + rows + " 帧");
                return false;
            }

            int tr = 255;
            int tg = 255;
            int tb = 255;
            if (tint != null) {
                int rgb = Integer.parseInt(tint.substring(1), 16);
                tr = (rgb >> 16) & 0xFF;
                tg = (rgb >> 8) & 0xFF;
                tb = rgb & 0xFF;
            }

            Files.createDirectories(target);
            int count = 0;
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    java.awt.image.BufferedImage frame =
                            atlas.getSubimage(column * frameW, row * frameH, frameW, frameH);
                    java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(
                            frameW, frameH, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < frameH; y++) {
                        for (int x = 0; x < frameW; x++) {
                            int pixel = frame.getRGB(x, y);
                            int alpha = (pixel >>> 24) & 0xFF;
                            if (alpha == 0) continue;
                            if (tint == null) {
                                out.setRGB(x, y, pixel);
                                continue;
                            }
                            int r = ((pixel >> 16) & 0xFF) * tr / 255;
                            int gg = ((pixel >> 8) & 0xFF) * tg / 255;
                            int b = (pixel & 0xFF) * tb / 255;
                            out.setRGB(x, y, (alpha << 24) | (r << 16) | (gg << 8) | b);
                        }
                    }
                    javax.imageio.ImageIO.write(out, "png",
                            target.resolve("frame_" + count + ".png").toFile());
                    count++;
                }
            }
            pack.frameWidth = frameW;
            pack.frameHeight = frameH;
            pack.frameCount = count;
            pack.ready = true;
            Baeffect.LOGGER.info("资源包图集已切帧：{} 帧，每帧 {}x{}，配色 {}", count, frameW, frameH,
                    tint == null ? "原始" : tint);
            return true;
        } catch (Throwable throwable) {
            ERRORS.add(pack.name + "：切帧失败 " + throwable.getMessage());
            Baeffect.LOGGER.warn("资源包图集切帧失败：{}：{}", pack.name, throwable);
            return false;
        }
    }

    private static void splitHold(PhiraRespack pack, Path target, String fileName, int[] atlas) {
        byte[] bytes = pack.images.get(fileName);
        if (bytes == null) return;
        if (atlas == null || atlas.length < 2) {
            ERRORS.add(pack.name + "：" + fileName + " 缺少 holdAtlas 配置，跳过三段切分");
            return;
        }
        try (var input = new java.io.ByteArrayInputStream(bytes)) {
            java.awt.image.BufferedImage raw = javax.imageio.ImageIO.read(input);
            if (raw == null) {
                ERRORS.add(pack.name + "：" + fileName + " 无法解码");
                return;
            }
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                    raw.getWidth(), raw.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setComposite(java.awt.AlphaComposite.Src);
            g.drawImage(raw, 0, 0, null);
            g.dispose();

            int tailH = atlas[0];
            int headH = atlas[1];
            int bodyH = img.getHeight() - tailH - headH;
            if (tailH <= 0 || headH <= 0 || bodyH <= 0 || tailH + headH > img.getHeight()) {
                ERRORS.add(pack.name + "：" + fileName + " 的 holdAtlas=" + tailH + "," + headH
                        + " 与图片高 " + img.getHeight() + " 不匹配");
                return;
            }
            String base = fileName.substring(0, fileName.length() - 4);
            writeRegion(img, 0, 0, img.getWidth(), tailH, target.resolve(base + "_tail.png"));
            writeRegion(img, 0, tailH, img.getWidth(), bodyH, target.resolve(base + "_body.png"));
            writeRegion(img, 0, tailH + bodyH, img.getWidth(), headH, target.resolve(base + "_head.png"));
        } catch (Throwable throwable) {
            ERRORS.add(pack.name + "：" + fileName + " 切分失败 " + throwable.getMessage());
        }
    }

    private static void writeRegion(java.awt.image.BufferedImage src, int x, int y, int w, int h, Path out)
            throws IOException {
        java.awt.image.BufferedImage region = new java.awt.image.BufferedImage(
                w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = region.createGraphics();
        g.setComposite(java.awt.AlphaComposite.Src);
        g.drawImage(src.getSubimage(x, y, w, h), 0, 0, null);
        g.dispose();
        javax.imageio.ImageIO.write(region, "png", out.toFile());
    }

    private static int removeSolidBackground(java.awt.image.BufferedImage image) {
        int[] corners = {
                image.getRGB(0, 0),
                image.getRGB(image.getWidth() - 1, 0),
                image.getRGB(0, image.getHeight() - 1),
                image.getRGB(image.getWidth() - 1, image.getHeight() - 1)
        };
        int background = corners[0];
        for (int corner : corners) {
            if (corner != background) return 0;
        }
        int br = (background >> 16) & 0xFF;
        int bg = (background >> 8) & 0xFF;
        int bb = background & 0xFF;
        boolean bright = br > 200 && bg > 200 && bb > 200;
        boolean dark = br < 24 && bg < 24 && bb < 24;
        if (!bright && !dark) return 0;

        int removed = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                boolean near = Math.abs(r - br) <= 12 && Math.abs(g - bg) <= 12 && Math.abs(b - bb) <= 12;
                if (near) {
                    image.setRGB(x, y, 0);
                    removed++;
                }
            }
        }
        return removed;
    }

    public static String framesDir(PhiraRespack pack) {
        if (pack == null || pack.extractedPath == null) return null;
        String key = pack.framesKey == null || pack.framesKey.isEmpty() ? framesKey() : pack.framesKey;
        return "/" + resourceRoot(pack.id) + "/" + framesFolder(key);
    }

    private static String framesFolder(String key) {
        return key.startsWith("tint:") ? "frames_tint_" + key.substring(5) : "frames";
    }

    public static String framesKey() {
        String tint = Baeffect.flag("phiraTint") ? Baeffect.text("color") : null;
        return tint != null && tint.matches("^#[0-9a-fA-F]{6}$")
                ? "tint:" + tint.toLowerCase(java.util.Locale.ROOT)
                : "plain";
    }

    public static void ensureFrames(PhiraRespack pack) {
        if (pack == null || pack.extractedPath == null) return;
        String key = framesKey();
        if (key.equals(pack.framesKey) && pack.ready) return;

        byte[] atlas = pack.hitFxBytes();
        if (atlas == null) return;

        deleteRecursively(pack.extractedPath.resolve("frames"));
        deleteRecursively(pack.extractedPath.resolve("frames_tint"));

        Path target = pack.extractedPath.resolve(framesFolder(key));
        if (splitFrames(pack, target, atlas, key.startsWith("tint:") ? key.substring(5) : null)) {
            pack.framesKey = key;
            pack.ready = true;
            clearOtherFrames(pack, framesFolder(key));
        }
    }

    private static void clearOtherFrames(PhiraRespack pack, String keep) {
        try (var dirs = Files.list(pack.extractedPath)) {
            for (Path path : dirs.toList()) {
                String name = path.getFileName().toString();
                if (!Files.isDirectory(path)) continue;
                if (!name.startsWith("frames")) continue;
                if (name.equals(keep)) continue;
                deleteRecursively(path);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void deleteRecursively(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (var children = Files.list(path)) {
                    for (Path child : children.toList()) {
                        deleteRecursively(child);
                    }
                }
            }
            Files.deleteIfExists(path);
        } catch (Throwable ignored) {
            //pass
        }
    }

    private static boolean tintFrames(PhiraRespack pack, String color, Path target) {
        Path source = pack.extractedPath.resolve("frames");
        if (!Files.isDirectory(source)) return false;
        int rgb = Integer.parseInt(color.substring(1), 16);
        int tr = (rgb >> 16) & 0xFF;
        int tg = (rgb >> 8) & 0xFF;
        int tb = rgb & 0xFF;
        try {
            Files.createDirectories(target);
            try (var files = Files.list(source)) {
                for (Path file : files.toList()) {
                    String name = file.getFileName().toString();
                    if (!name.endsWith(".png")) continue;
                    java.awt.image.BufferedImage image =
                            javax.imageio.ImageIO.read(file.toFile());
                    if (image == null) continue;
                    java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(
                            image.getWidth(), image.getHeight(),
                            java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < image.getHeight(); y++) {
                        for (int x = 0; x < image.getWidth(); x++) {
                            int pixel = image.getRGB(x, y);
                            int alpha = (pixel >>> 24) & 0xFF;
                            if (alpha == 0) {
                                out.setRGB(x, y, 0);
                                continue;
                            }
                            int r = ((pixel >> 16) & 0xFF) * tr / 255;
                            int g = ((pixel >> 8) & 0xFF) * tg / 255;
                            int b = (pixel & 0xFF) * tb / 255;
                            out.setRGB(x, y, (alpha << 24) | (r << 16) | (g << 8) | b);
                        }
                    }
                    javax.imageio.ImageIO.write(out, "png", target.resolve(name).toFile());
                }
            }
            Baeffect.LOGGER.info("已生成着色帧：{}", target.getFileName());
            return true;
        } catch (Throwable throwable) {
            Baeffect.LOGGER.warn("生成着色帧失败：{}：{}", target, throwable);
            return false;
        }
    }

    public static String hitFxTexture(PhiraRespack pack) {
        if (pack == null) return null;
        if (pack.extractedPath == null) {
            load(pack);
            if (pack.extractedPath == null) return null;
        }
        boolean tinted = pack.hitFxTinted && Baeffect.flag("phiraTint");
        if (!tinted) {
            return "/" + resourceRoot(pack.id) + "/hit_fx.png";
        }
        String color = Baeffect.text("color");
        if (color == null || !color.matches("^#[0-9a-fA-F]{6}$")) {
            return "/" + resourceRoot(pack.id) + "/hit_fx.png";
        }
        String name = "hit_fx_tint_" + color.substring(1).toLowerCase(Locale.ROOT) + ".png";
        Path file = pack.extractedPath.resolve(name);
        if (!Files.isRegularFile(file)) {
            if (!tint(pack, color, file)) return "/" + resourceRoot(pack.id) + "/hit_fx.png";
        }
        return "/" + resourceRoot(pack.id) + "/" + name;
    }

    private static boolean tint(PhiraRespack pack, String color, Path target) {
        byte[] bytes = pack.hitFxBytes();
        if (bytes == null) return false;
        try (var input = new java.io.ByteArrayInputStream(bytes)) {
            java.awt.image.BufferedImage raw = javax.imageio.ImageIO.read(input);
            if (raw == null) return false;

            java.awt.image.BufferedImage source = new java.awt.image.BufferedImage(
                    raw.getWidth(), raw.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = source.createGraphics();
            g.setComposite(java.awt.AlphaComposite.Src);
            g.drawImage(raw, 0, 0, null);
            g.dispose();

            int rgb = Integer.parseInt(color.substring(1), 16);
            int tr = (rgb >> 16) & 0xFF;
            int tg = (rgb >> 8) & 0xFF;
            int tb = rgb & 0xFF;

            java.awt.image.BufferedImage output = new java.awt.image.BufferedImage(
                    source.getWidth(), source.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    int pixel = source.getRGB(x, y);
                    int alpha = (pixel >>> 24) & 0xFF;
                    if (alpha == 0) {
                        output.setRGB(x, y, 0);
                        continue;
                    }
                    int r = ((pixel >> 16) & 0xFF) * tr / 255;
                    int g2 = ((pixel >> 8) & 0xFF) * tg / 255;
                    int b = (pixel & 0xFF) * tb / 255;
                    output.setRGB(x, y, (alpha << 24) | (r << 16) | (g2 << 8) | b);
                }
            }
            javax.imageio.ImageIO.write(output, "png", target.toFile());
            Baeffect.LOGGER.info("已生成着色贴图：{}", target.getFileName());
            return true;
        } catch (Throwable throwable) {
            Baeffect.LOGGER.warn("生成着色贴图失败：{}：{}", target, throwable);
            return false;
        }
    }

    private static void validateSounds(PhiraRespack pack) {
        String[] names = {"click.ogg", "drag.ogg", "flick.ogg"};
        for (String name : names) {
            byte[] bytes = pack.images.get(name);
            if (bytes == null) continue;
            try (var in = new java.io.ByteArrayInputStream(bytes);
                 var audio = javax.sound.sampled.AudioSystem.getAudioInputStream(in)) {
                float rate = audio.getFormat().getSampleRate();
                if (Math.abs(rate - 44100f) > 1f) {
                    ERRORS.add(pack.name + "：" + name + " 采样率 " + rate
                            + "Hz，规范要求 44100Hz");
                }
            } catch (Throwable throwable) {
                ERRORS.add(pack.name + "：" + name + " 无法解析（" + throwable.getMessage() + "）");
            }
        }
    }
}