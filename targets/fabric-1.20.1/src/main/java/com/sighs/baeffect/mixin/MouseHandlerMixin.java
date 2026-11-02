package com.sighs.baeffect.mixin;

import com.sighs.apricityui.client.Client;
import com.sighs.apricityui.layout.Position;
import com.sighs.baeffect.BaeffectClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onPress", at = @At("HEAD"))
    private void baeffect$onPress(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS) {
            BaeffectClient.onMouseButton(button, true);
            Screen screen = Minecraft.getInstance().screen;
            if (screen != null) {
                Position mouse = Client.getMousePositionDirectly();
                if (mouse != null) {
                    BaeffectClient.onScreenMousePressed(screen, mouse.x, mouse.y, button);
                }
            }
        } else if (action == GLFW.GLFW_RELEASE) {
            BaeffectClient.onMouseButton(button, false);
        }
    }
}
