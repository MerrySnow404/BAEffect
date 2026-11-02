package com.sighs.baeffect.neoforge;

import com.sighs.baeffect.Baeffect;
import com.sighs.baeffect.BaeffectClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(Baeffect.MODID)
public final class BaeffectNeoForge121 {
    public BaeffectNeoForge121() {
        if (FMLEnvironment.dist.isClient()) {
            BaeffectClient.init(new NeoForgePlatform());
        }
    }
}
