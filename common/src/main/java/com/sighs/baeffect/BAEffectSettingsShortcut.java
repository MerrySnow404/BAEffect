package com.sighs.baeffect;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Geometry and click handling for the shortcut drawn on the vanilla
 * mouse-settings page. The actual drawing is version specific (PoseStack,
 * GuiGraphics or GuiGraphicsExtractor) and lives in each target.
 */
public final class BAEffectSettingsShortcut {
    public static final int ICON_SIZE = 192;
    public static final int BUTTON_SIZE = 16;
    public static final int MARGIN = 6;
    public static final int HOVER_BORDER = 0x88FFFFFF;

    private static final List<Component> TOOLTIP = List.of(
            Component.nullToEmpty("BAEffect 设置"),
            Component.nullToEmpty("点击打开点击特效与光标设置"));

    private BAEffectSettingsShortcut() {
    }

    public static boolean isTargetScreen(Screen screen) {
        return screen != null && Baeffect.platform().isSettingsScreen(screen);
    }

    public static int buttonX(Screen screen) {
        return Math.max(MARGIN, screen.width - BUTTON_SIZE - MARGIN);
    }

    public static int buttonY() {
        return MARGIN + 4;
    }

    public static boolean isHovered(Screen screen, double mouseX, double mouseY) {
        if (!isTargetScreen(screen)) return false;
        int x = buttonX(screen);
        int y = buttonY();
        return mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE;
    }

    public static List<Component> tooltip() {
        return TOOLTIP;
    }

    public static boolean onMousePressed(Screen screen, double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (!isHovered(screen, mouseX, mouseY)) return false;
        BAClickFxHtmlSettings.open();
        return true;
    }
}
