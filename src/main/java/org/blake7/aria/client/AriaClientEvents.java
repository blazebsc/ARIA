package org.blake7.aria.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import org.blake7.aria.Aria;
import org.blake7.aria.AriaStage;
import org.blake7.aria.chat.AriaChatManager;
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
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex == 0) {
                AriaDataComponents.AriaCoreData data = stack.getOrDefault(
                        AriaDataComponents.ARIA_CORE.get(), AriaDataComponents.AriaCoreData.DEFAULT);
                return switch (data.stage()) {
                    case 1 -> 0xFFFF00;
                    case 2 -> 0xFF8800;
                    default -> 0xFF0000;
                };
            }
            return 0xFFFFFF;
        }, Aria.ARIA_CORE.get());
    }

    public static AriaChatManager getChatManager() {
        return chatManager;
    }

    public static void tryStartChat(AriaEntity entity) {
        tryStartChat(entity, null, null);
    }

    public static void tryStartChat(AriaEntity entity, java.util.function.Consumer<String> chatCallback, Runnable onThinking) {
        if (!chatStarted && Minecraft.getInstance().level != null) {
            chatStarted = true;
            String playerName = Minecraft.getInstance().player != null
                    ? Minecraft.getInstance().player.getName().getString() : "";
            chatManager.start(entity, playerName, chatCallback, onThinking);
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
}
