package com.sighs.baeffect;

import net.minecraft.client.gui.screens.Screen;

/**
 * Loader-neutral client lifecycle. Each target owns the loader event wiring and
 * forwards into these methods.
 */
public final class BaeffectClient {
    private static boolean initialized;

    private BaeffectClient() {
    }

    public static void init(BaeffectPlatform platform) {
        if (initialized) return;
        initialized = true;

        Baeffect.init(platform);
        PhiraRespackManager.ensureScanned();

        if ("phira".equalsIgnoreCase(Baeffect.text("fxStyle"))) {
            String id = Baeffect.text("phiraRespack");
            PhiraRespack pack = PhiraRespackManager.get(id);
            if (pack == null && !PhiraRespackManager.isEmpty()) {
                id = PhiraRespackManager.ids().get(0);
                Baeffect.applyPatch(java.util.Map.of("phiraRespack", id));
                pack = PhiraRespackManager.get(id);
                Baeffect.LOGGER.info("[BAEffect] Loading Phira Pack：{}", pack == null ? "null" : pack.name);
            }
            PhiraRespackManager.load(pack);
        }
    }

    public static void onClientTickStart() {
        BAClickFxOverlay.handleClientTickStart();
    }

    public static void onClientTickEnd() {
        BAClickFxHtmlSettings.onClientTick();
        BAEffectCursorDispatcher.onClientTick();
    }

    public static void onMouseButton(int button, boolean pressed) {
        BAClickFxOverlay.handleMouseButton(button, pressed);
    }

    public static void onScreenRenderPre() {
        BAEffectCursorDispatcher.onScreenRenderPre();
    }

    public static void onScreenRenderPost() {
        BAClickFxOverlay.renderFrame();
        BAEffectCursorDispatcher.onScreenRender();
    }

    /**
     * @return true when the click was consumed and the loader event must be cancelled.
     */
    public static boolean onScreenMousePressed(Screen screen, double mouseX, double mouseY, int button) {
        if (BAClickFxHtmlSettings.onMousePress(screen, mouseX, mouseY, button)) return true;
        return BAEffectSettingsShortcut.onMousePressed(screen, mouseX, mouseY, button);
    }

    public static void onTooltip(boolean visible) {
        BAEffectCursorDispatcher.onTooltip(visible);
    }
}
