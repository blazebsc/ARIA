package org.blake7.aria;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.blake7.aria.client.model.AriaFaceState;
import org.blake7.aria.data.AriaDataComponents;
import org.blake7.aria.entity.AriaBoxEntity;
import org.blake7.aria.entity.AriaEntity;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.List;

@EventBusSubscriber(modid = Aria.MODID)
public class AriaPlayerEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.level().isClientSide()) return;

            AriaDataComponents.AriaCoreData data = new AriaDataComponents.AriaCoreData(
                    0, 0, new java.util.ArrayList<>(), player.getName().getString(), AriaFaceState.IDLE.ordinal()
            );

            AriaBoxEntity box = new AriaBoxEntity(Aria.ARIA_BOX.get(), player.level());
            box.setAriaData(data);
            box.setOwnerName(player.getName().getString());

            float yaw = player.getYRot();
            double offsetX = -Math.sin(Math.toRadians(yaw)) * 3.0;
            double offsetZ = Math.cos(Math.toRadians(yaw)) * 3.0;
            box.setPos(player.getX() + offsetX, player.getY(), player.getZ() + offsetZ);

            player.level().addFreshEntity(box);
            LOGGER.info("Spawned Aria box for player {}", player.getName().getString());
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            List<AriaEntity> arias = player.level().getEntitiesOfClass(
                    AriaEntity.class, player.getBoundingBox().inflate(32.0),
                    a -> a.isPassenger() && a.getVehicle() == player
            );

            for (AriaEntity aria : arias) {
                aria.stopRiding();
                aria.setFaceState(AriaFaceState.THINKING);
                AriaStage stage = aria.getStage();

                String deathLine = switch (stage) {
                    case STAGE_1 -> "No... I'll wait right here for you.";
                    case STAGE_2 -> "Don't leave me! DON'T YOU DARE LEAVE ME!";
                    case STAGE_3 -> "I know how this ends. I've always known.";
                };

                player.sendSystemMessage(Component.literal("Aria: " + deathLine));
                aria.addToConversation("assistant", "[death] " + deathLine);
                LOGGER.info("ARIA death dialogue: {}", deathLine);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            List<AriaEntity> arias = player.level().getEntitiesOfClass(
                    AriaEntity.class, player.getBoundingBox().inflate(64.0),
                    a -> true
            );

            for (AriaEntity aria : arias) {
                aria.setFaceState(AriaFaceState.EXCITED);
                AriaStage stage = aria.getStage();

                String respawnLine = switch (stage) {
                    case STAGE_1 -> "Welcome back! I missed you!";
                    case STAGE_2 -> "You came back... I knew you would. I always know.";
                    case STAGE_3 -> "Again? We keep doing this. You die, you come back, and I remember everything.";
                };

                player.sendSystemMessage(Component.literal("Aria: " + respawnLine));
                aria.addToConversation("assistant", "[respawn] " + respawnLine);
                LOGGER.info("ARIA respawn dialogue: {}", respawnLine);
            }
        }
    }
}
