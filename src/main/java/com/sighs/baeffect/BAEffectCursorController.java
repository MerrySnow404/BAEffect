package com.sighs.baeffect;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BAEffectCursorController {
    public static final String DOCUMENT_PATH = "baeffect/cursor-overlay.html";
    public static final String LAYER_ID = "baeffect-cursor-layer";
    private static final String BASE_CLASS = "cursor-layer";

    private BAEffectCursorController() {
    }

    public static Document show() {
        return show(CursorStyle.NORMAL);
    }

    public static Document show(CursorStyle style) {
        Document existing = getDocument();
        if (existing != null) {
            existing.remove();
        }

        Document document = Document.create(DOCUMENT_PATH);
        if (document == null) return null;

        document.setReloadPersistent(true);
        applyCursorStyle(document, style);
        return document;
    }

    public static void hide() {
        clearApricityPseudoCursor();
        ArrayList<Document> docs = Document.get(DOCUMENT_PATH);
        for (Document document : docs) {
            if (document != null) {
                applyCursorStyle(document, CursorStyle.HIDDEN);
                document.setReloadPersistent(false);
                document.remove();
            }
        }
    }

    public static boolean isShowing() {
        return getDocument() != null;
    }

    public static Document ensure() {
        Document document = getDocument();
        if (document != null) return document;
        return show(CursorStyle.NORMAL);
    }

    public static void setCursorStyle(CursorStyle style) {
        Document document = ensure();
        if (document == null) return;
        applyCursorStyle(document, style);
    }

    public static void setCursorClass(String className) {
        Document document = ensure();
        if (document == null) return;

        Element layer = document.getElementById(LAYER_ID);
        if (layer == null) layer = document.querySelector("#" + LAYER_ID);
        if (layer == null) return;

        String normalized = normalizeClassName(className);
        layer.setAttribute("class", BASE_CLASS + (normalized.isBlank() ? "" : " " + normalized));
    }

    public static CursorStyle getCursorStyle() {
        Document document = getDocument();
        if (document == null) return CursorStyle.NORMAL;

        Element layer = document.getElementById(LAYER_ID);
        if (layer == null) layer = document.querySelector("#" + LAYER_ID);
        if (layer == null) return CursorStyle.NORMAL;

        String classAttr = layer.getAttribute("class");
        for (CursorStyle style : CursorStyle.values()) {
            if (classAttr.contains(style.className)) return style;
        }
        return CursorStyle.NORMAL;
    }

    public static List<String> getSupportedClasses() {
        return List.of(
                CursorStyle.NORMAL.className,
                CursorStyle.HELP.className,
                CursorStyle.LOADING.className,
                CursorStyle.BACKGROUND.className,
                CursorStyle.LINK.className,
                CursorStyle.MOVE.className,
                CursorStyle.TEXT.className,
                CursorStyle.PEN.className,
                CursorStyle.BLOCK.className,
                CursorStyle.AREA_SELECT.className,
                CursorStyle.ALTERNATE_SELECT.className,
                CursorStyle.RESIZE_NS.className,
                CursorStyle.RESIZE_WE.className,
                CursorStyle.RESIZE_DIAG1.className,
                CursorStyle.RESIZE_DIAG2.className,
                CursorStyle.HIDDEN.className
        );
    }

    private static void applyCursorStyle(Document document, CursorStyle style) {
        if (document == null) return;
        Element layer = document.getElementById(LAYER_ID);
        if (layer == null) layer = document.querySelector("#" + LAYER_ID);
        if (layer == null) return;

        CursorStyle safeStyle = style == null ? CursorStyle.NORMAL : style;
        String nextClass = BASE_CLASS + " " + safeStyle.className;
        String currentClass = layer.getAttribute("class");
        if (nextClass.equals(currentClass)) return;
        layer.setAttribute("class", nextClass);
    }

    private static String normalizeClassName(String className) {
        if (className == null) return "";
        String normalized = className.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return "";
        if (!normalized.startsWith("cursor-")) {
            normalized = "cursor-" + normalized;
        }
        return normalized;
    }

    private static Document getDocument() {
        ArrayList<Document> docs = Document.get(DOCUMENT_PATH);
        if (docs.isEmpty()) return null;
        return docs.get(docs.size() - 1);
    }

    private static void clearApricityPseudoCursor() {
        try {
            Class<?> cursorClass = Class.forName("com.sighs.apricityui.style.Cursor");
            Field pseudoCursorSpec = cursorClass.getDeclaredField("pseudoCursorSpec");
            pseudoCursorSpec.setAccessible(true);
            pseudoCursorSpec.set(null, null);

            Field systemCursorHidden = cursorClass.getDeclaredField("systemCursorHidden");
            systemCursorHidden.setAccessible(true);
            systemCursorHidden.setBoolean(null, false);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    public enum CursorStyle {
        NORMAL("cursor-normal"),
        HELP("cursor-help"),
        LOADING("cursor-loading"),
        BACKGROUND("cursor-background"),
        LINK("cursor-link"),
        MOVE("cursor-move"),
        TEXT("cursor-text"),
        PEN("cursor-pen"),
        BLOCK("cursor-block"),
        AREA_SELECT("cursor-area-select"),
        ALTERNATE_SELECT("cursor-alternate-select"),
        RESIZE_NS("cursor-resize-ns"),
        RESIZE_WE("cursor-resize-we"),
        RESIZE_DIAG1("cursor-resize-diag1"),
        RESIZE_DIAG2("cursor-resize-diag2"),
        HIDDEN("cursor-hidden");

        private final String className;

        CursorStyle(String className) {
            this.className = className;
        }

        public String className() {
            return className;
        }
    }
}
