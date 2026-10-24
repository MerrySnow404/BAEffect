package com.sighs.baeffect;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
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
    private static int pushedRevision = -1;
    private static boolean loggedElements;
    private static String colorPickerOpeningKey = null;
    private static String lastPhiraSignature = null;

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

    private static void openOnClient() {
        closeOnClient();
        Minecraft minecraft = Minecraft.getInstance();

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
        if (hostScreen != null && minecraft.screen == hostScreen) {
            minecraft.setScreen(null);
        }
        hostScreen = null;
        pushedRevision = -1;
        loggedElements = false;
        colorPickerOpeningKey = null;
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
                    if (!page.equals(Baeffect.text(key))) {
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

    private static void bindPageEvents(Document document) {
        if (eventDocument == document) return;
        eventDocument = document;

        bindButton(document, "fx-save", "save");
        bindButton(document, "fx-reset", "reset");
        bindButton(document, "fx-close", "close");

        List<Element> controls = query(document, "[data-key]");
        for (Element element : controls) {
            String key = element.getAttribute("data-key");
            if (key == null || key.isEmpty()) continue;

            if ("checkbox".equalsIgnoreCase(element.getAttribute("type"))) {
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

    @SubscribeEvent
    public static void onMousePress(net.minecraftforge.client.event.ScreenEvent.MouseButtonPressed.Pre event) {
        if (hostScreen == null || event.getScreen() != hostScreen || event.getButton() != 0) return;
        Document document = document();
        if (document == null) return;

        double scale = renderScale(document);
        double x = event.getMouseX() / Math.max(0.0001D, scale);
        double y = event.getMouseY() / Math.max(0.0001D, scale);

        List<Element> controls = query(document, "[data-key]");

        for (Element element : controls) {
            if (!hitsControl(element, x, y)) continue;
            String key = element.getAttribute("data-key");
            if (key == null || key.isEmpty()) continue;

            String type = element.getAttribute("type");

            if ("checkbox".equalsIgnoreCase(type)) {
                boolean value = !Baeffect.flag(key);
                Baeffect.applyPatch(Map.<String, Object>of(key, value));
                syncChecked(element, value);
                LAST_SEEN_CHECKED.put(key, value);
                pushedRevision = -1;
                event.setCanceled(true);
                return;
            }
            if ("text".equalsIgnoreCase(type)) continue;
            if ("1".equals(element.getAttribute("data-color-swatch"))) continue;

            if (isSelect(element)) {
                List<String> values = optionValues(element);
                if (values.isEmpty()) continue;
                String previous = element.getValue();
                int index = values.indexOf(previous);
                if (index < 0) index = 0;
                int delta = x < centerX(element) ? -1 : 1;
                int next = Math.floorMod(index + delta, values.size());
                String value = values.get(next);
                element.setValue(value);//re
                if ("preset".equals(key)) {
                    Baeffect.applyPreset(value);
                } else {
                    Baeffect.applyPatch(Map.<String, Object>of(key, value));
                }
                pushedRevision = -1;
                event.setCanceled(true);
                return;
            }

            if ("range".equalsIgnoreCase(type)) continue;

            double step = doubleAttribute(element, "step", 1.0D);
            double delta = step * (x < centerX(element) ? -1 : 1);
            double value = Baeffect.num(key) + delta;
            double min = doubleAttribute(element, "min", Double.NEGATIVE_INFINITY);
            double max = doubleAttribute(element, "max", Double.POSITIVE_INFINITY);
            value = Math.max(min, Math.min(max, value));
            element.setValue(round(value));
            Baeffect.applyPatch(Map.<String, Object>of(key, value));
            pushedRevision = -1;
            event.setCanceled(true);
            return;
        }
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
        return "width:46px;height:18px;padding:0;border:1px solid #9fc4dd;"
                + "border-radius:3px;background:" + color + ";cursor:pointer;";
    }

    private static List<String> optionValues(Element select) {
        List<String> values = new ArrayList<>();
        for (Element child : select.children) {
            String value = child.getAttribute("value");
            if (value != null && !value.isEmpty()) values.add(value);
        }
        return values;
    }

    private static void setStatus(Document document, String text) {
        Element status = document.getElementById("fx-status");
        if (status != null) status.setTextContent(text);
    }

    private static boolean hitsControl(Element element, double x, double y) {
        if (inRect(element, x, y)) return true;
        Element row = element.parentElement;
        for (int guard = 0; row != null && guard < 3; guard++) {
            double[] parentRect = rect(row);
            if (parentRect != null && parentRect[2] > 1.0D && parentRect[3] > 1.0D) {
                return x >= parentRect[0] && y >= parentRect[1]
                        && x <= parentRect[0] + parentRect[2] && y <= parentRect[1] + parentRect[3];
            }
            row = row.parentElement;
        }
        return false;
    }

    private static double renderScale(Document document) {
        try {
            Object viewport = document.getClass().getMethod("getViewport").invoke(document);
            if (viewport == null) return 1.0D;
            Object scale = viewport.getClass().getMethod("renderScale").invoke(viewport);
            return scale instanceof Number number ? number.doubleValue() : 1.0D;
        } catch (Throwable ignored) {
            return 1.0D;
        }
    }

    private static boolean inRect(Element element, double x, double y) {
        double[] rect = rect(element);
        return rect != null && x >= rect[0] && y >= rect[1] && x <= rect[0] + rect[2] && y <= rect[1] + rect[3];
    }

    private static double centerX(Element element) {
        double[] rect = rect(element);
        return rect == null ? 0.0D : rect[0] + rect[2] / 2.0D;
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

    private static double number(Class<?> type, Object target, String name) throws Exception {
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

    private static void syncChecked(Element element, boolean value) {
        if (value) {
            element.setAttribute("checked", "checked");
        } else {
            element.removeAttribute("checked");
        }
    }

    private static void push(Document document, List<Element> controls) {
        for (Element element : controls) {
            String key = element.getAttribute("data-key");
            if (key == null || key.isEmpty()) continue;

            Object raw = Baeffect.value(key);
            boolean isSelect = isSelect(element);

            if ("checkbox".equalsIgnoreCase(element.getAttribute("type"))) {
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
                    + Math.round(Math.max(0.0D, Math.min(1.0D, progress)) * 100.0D) + "%;height:100%;"
                    + "background-color:#4ca7ff;");
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
            if (key.contains("opacity") || key.contains("Alpha")) return Math.round(value * 100) + "%";
            return round(value);
        }
        return raw == null ? "" : String.valueOf(raw);
    }

    private static String round(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}