package org.blake7.aria.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.blake7.aria.Aria;
import org.blake7.aria.AriaStage;
import org.blake7.aria.chat.AriaChatManager;
import org.blake7.aria.client.renderer.AriaBoxRenderer;
import org.blake7.aria.client.renderer.AriaRenderer;
import org.blake7.aria.data.AriaDataComponents;
import org.blake7.aria.entity.AriaEntity;

@EventBusSubscriber(modid = Aria.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class AriaClientEvents {

    private static final AriaChatManager chatManager = new AriaChatManager();
    private static boolean chatStarted = false;
    private static AriaStage lastStage = AriaStage.STAGE_1;

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Aria.ARIA_ENTITY.get(), AriaRenderer::new);
        event.registerEntityRenderer(Aria.ARIA_BOX.get(), AriaBoxRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex == 0) {
                return 0xFFFFFF00; // Always yellow
            }
            return 0xFFFFFFFF;
        }, Aria.ARIA_CORE.get());
    }

    public static AriaChatManager getChatManager() {
        return chatManager;
    }

    public static void tryStartChat(AriaEntity entity) {
        tryStartChat(entity, AriaChatHandler::sendAriaChat, AriaChatHandler::sendPlayerChat, null);
    }

    public static void tryStartChat(AriaEntity entity, java.util.function.Consumer<String> chatCallback, Runnable onThinking) {
        tryStartChat(entity, chatCallback, chatCallback, onThinking);
    }

    public static void tryStartChat(AriaEntity entity, java.util.function.Consumer<String> chatCallback,
                                     java.util.function.Consumer<String> playerChatCallback, Runnable onThinking) {
        if (!chatStarted && Minecraft.getInstance().level != null) {
            chatStarted = true;
            String playerName = Minecraft.getInstance().player != null
                    ? Minecraft.getInstance().player.getName().getString() : "";
            chatManager.start(entity, playerName, chatCallback, onThinking);
            if (playerChatCallback != null) {
                chatManager.setPlayerChatCallback(playerChatCallback);
            }
        }
    }

    public static void updateStage(AriaStage newStage) {
        if (newStage != lastStage) {
            lastStage = newStage;
            chatManager.setStage(newStage);
        }
    }

    public static AriaStage getLastStage() {
        return lastStage;
    }

    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        if (Minecraft.getInstance().level != null && event.getLevel() == Minecraft.getInstance().level) {
            chatStarted = false;
        }
    }

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        tickCounter++;
        if (tickCounter % 5 != 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        for (ItemStack stack : mc.player.getInventory().items) {
            if (stack.is(Aria.ARIA_CORE.get())) {
                AriaDataComponents.AriaCoreData data = stack.getOrDefault(
                        AriaDataComponents.ARIA_CORE.get(), AriaDataComponents.AriaCoreData.DEFAULT);
                int faceOrdinal = data.faceState();
                int current = stack.has(DataComponents.CUSTOM_MODEL_DATA)
                        ? stack.get(DataComponents.CUSTOM_MODEL_DATA).value() : -1;
                if (faceOrdinal != current) {
                    stack.set(DataComponents.CUSTOM_MODEL_DATA,
                            new net.minecraft.world.item.component.CustomModelData(faceOrdinal));
                }
            }
        }

        ItemStack offhand = mc.player.getOffhandItem();
        if (offhand.is(Aria.ARIA_CORE.get())) {
            AriaDataComponents.AriaCoreData data = offhand.getOrDefault(
                    AriaDataComponents.ARIA_CORE.get(), AriaDataComponents.AriaCoreData.DEFAULT);
            int faceOrdinal = data.faceState();
            int current = offhand.has(DataComponents.CUSTOM_MODEL_DATA)
                    ? offhand.get(DataComponents.CUSTOM_MODEL_DATA).value() : -1;
            if (faceOrdinal != current) {
                offhand.set(DataComponents.CUSTOM_MODEL_DATA,
                        new net.minecraft.world.item.component.CustomModelData(faceOrdinal));
            }
        }
    }
}
