package org.blake7.aria.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.blake7.aria.Aria;
import org.blake7.aria.AriaStage;
import org.blake7.aria.client.model.AriaFaceState;
import org.blake7.aria.entity.AriaEntity;

@EventBusSubscriber(modid = Aria.MODID, value = Dist.CLIENT)
public class AriaClientTick {

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        tickCounter++;
        if (tickCounter % 20 != 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        mc.level.getEntitiesOfClass(AriaEntity.class,
                mc.player.getBoundingBox().inflate(32.0),
                a -> true
        ).forEach(aria -> {
            AriaStage entityStage = aria.getStage();
            AriaStage clientStage = AriaClientEvents.getLastStage();
            if (entityStage != clientStage) {
                AriaClientEvents.updateStage(entityStage);
            }

            if (aria.isSpeaking() && aria.getFaceState() != AriaFaceState.EXCITED) {
                aria.setFaceState(AriaFaceState.EXCITED);
            }
        });
    }
}
