package com.sighs.baeffect;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class BAClickFxHtmlSettings {
    private static final String PAGE_PATH = "baeffect/settings.html";

    private static final String[] SCREEN_CLASSES = {
            "com.sighs.apricityui.screen.ApricityScreen",
            "com.sighs.apricityui.client.ApricityScreen",
            "com.sighs.apricityui.instance.ApricityScreen",
            "com.sighs.apricityui.ApricityScreen"
    };

    private static Screen hostScreen;
    /** 打开设置前所在的界面，关闭设置后回到这里。 */
    private static Screen returnScreen;
    private static int pushedRevision = -1;
    private static boolean loggedElements;
    private static String colorPickerOpeningKey = null;
    private static String lastPhiraSignature = null;
    /** 正在被拖动的主题滑块（.slider），null 表示未拖动。 */
    private static Element draggedSlider;
    private static String draggedSliderKey;

    private BAClickFxHtmlSettings() {
    }

    public static boolean isOpen() {
        return document() != null;
    }

    public static void toggle() {
        Minecraft.getInstance().execute(() -> {
            if (isOpen()) {
                closeOnClient();
            } else {
                openOnClient();
            }
        });
    }

    /** 打开设置页；已打开时保持现状。 */
    public static void open() {
        Minecraft.getInstance().execute(() -> {
            if (!isOpen()) {
                openOnClient();
            }
        });
    }

    private static void openOnClient() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen previous = minecraft.screen;
        Screen inherited = returnScreen;
        boolean reopen = previous != null && previous == hostScreen;
        closeOnClient();
        // 记录来源界面；若本身就在设置页里重开，则保留原来的来源。
        returnScreen = reopen ? inherited : previous;

        for (String className : SCREEN_CLASSES) {
            try {
                Class<?> type = Class.forName(className);
                if (!Screen.class.isAssignableFrom(type)) continue;
                Screen screen = (Screen) type.getConstructor(String.class).newInstance(PAGE_PATH);
                minecraft.setScreen(screen);
                hostScreen = screen;
                return;
            } catch (Throwable ignored) {
                //pass
            }
        }
        Baeffect.LOGGER.error("HTML setting is error：" + PAGE_PATH);
    }

    private static void closeOnClient() {
        int removed = 0;
        for (Document document : new ArrayList<>(Baeffect.documents(PAGE_PATH))) {
            if (document != null) {
                document.remove();
                removed++;
            }
        }
        Minecraft minecraft = Minecraft.getInstance();
        Screen target = returnScreen;
        returnScreen = null;
        if (hostScreen != null && minecraft.screen == hostScreen) {
            minecraft.setScreen(target);
        }
        hostScreen = null;
        pushedRevision = -1;
        loggedElements = false;
        colorPickerOpeningKey = null;
        draggedSlider = null;
        draggedSliderKey = null;
        lastPhiraSignature = null;
        loadingPackId = null;
        lastStatusMessage = null;
        lastPhiraInfo = null;
        lastProbe = null;
        Baeffect.LOGGER.info("HTML Close：clean  " + removed);
    }

    private static Document document() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) {
            try {
                Object value = screen.getClass().getMethod("getLinkedDocument").invoke(screen);
                if (value instanceof Document document) return document;
            } catch (Throwable ignored) {
                //null
            }
        }
        List<Document> documents = Baeffect.documents(PAGE_PATH);
        return documents.isEmpty() ? null : documents.get(documents.size() - 1);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (hostScreen != null && minecraft.screen != hostScreen) {
            hostScreen = null;
            pushedRevision = -1;
            loggedElements = false;
            colorPickerOpeningKey = null;
            draggedSlider = null;
            draggedSliderKey = null;
            // 通过 ESC 等方式关闭时也回到来源界面。
            Screen target = returnScreen;
            returnScreen = null;
            if (target != null && minecraft.screen == null) {
                minecraft.setScreen(target);
            }
            return;
        }

        Document document = document();
        if (document == null) {
            pushedRevision = -1;
            return;
        }

        List<Element> controls = query(document, "[data-key]");
        if (!loggedElements) {
            loggedElements = true;
        }

        bindPageEvents(document);

        Element probe = controls.isEmpty() ? null : controls.get(0);
        if (probe != null && probe != lastProbe) {
            lastProbe = probe;
            LAST_SEEN_CHECKED.clear();
            LAST_SEEN_SELECT.clear();
            lastPhiraSignature = null;
            lastPhiraInfo = null;
            lastStatusMessage = null;
            pushedRevision = -1;
            push(document, controls);
            updatePhiraStatus(document);
            return;
        }

        if (Baeffect.revision() != pushedRevision) {
            pushedRevision = Baeffect.revision();
            push(document, controls);
        }
        pullControlState(controls);
        tickSliderRelease();
        updatePhiraStatus(document);

        Element root = document.getElementById("fx-root");
        if (root != null) {
            String patch = root.getAttribute("data-patch");
            if (patch != null && !patch.isBlank()) {
                root.setAttribute("data-patch", "");
                Map<String, Object> parsed = Baeffect.parsePatch(patch);
                if (parsed.containsKey("__save")) {
                    Baeffect.save();
                }
                if (parsed.containsKey("__reset")) {
                    Baeffect.reset();
                }
                if (parsed.containsKey("__close")) {
                    closeOnClient();
                    return;
                }
                if (!parsed.isEmpty()) {
                    Baeffect.applyPatch(parsed);
                    pushedRevision = -1;
                }
            }
            if ("1".equals(root.getAttribute("data-close"))) {
                root.setAttribute("data-close", "");
                closeOnClient();
                return;
            }
        }
    }

    private static final Map<String, Boolean> LAST_SEEN_CHECKED = new java.util.HashMap<>();
    private static final Map<String, String> LAST_SEEN_SELECT = new java.util.HashMap<>();

    private static void pullControlState(List<Element> controls) {
        for (Element element : controls) {
            String key = element.getAttribute("data-key");
            if (key == null || key.isEmpty()) continue;

            String type = element.getAttribute("type");
            boolean isSelect = isSelect(element);

            // 开关与滑块由 Java 直接驱动，页面自身不产生可回读的值。
            if (isSwitch(element) || isSlider(element)) continue;

            if ("checkbox".equalsIgnoreCase(type)) {
                boolean page = element.isChecked();
                Boolean last = LAST_SEEN_CHECKED.get(key);
                if (last == null) {
                    LAST_SEEN_CHECKED.put(key, page);
                } else if (page != last) {
                    LAST_SEEN_CHECKED.put(key, page);
                    if (page != Baeffect.flag(key)) {
                        Baeffect.applyPatch(Map.<String, Object>of(key, page));
                        pushedRevision = -1;
                    }
                }
                continue;
            }

            if (isSelect) {
                String page = element.getValue();
                if (page == null) continue;
                String last = LAST_SEEN_SELECT.get(key);
                if (last == null) {
                    LAST_SEEN_SELECT.put(key, page);
                } else if (!page.equals(last)) {
                    LAST_SEEN_SELECT.put(key, page);
                    if ("preset".equals(key)) {
                        Baeffect.applyPreset(page);
                        pushedRevision = -1;
                    } else if (!page.equals(Baeffect.text(key))) {
                        Baeffect.applyPatch(Map.<String, Object>of(key, page));
                        pushedRevision = -1;
                    }
                }
                continue;
            }

            String pageValue = element.value;
            if (pageValue == null || pageValue.isEmpty()) continue;
            double parsed;
            try {
                parsed = Double.parseDouble(pageValue.trim());
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (Math.abs(parsed - Baeffect.num(key)) > 1.0E-4D) {
                Baeffect.applyPatch(Map.<String, Object>of(key, parsed));
                pushedRevision = -1;
            }
        }
    }

    private static String selectValue(Element select) {
        return select.getValue();
    }

    private static boolean writeSelectValue(Element select, String value) {
        select.setValue(value);
        return true;
    }

    private static List<Element> query(Document document, String selector) {
        for (String name : new String[]{"querySelectorAll", "querySelectAll"}) {
            try {
                Object value = document.getClass().getMethod(name, String.class).invoke(document, selector);
                if (value instanceof List<?> list) {
                    List<Element> elements = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Element element) elements.add(element);
                    }
                    return elements;
                }
            } catch (Throwable ignored) {
                //pass
            }
        }
        return List.of();
    }

    private static Document eventDocument;
    private static boolean sliderDragBound;

    private static void bindPageEvents(Document document) {
        if (eventDocument == document) return;
        eventDocument = document;
        sliderDragBound = false;

        bindButton(document, "fx-save", "save");
        bindButton(document, "fx-reset", "reset");
        bindButton(document, "fx-close", "close");
        bindSliderDrag(document);

        List<Element> controls = query(document, "[data-key]");
        for (Element element : controls) {
            String key = element.getAttribute("data-key");
            if (key == null || key.isEmpty()) continue;

            if (isSwitch(element)) {
                if ("1".equals(element.getAttribute("data-switch-bound"))) continue;
                element.setAttribute("data-switch-bound", "1");
                element.addEventListener("click", event -> {
                    boolean value = !Baeffect.flag(key);
                    Baeffect.applyPatch(Map.<String, Object>of(key, value));
                    setSwitchState(element, value);
                    pushedRevision = -1;
                });
                continue;
            }

            if (isSlider(element)) {
                if ("1".equals(element.getAttribute("data-slider-bound"))) continue;
                element.setAttribute("data-slider-bound", "1");
                element.addEventListener("mousedown", event -> {
                    draggedSlider = element;
                    draggedSliderKey = key;
                    if (event instanceof com.sighs.apricityui.event.MouseEvent mouse) {
                        applySliderAt(element, key, mouse.clientX);
                    } else {
                        double[] bounds = rect(element);
                        if (bounds != null) applySliderAt(element, key, bounds[0] + bounds[2] * 0.5D);
                    }
                });
                continue;
            }

            if ("1".equals(element.getAttribute("data-color-swatch"))) {
                if ("1".equals(element.getAttribute("data-color-bound"))) continue;
                element.setAttribute("data-color-bound", "1");
                try {
                    element.addEventListener("click", event -> {
                        Minecraft.getInstance().execute(() -> openColorPicker(document, element, key));
                    });
                } catch (Throwable throwable) {
                    Baeffect.LOGGER.error("监听失败：{}：{}", key, throwable);
                }
            }
        }
    }

    private static void bindButton(Document document, String id, String action) {
        Element button = document.getElementById(id);
        if (button == null) {
            Baeffect.LOGGER.warn("HTML setting is null：{}", id);
            return;
        }
        try {
            button.addEventListener("click", event -> handlePageAction(document, action));
        } catch (Throwable throwable) {
            Baeffect.LOGGER.warn("HTML setting监听失败：{}：{}", id, throwable);
        }
    }

    private static long resetArmedAt;
    private static String loadingPackId;
    private static String lastStatusMessage;
    private static String lastPhiraInfo;
    private static Element lastProbe;

    private static String fallback(String value) {
        return value == null || value.isBlank() ? "（未填写）" : value;
    }

    private static void handlePageAction(Document document, String detail) {
        if (detail == null) return;
        String action = detail.trim().toLowerCase(Locale.ROOT);
        if (action.contains("save")) {
            Baeffect.save();
            setStatus(document, "已保存到 config/baeffect.toml");
        } else if (action.contains("reset")) {
            long now = System.currentTimeMillis();
            if (now - resetArmedAt > 5000L) {
                resetArmedAt = now;
                setStatus(document, "再点一次「重制配置」以恢复默认值");
                return;
            }
            resetArmedAt = 0L;
            Baeffect.reset();
            setStatus(document, "已恢复默认值");
        } else if (action.contains("close")) {
            closeOnClient();
        } else {
            Baeffect.applyPatch(Baeffect.parsePatch(detail));
        }
        pushedRevision = -1;
    }

    private static void openColorPicker(Document document, Element anchor, String key) {
        if (key.equals(colorPickerOpeningKey)) {
            return;
        }
        colorPickerOpeningKey = key;

        try {
            Class<?> pickerClass = Class.forName("com.sighs.apricityui.ui.ColorPicker");

            String current = Baeffect.text(key);
            if (current == null || current.isEmpty()) current = "#4ca7ff";

            Object future = pickerClass
                    .getMethod("pick", String.class)
                    .invoke(null, current);

            if (future instanceof CompletableFuture<?> completable) {
                completable.whenComplete((result, error) -> {
                    colorPickerOpeningKey = null;
                    if (error != null) {
                        Baeffect.LOGGER.warn("选择器异常：{}", String.valueOf(error));
                        return;
                    }
                    if (!(result instanceof Optional<?> optional)) {
                        return;
                    }
                    if (optional.isEmpty()) {
                        return;
                    }
                    String value = String.valueOf(optional.get());
                    Minecraft.getInstance().execute(() -> {
                        Baeffect.applyPatch(Map.<String, Object>of(key, value));
                        anchor.setAttribute("style", swatchStyle(value));
                        setStatus(document, "主题颜色：" + value);
                        pushedRevision = -1;
                    });
                });
            } else {
                Baeffect.LOGGER.warn("选择器返回类型异常：{}", future);
            }
        } catch (Throwable throwable) {
            colorPickerOpeningKey = null;
            Baeffect.LOGGER.warn("选择器不可用：{}", String.valueOf(throwable));
        }
    }

    private static String swatchStyle(String color) {
        return "width:46px;height:18px;padding:0;border:2px solid var(--edge, #1e1e1f);"
                + "background:" + color + ";cursor:pointer;";
    }


    private static void setStatus(Document document, String text) {
        Element status = document.getElementById("fx-status");
        if (status != null) status.setTextContent(text);
    }





    private static double[] rect(Element element) {
        try {
            Object value = element.getClass().getMethod("getBoundingClientRect").invoke(element);
            if (value == null) return null;
            Class<?> type = value.getClass();
            return new double[]{
                    number(type, value, "x"),
                    number(type, value, "y"),
                    number(type, value, "width"),
                    number(type, value, "height")
            };
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * DOMRect 在部分 AUI 版本里是 public 字段（x/y/width/height），
     * 另一些版本才提供同名 getter；两者都要兼容。
     */
    private static double number(Class<?> type, Object target, String name) throws Exception {
        try {
            java.lang.reflect.Field field = type.getField(name);
            Object value = field.get(target);
            if (value instanceof Number number) return number.doubleValue();
        } catch (NoSuchFieldException ignored) {
            // 回退到 getter
        }
        Object value = type.getMethod(name).invoke(target);
        return value instanceof Number number ? number.doubleValue() : 0.0D;
    }

    private static double doubleAttribute(Element element, String name, double fallback) {
        String raw = element.getAttribute(name);
        if (raw == null || raw.isEmpty()) return fallback;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }


    private static void push(Document document, List<Element> controls) {
        for (Element element : controls) {
            String key = element.getAttribute("data-key");
            if (key == null || key.isEmpty()) continue;

            Object raw = Baeffect.value(key);
            boolean isSelect = isSelect(element);

            if (isSwitch(element)) {
                setSwitchState(element, Baeffect.flag(key));
            } else if (isSlider(element)) {
                setSliderVisual(element, Baeffect.num(key));
            } else if ("checkbox".equalsIgnoreCase(element.getAttribute("type"))) {
                boolean want = Baeffect.flag(key);
                if (want != element.isChecked()) {
                    element.setChecked(want);
                }
                LAST_SEEN_CHECKED.put(key, want);
            } else if ("1".equals(element.getAttribute("data-color-swatch"))) {
                String value = raw instanceof String string && !string.isEmpty() ? string : "#4ca7ff";
                element.setAttribute("style", swatchStyle(value));
            } else if (raw instanceof String string) {
                if (isSelect) {
                    if (!string.equals(element.getValue())) {
                        element.setValue(string);
                    }
                    LAST_SEEN_SELECT.put(key, string);
                } else {
                    element.setValue(string);
                }
            } else if (raw instanceof Boolean bool) {
                element.setValue(bool.toString());
            } else {
                element.setValue(round(Baeffect.num(key)));
            }

            Element out = document.getElementById("o-" + key);
            if (out != null) out.setTextContent(display(key));
        }
        syncPhiraOptions(document);
        syncPhiraRowVisibility(document);
    }

    private static void syncPhiraOptions(Document document) {
        Element select = document.getElementById("sel-phiraRespack");
        if (select == null) {
            for (Element element : query(document, "[data-key]")) {
                if ("phiraRespack".equals(element.getAttribute("data-key")) && isSelect(element)) {
                    select = element;
                    break;
                }
            }
        }
        if (select == null) return;

        PhiraRespackManager.ensureScanned();
        List<String> ids = PhiraRespackManager.ids();
        String signature = String.join(",", ids);

        if (!signature.equals(lastPhiraSignature)) {
            lastPhiraSignature = signature;

            for (Element child : new ArrayList<>(select.children)) {
                child.remove();
            }

            if (ids.isEmpty()) {
                Element option = document.createElement("OPTION");
                option.setAttribute("value", "");
                option.setTextContent("（未加载资源包）");
                select.append(option);
            } else {
                for (String id : ids) {
                    PhiraRespack pack = PhiraRespackManager.get(id);
                    Element option = document.createElement("OPTION");
                    option.setAttribute("value", id);
                    option.setTextContent(pack == null ? id : pack.fileName());
                    select.append(option);
                }
            }
        }

        String current = Baeffect.text("phiraRespack");
        if (ids.isEmpty()) {
            if (!current.isEmpty()) Baeffect.applyPatch(Map.<String, Object>of("phiraRespack", ""));
            return;
        }
        if (current.isEmpty() || !ids.contains(current)) {
            current = ids.get(0);
            Baeffect.applyPatch(Map.<String, Object>of("phiraRespack", current));
            pushedRevision = -1;
        }
        if (!current.equals(select.getValue())) {
            select.setValue(current);
        }
        Element out = document.getElementById("o-phiraRespack");
        if (out != null) {
            PhiraRespack pack = PhiraRespackManager.get(current);
            out.setTextContent(pack == null ? current : pack.name);
        }
    }

    private static void syncPhiraRowVisibility(Document document) {
        boolean phira = "phira".equalsIgnoreCase(Baeffect.text("fxStyle"));

        show(document, "phira-row", phira, "flex");
        show(document, "phira-only-tip", phira, "flex");
        show(document, "phira-only-tint", phira, "flex");
        show(document, "phira-only-scale", phira, "flex");
        show(document, "phira-only-sound", phira, "flex");
        show(document, "phira-only-progress", phira, "flex");
        show(document, "phira-only-info", phira, "flex");

        for (String id : new String[]{
                "card-hit", "card-disk", "card-rings", "card-shards", "card-trail", "card-bloom"}) {
            show(document, id, !phira, "block");
        }
    }

    private static boolean isSelect(Element element) {
        return "SELECT".equalsIgnoreCase(element.tagName)
                || "select".equalsIgnoreCase(element.getAttribute("data-kind"));
    }

    private static boolean hasClass(Element element, String name) {
        String classes = element.getAttribute("class");
        if (classes == null) return false;
        for (String token : classes.trim().split("\\s+")) {
            if (token.equals(name)) return true;
        }
        return false;
    }

    /** 主题开关：<span class="switch" data-key="...">。 */
    private static boolean isSwitch(Element element) {
        return hasClass(element, "switch");
    }

    /** 主题拖动条：<div class="slider" data-key="..." min max step>。 */
    private static boolean isSlider(Element element) {
        return hasClass(element, "slider") && !"INPUT".equalsIgnoreCase(element.tagName);
    }

    private static void setSwitchState(Element element, boolean on) {
        element.setAttribute("class", on ? "switch on" : "switch");
    }

    private static void setSliderVisual(Element element, double value) {
        double min = doubleAttribute(element, "min", 0.0D);
        double max = doubleAttribute(element, "max", 100.0D);
        double fraction = max > min ? (value - min) / (max - min) : 0.0D;
        double percent = Math.round(Math.max(0.0D, Math.min(1.0D, fraction)) * 1000.0D) / 10.0D;
        element.setAttribute("style", "--value:" + percent + "%;");
    }

    /** 按鼠标横向位置更新滑块并写回配置。 */
    private static void applySliderAt(Element element, String key, double mouseX) {
        double[] bounds = rect(element);
        if (bounds == null || bounds[2] <= 1.0D) return;

        double fraction = (mouseX - bounds[0]) / bounds[2];
        fraction = Math.max(0.0D, Math.min(1.0D, fraction));

        double min = doubleAttribute(element, "min", 0.0D);
        double max = doubleAttribute(element, "max", 100.0D);
        double step = doubleAttribute(element, "step", 0.0D);
        double value = min + (max - min) * fraction;
        if (step > 0.0D) value = Math.round(value / step) * step;
        value = Math.max(min, Math.min(max, value));

        setSliderVisual(element, value);
        if (Math.abs(value - Baeffect.num(key)) > 1.0E-6D) {
            Baeffect.applyPatch(Map.<String, Object>of(key, value));
            pushedRevision = -1;
        }
    }

    private static void endSliderDrag() {
        draggedSlider = null;
        draggedSliderKey = null;
    }

    /** 只做松开兜底判断；坐标一律用 DOM，避免两套坐标系打架。 */
    private static void tickSliderRelease() {
        if (draggedSlider == null) return;
        long handle = Minecraft.getInstance().getWindow().getWindow();
        if (handle == 0L) {
            endSliderDrag();
            return;
        }
        if (GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            endSliderDrag();
        }
    }

    /**
     * 拖动期间用 AUI 的 DOM mousemove 继续更新。
     * 与 mousedown 共用 clientX（文档坐标），避免和 tick 轮询的 GUI 坐标混用导致值被拉回去。
     */
    private static void bindSliderDrag(Document document) {
        if (sliderDragBound) return;
        sliderDragBound = true;

        Element body = document.body;
        if (body == null) return;

        body.addEventListener("mousemove", event -> {
            if (draggedSlider == null || draggedSliderKey == null) return;
            if (!(event instanceof com.sighs.apricityui.event.MouseEvent mouse)) return;
            applySliderAt(draggedSlider, draggedSliderKey, mouse.clientX);
        });

        body.addEventListener("mouseup", event -> endSliderDrag());
    }

    private static void show(Document document, String id, boolean visible, String display) {
        Element element = document.getElementById(id);
        if (element == null) return;
        element.setAttribute("style", visible ? "display:" + display + ";" : "display:none;");
    }

    private static void updatePhiraStatus(Document document) {
        if (!"phira".equalsIgnoreCase(Baeffect.text("fxStyle"))) return;

        PhiraRespack pack = PhiraHitFx.selected();

        if (pack != null && !pack.ready && !pack.id.equals(loadingPackId)) {
            loadingPackId = pack.id;
            PhiraRespackManager.load(pack);
        }
        if (pack == null || pack.ready) loadingPackId = null;

        if (pack != null && pack.extractedPath != null
                && !PhiraRespackManager.framesKey().equals(pack.framesKey)) {
            PhiraRespackManager.ensureFrames(pack);
        }

        double progress = PhiraRespackManager.progress();
        Element fill = document.getElementById("fx-progress-fill");
        if (fill != null) {
            fill.setAttribute("style", "display:block;width:"
                    + Math.round(Math.max(0.0D, Math.min(1.0D, progress)) * 100.0D) + "%;height:100%;");
        }
        Element percent = document.getElementById("o-phiraProgress");
        if (percent != null) {
            percent.setTextContent(Math.round(progress * 100.0D) + "%");
        }

        Element info = document.getElementById("phira-info");
        if (info != null) {
            String text;
            if (pack == null) {
                text = "未选择资源包";
            } else {
                text = "文件：" + pack.fileName()
                        + "\n名称：" + fallback(pack.name)
                        + "\n作者：" + fallback(pack.author)
                        + "\n介绍：" + fallback(pack.description);
            }
            if (!text.equals(lastPhiraInfo)) {
                lastPhiraInfo = text;
                info.setTextContent(text);
            }
        }

        Element status = document.getElementById("fx-status");
        if (status == null) return;

        String message;
        if (!PhiraRespackManager.errors().isEmpty()) {
            message = "资源包导入失败：" + PhiraRespackManager.errors().get(0);
        } else if (pack == null) {
            message = PhiraRespackManager.isEmpty()
                    ? "未找到 Phira 资源包：请把资源包放进 config/baeffect/phira_resourcepacks"
                    : "请选择一个 Phira 资源包";
        } else if (!pack.ready) {
            message = "正在加载资源包：" + pack.name + "（"
                    + Math.round(progress * 100.0D) + "%）" + PhiraRespackManager.progressText();
        } else {
            message = "资源包已就绪：" + pack.name
                    + "（" + pack.hitFxColumns + "x" + pack.hitFxRows + " 帧，共 "
                    + pack.frameCount + " 帧已切分）";
        }
        if (!message.equals(lastStatusMessage)) {
            lastStatusMessage = message;
            status.setTextContent(message);
        }
    }

    private static String text(String key) {
        Object raw = Baeffect.value(key);
        if (raw instanceof Number number) return round(number.doubleValue());
        return raw == null ? "" : String.valueOf(raw);
    }

    private static String display(String key) {
        Object raw = Baeffect.value(key);
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.contains("opacity") || lower.contains("alpha")) {
                return Math.round(value * 100) + "%";
            }
            return round(value);
        }
        return raw == null ? "" : String.valueOf(raw);
    }

    private static String round(double value) {
        String text = String.format(Locale.ROOT, "%.3f", value);
        int end = text.length();
        if (text.indexOf('.') >= 0) {
            while (end > 0 && text.charAt(end - 1) == '0') end--;
            if (end > 0 && text.charAt(end - 1) == '.') end--;
            text = text.substring(0, end);
        }
        return text.isEmpty() ? "0" : text;
    }
}