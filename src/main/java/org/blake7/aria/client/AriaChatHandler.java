package org.blake7.aria.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import org.blake7.aria.Aria;
import org.blake7.aria.Config;
import org.blake7.aria.entity.AriaEntity;

@EventBusSubscriber(modid = Aria.MODID, value = Dist.CLIENT)
public class AriaChatHandler {

    @SubscribeEvent
    public static void onClientChat(ClientChatEvent event) {
        if (!Config.CLIENT.enableTextChat.get()) return;

        String message = event.getMessage();
        if (message == null || message.isBlank()) return;

        if (message.startsWith("/")) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        AriaEntity aria = findNearestAria(mc);
        if (aria == null) return;

        AriaClientEvents.tryStartChat(aria, AriaChatHandler::sendAriaChat, () -> {
            if (aria != null) {
                aria.setFaceState(org.blake7.aria.client.model.AriaFaceState.THINKING);
            }
        });

        var chatManager = AriaClientEvents.getChatManager();
        if (!chatManager.isRunning()) return;

        chatManager.processTextMessage(message, response -> {
            sendAriaChat(response);
        });
    }

    private static AriaEntity findNearestAria(Minecraft mc) {
        if (mc.level == null || mc.player == null) return null;

        var entities = mc.level.getEntitiesOfClass(AriaEntity.class,
                mc.player.getBoundingBox().inflate(32.0),
                a -> true);

        return entities.isEmpty() ? null : entities.get(0);
    }

    private static void sendAriaChat(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Component component = Component.literal("Aria: " + message);
        mc.player.sendSystemMessage(component);
    }
}
