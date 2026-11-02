package com.sighs.baeffect.neoforge;

import com.sighs.baeffect.Baeffect;
import com.sighs.baeffect.BaeffectClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = Baeffect.MODID, value = Dist.CLIENT)
public final class BaeffectNeoForgeEvents {
    private BaeffectNeoForgeEvents() {
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getAction() == InputConstants.PRESS) {
            BaeffectClient.onMouseButton(event.getButton(), true);
        } else if (event.getAction() == InputConstants.RELEASE) {
            BaeffectClient.onMouseButton(event.getButton(), false);
        }
    }

    @SubscribeEvent
    public static void onTickPre(ClientTickEvent.Pre event) {
        BaeffectClient.onClientTickStart();
    }

    @SubscribeEvent
    public static void onTickPost(ClientTickEvent.Post event) {
        BaeffectClient.onClientTickEnd();
    }

    @SubscribeEvent
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        BaeffectClient.onScreenRenderPre();
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        BaeffectClient.onScreenRenderPost();
        BAEffectSettingsShortcutRenderer.render(
                event.getGuiGraphics(), event.getScreen(), event.getMouseX(), event.getMouseY());
    }

    @SubscribeEvent
    public static void onTooltip(RenderTooltipEvent.Pre event) {
        BaeffectClient.onTooltip(!event.getItemStack().isEmpty());
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (BaeffectClient.onScreenMousePressed(
                event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }
}
