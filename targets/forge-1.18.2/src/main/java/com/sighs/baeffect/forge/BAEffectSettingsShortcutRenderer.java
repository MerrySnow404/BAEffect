package com.sighs.baeffect.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.baeffect.BAEffectSettingsShortcut;
import com.sighs.baeffect.Baeffect;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public final class BAEffectSettingsShortcutRenderer {
    private static final ResourceLocation ICON =
            new ResourceLocation(Baeffect.MODID, "textures/gui/baeffect_helper.png");

    private BAEffectSettingsShortcutRenderer() {
    }

    public static void render(PoseStack poseStack, Screen screen, double mouseX, double mouseY) {
        if (!BAEffectSettingsShortcut.isTargetScreen(screen)) return;

        int x = BAEffectSettingsShortcut.buttonX(screen);
        int y = BAEffectSettingsShortcut.buttonY();
        boolean hovered = BAEffectSettingsShortcut.isHovered(screen, mouseX, mouseY);

        if (hovered) {
            GuiComponent.fill(poseStack, x - 1, y - 1, x + BAEffectSettingsShortcut.BUTTON_SIZE + 1,
                    y + BAEffectSettingsShortcut.BUTTON_SIZE + 1, BAEffectSettingsShortcut.HOVER_BORDER);
        }

        GuiComponent.blit(poseStack, x, y,
                BAEffectSettingsShortcut.BUTTON_SIZE, BAEffectSettingsShortcut.BUTTON_SIZE,
                0.0F, 0.0F,
                BAEffectSettingsShortcut.ICON_SIZE, BAEffectSettingsShortcut.ICON_SIZE,
                BAEffectSettingsShortcut.ICON_SIZE, BAEffectSettingsShortcut.ICON_SIZE);

        if (hovered) {
            screen.renderTooltip(poseStack, BAEffectSettingsShortcut.tooltip(), Optional.empty(),
                    (int) mouseX, (int) mouseY);
        }
    }
}
