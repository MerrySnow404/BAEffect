package com.sighs.baeffect.fabric;

import com.sighs.baeffect.BaeffectPlatform;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.MouseSettingsScreen;

import java.nio.file.Path;

public final class FabricPlatform implements BaeffectPlatform {
    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public long windowHandle() {
        return Minecraft.getInstance().getWindow().handle();
    }

    @Override
    public boolean isSettingsScreen(Screen screen) {
        return screen instanceof MouseSettingsScreen;
    }
}
