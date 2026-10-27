package com.sighs.baeffect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MouseSettingsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Optional;

/**
 * 在「选项 → 按键控制 → 鼠标设置」页面右上角绘制一个悬浮图标，
 * 点击后打开本模组的 HTML 设置页。
 *
 * <p>使用 Render.Post：在原生控件之后绘制，图标层级高于背景与原生控件，
 * 鼠标光标仍由 MC 在最后统一绘制，因此不会被图标遮挡。</p>
 */
@Mod.EventBusSubscriber(modid = Baeffect.MODID, value = Dist.CLIENT)
public final class BAEffectSettingsShortcut {
    private static final ResourceLocation ICON =
            new ResourceLocation(Baeffect.MODID, "textures/gui/baeffect_helper.png");
    private static final int ICON_SIZE = 192;
    private static final int BUTTON_SIZE = 16;
    private static final int MARGIN = 6;
    private static final int HOVER_BORDER = 0x88FFFFFF;

    private static final List<Component> TOOLTIP = List.of(
            Component.literal("BAEffect 设置"),
            Component.literal("点击打开点击特效与光标设置"));

    private BAEffectSettingsShortcut() {
    }

    private static boolean isTargetScreen(Screen screen) {
        return screen instanceof MouseSettingsScreen;
    }

    private static int buttonX(Screen screen) {
        return Math.max(MARGIN, screen.width - BUTTON_SIZE - MARGIN);
    }

    private static int buttonY() {
        return MARGIN + 4;
    }

    static boolean isHovered(Screen screen, double mouseX, double mouseY) {
        if (!isTargetScreen(screen)) return false;
        int x = buttonX(screen);
        int y = buttonY();
        return mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE;
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!isTargetScreen(screen)) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int x = buttonX(screen);
        int y = buttonY();
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        boolean hovered = isHovered(screen, mouseX, mouseY);

        if (hovered) {
            graphics.fill(x - 1, y - 1, x + BUTTON_SIZE + 1, y + BUTTON_SIZE + 1, HOVER_BORDER);
        }

        // 目标 16x16，源图 192x192；整张图缩放到目标区域。
        graphics.blit(ICON, x, y, BUTTON_SIZE, BUTTON_SIZE,
                0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

        if (hovered) {
            graphics.renderTooltip(Minecraft.getInstance().font, TOOLTIP, Optional.empty(),
                    (int) mouseX, (int) mouseY);
        }
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0) return;
        if (!isHovered(event.getScreen(), event.getMouseX(), event.getMouseY())) return;

        event.setCanceled(true);
        BAClickFxHtmlSettings.open();
    }
}