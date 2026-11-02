package com.sighs.baeffect;

import com.sighs.apricityui.client.Client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

public final class BAEffectCursorDispatcher {
    private static boolean itemTooltipVisibleThisFrame;

    private BAEffectCursorDispatcher() {
    }

    public static void onClientTick() {
        syncCursor();
    }

    public static void onScreenRender() {
        syncCursor();
    }

    public static void onScreenRenderPre() {
        itemTooltipVisibleThisFrame = false;
    }

    public static void onTooltip(boolean visible) {
        itemTooltipVisibleThisFrame = visible;
    }

    private static void syncCursor() {
        Minecraft minecraft = Minecraft.getInstance();

        if (!Baeffect.flag("cursorEnabled")) {
            if (BAEffectCursorController.isShowing()) {
                BAEffectCursorController.hide();
            }
            return;
        }

        Screen screen = minecraft.screen;
        if (screen == null) {
            BAEffectCursorController.hide();
            if (minecraft.level != null && !minecraft.mouseHandler.isMouseGrabbed()) {
                minecraft.mouseHandler.grabMouse();
            }
            return;
        }

        BAEffectCursorController.ensure();
        BAEffectCursorController.setCursorStyle(resolveCursorStyle(screen));
    }

    private static BAEffectCursorController.CursorStyle resolveCursorStyle(Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && !minecraft.player.containerMenu.getCarried().isEmpty()) {
            return BAEffectCursorController.CursorStyle.MOVE;
        }

        if (itemTooltipVisibleThisFrame) {
            return BAEffectCursorController.CursorStyle.LINK;
        }

        var mouse = Client.getMousePositionDirectly();
        if (mouse != null && BAEffectSettingsShortcut.isHovered(screen, mouse.x, mouse.y)) {
            return BAEffectCursorController.CursorStyle.LINK;
        }

        GuiEventListener hovered = findHovered(screen);
        if (hovered instanceof EditBox) {
            return BAEffectCursorController.CursorStyle.TEXT;
        }
        if (hovered instanceof AbstractButton button) {
            return button.active
                    ? BAEffectCursorController.CursorStyle.LINK
                    : BAEffectCursorController.CursorStyle.BLOCK;
        }
        if (hovered instanceof AbstractWidget widget && !widget.active) {
            return BAEffectCursorController.CursorStyle.BLOCK;
        }

        GuiEventListener focused = screen.getFocused();
        if (focused instanceof EditBox) {
            return BAEffectCursorController.CursorStyle.TEXT;
        }
        if (focused instanceof AbstractButton button) {
            return button.active
                    ? BAEffectCursorController.CursorStyle.ALTERNATE_SELECT
                    : BAEffectCursorController.CursorStyle.BLOCK;
        }
        if (focused instanceof AbstractWidget widget && !widget.active) {
            return BAEffectCursorController.CursorStyle.BLOCK;
        }

        return BAEffectCursorController.CursorStyle.NORMAL;
    }

    private static GuiEventListener findHovered(Screen screen) {
        var mouse = Client.getMousePositionDirectly();
        if (mouse == null) return null;
        return screen.getChildAt(mouse.x, mouse.y).orElse(null);
    }
}
