package com.sighs.baeffect;

import net.minecraft.client.gui.screens.Screen;

import java.nio.file.Path;

/**
 * Loader/version adapter surface. Everything that genuinely differs between
 * targets is expressed here; all other client logic stays in common.
 */
public interface BaeffectPlatform {
    Path configDir();

    Path gameDir();

    /** The GLFW window handle. */
    long windowHandle();

    /** Whether the given screen is the vanilla mouse-settings page. */
    boolean isSettingsScreen(Screen screen);
}
