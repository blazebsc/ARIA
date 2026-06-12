package org.blake7.aria.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import org.blake7.aria.Aria;
import org.blake7.aria.Config;
import org.blake7.aria.data.AriaDataComponents;
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
        if (aria != null) {
            AriaClientEvents.tryStartChat(aria, AriaChatHandler::sendAriaChat, () -> aria.setFaceState(org.blake7.aria.client.model.AriaFaceState.THINKING));
            var chatManager = AriaClientEvents.getChatManager();
            chatManager.setChatCallback(AriaChatHandler::sendAriaChat);
            if (!chatManager.isRunning()) return;
            chatManager.processTextMessage(message, AriaChatHandler::sendAriaChat);
            return;
        }

        ItemStack coreStack = findAriaCoreInInventory(mc);
        if (coreStack != null) {
            AriaDataComponents.AriaCoreData data = coreStack.getOrDefault(
                    AriaDataComponents.ARIA_CORE.get(), AriaDataComponents.AriaCoreData.DEFAULT);
            var chatManager = AriaClientEvents.getChatManager();
            chatManager.processTextMessageWithHistory(message, data, coreStack, AriaChatHandler::sendAriaChat);
        }
    }

    private static AriaEntity findNearestAria(Minecraft mc) {
        if (mc.level == null || mc.player == null) return null;
        var entities = mc.level.getEntitiesOfClass(AriaEntity.class,
                mc.player.getBoundingBox().inflate(32.0), a -> true);
        return entities.isEmpty() ? null : entities.get(0);
    }

    private static ItemStack findAriaCoreInInventory(Minecraft mc) {
        if (mc.player == null) return null;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Aria.ARIA_CORE.get())) {
                AriaDataComponents.AriaCoreData data = stack.getOrDefault(
                        AriaDataComponents.ARIA_CORE.get(), AriaDataComponents.AriaCoreData.DEFAULT);
                if (!data.ownerName().isEmpty() && data.ownerName().equals(mc.player.getName().getString())) {
                    return stack;
                }
            }
        }
        return null;
    }

    private static void sendAriaChat(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("Aria: " + message));
    }
}
