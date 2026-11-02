package com.sighs.baeffect.mixin;

import com.sighs.apricityui.screen.ApricityScreen;
import com.sighs.baeffect.BaeffectClient;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ApricityScreen overrides render without calling super, so the generic
 * Screen mixin never runs for the mod's own HTML settings page.
 */
@Mixin(ApricityScreen.class)
public abstract class ApricityScreenMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void baeffect$renderPre(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                    CallbackInfo ci) {
        BaeffectClient.onScreenRenderPre();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void baeffect$renderPost(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                     CallbackInfo ci) {
        BaeffectClient.onScreenRenderPost();
    }
}
