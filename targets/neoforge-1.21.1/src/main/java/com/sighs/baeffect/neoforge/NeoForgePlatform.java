package com.sighs.baeffect.neoforge;

import com.sighs.baeffect.BaeffectPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.MouseSettingsScreen;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class NeoForgePlatform implements BaeffectPlatform {
    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public long windowHandle() {
        return Minecraft.getInstance().getWindow().getWindow();
    }

    @Override
    public boolean isSettingsScreen(Screen screen) {
        return screen instanceof MouseSettingsScreen;
    }
}
