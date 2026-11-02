package com.sighs.baeffect.fabric;

import com.sighs.baeffect.BaeffectClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

public final class BaeffectFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BaeffectClient.init(new FabricPlatform());

        ClientTickEvents.START_CLIENT_TICK.register(client -> BaeffectClient.onClientTickStart());
        ClientTickEvents.END_CLIENT_TICK.register(client -> BaeffectClient.onClientTickEnd());

        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipFlag, lines) ->
                BaeffectClient.onTooltip(!stack.isEmpty()));
    }
}
