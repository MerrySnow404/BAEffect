package com.sighs.baeffect.mixin;

import com.sighs.baeffect.BaeffectClient;
import com.sighs.baeffect.fabric.BAEffectSettingsShortcutRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void baeffect$renderPre(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
                                    CallbackInfo ci) {
        BaeffectClient.onScreenRenderPre();
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void baeffect$renderPost(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
                                     CallbackInfo ci) {
        BaeffectClient.onScreenRenderPost();
        BAEffectSettingsShortcutRenderer.render(graphics, (Screen) (Object) this, mouseX, mouseY);
    }
}
