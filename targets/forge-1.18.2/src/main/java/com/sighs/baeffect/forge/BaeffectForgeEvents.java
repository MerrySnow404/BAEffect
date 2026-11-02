package com.sighs.baeffect.forge;

import com.mojang.blaze3d.platform.InputConstants;
import com.sighs.baeffect.BaeffectClient;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class BaeffectForgeEvents {
    private BaeffectForgeEvents() {
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseInputEvent event) {
        if (event.getAction() == InputConstants.PRESS) {
            BaeffectClient.onMouseButton(event.getButton(), true);
        } else if (event.getAction() == InputConstants.RELEASE) {
            BaeffectClient.onMouseButton(event.getButton(), false);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            BaeffectClient.onClientTickStart();
        } else {
            BaeffectClient.onClientTickEnd();
        }
    }

    @SubscribeEvent
    public static void onScreenRenderPre(ScreenEvent.DrawScreenEvent.Pre event) {
        BaeffectClient.onScreenRenderPre();
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.DrawScreenEvent.Post event) {
        BaeffectClient.onScreenRenderPost();
        BAEffectSettingsShortcutRenderer.render(
                event.getPoseStack(), event.getScreen(), event.getMouseX(), event.getMouseY());
    }

    @SubscribeEvent
    public static void onTooltip(RenderTooltipEvent.Pre event) {
        BaeffectClient.onTooltip(!event.getItemStack().isEmpty());
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseClickedEvent.Pre event) {
        if (BaeffectClient.onScreenMousePressed(
                event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }
}
