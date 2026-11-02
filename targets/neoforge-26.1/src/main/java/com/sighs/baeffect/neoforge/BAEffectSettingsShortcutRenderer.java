package com.sighs.baeffect.neoforge;

import com.sighs.baeffect.BAEffectSettingsShortcut;
import com.sighs.baeffect.Baeffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public final class BAEffectSettingsShortcutRenderer {
    private static final Identifier ICON =
            Identifier.fromNamespaceAndPath(Baeffect.MODID, "textures/gui/baeffect_helper.png");

    private BAEffectSettingsShortcutRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics, Screen screen, double mouseX, double mouseY) {
        if (!BAEffectSettingsShortcut.isTargetScreen(screen)) return;

        int x = BAEffectSettingsShortcut.buttonX(screen);
        int y = BAEffectSettingsShortcut.buttonY();
        boolean hovered = BAEffectSettingsShortcut.isHovered(screen, mouseX, mouseY);

        if (hovered) {
            graphics.fill(x - 1, y - 1, x + BAEffectSettingsShortcut.BUTTON_SIZE + 1,
                    y + BAEffectSettingsShortcut.BUTTON_SIZE + 1, BAEffectSettingsShortcut.HOVER_BORDER);
        }

        graphics.blit(ICON, x, y, x + BAEffectSettingsShortcut.BUTTON_SIZE,
                y + BAEffectSettingsShortcut.BUTTON_SIZE, 0.0F, 1.0F, 0.0F, 1.0F);

        if (hovered) {
            graphics.setTooltipForNextFrame(Minecraft.getInstance().font,
                    BAEffectSettingsShortcut.tooltip(), Optional.empty(), (int) mouseX, (int) mouseY);
        }
    }
}
