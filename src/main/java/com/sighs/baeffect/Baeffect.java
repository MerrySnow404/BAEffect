package com.sighs.baeffect;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(Baeffect.MODID)
public class Baeffect {
    public static final String MODID = "baeffect";

    public Baeffect() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
