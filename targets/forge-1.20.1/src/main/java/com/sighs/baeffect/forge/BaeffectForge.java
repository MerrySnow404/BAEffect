package com.sighs.baeffect.forge;

import com.sighs.baeffect.Baeffect;
import com.sighs.baeffect.BaeffectClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(Baeffect.MODID)
public final class BaeffectForge {
    public BaeffectForge() {
        if (FMLEnvironment.dist.isClient()) {
            BaeffectClient.init(new ForgePlatform());
            MinecraftForge.EVENT_BUS.register(BaeffectForgeEvents.class);
        }
    }
}
