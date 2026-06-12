package org.blake7.aria.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.blake7.aria.Aria;
import org.blake7.aria.client.model.AriaFaceState;
import org.blake7.aria.data.AriaDataComponents;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.Map;

@EventBusSubscriber(modid = Aria.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class AriaHudOverlay {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<AriaFaceState, ResourceLocation> HUD_TEXTURES = new EnumMap<>(AriaFaceState.class);

    static {
        HUD_TEXTURES.put(AriaFaceState.IDLE, ResourceLocation.fromNamespaceAndPath(Aria.MODID, "textures/gui/idle.png"));
        HUD_TEXTURES.put(AriaFaceState.EXCITED, ResourceLocation.fromNamespaceAndPath(Aria.MODID, "textures/gui/excited.png"));
        HUD_TEXTURES.put(AriaFaceState.THINKING, ResourceLocation.fromNamespaceAndPath(Aria.MODID, "textures/gui/thinking.png"));
        HUD_TEXTURES.put(AriaFaceState.LISTENING, ResourceLocation.fromNamespaceAndPath(Aria.MODID, "textures/gui/listening.png"));
        HUD_TEXTURES.put(AriaFaceState.UNSETTLING, ResourceLocation.fromNamespaceAndPath(Aria.MODID, "textures/gui/unsettling.png"));
        HUD_TEXTURES.put(AriaFaceState.NO_MOUTH, ResourceLocation.fromNamespaceAndPath(Aria.MODID, "textures/gui/no_mouth.png"));
        HUD_TEXTURES.put(AriaFaceState.STARING, ResourceLocation.fromNamespaceAndPath(Aria.MODID, "textures/gui/staring.png"));
        HUD_TEXTURES.put(AriaFaceState.DISTURBING, ResourceLocation.fromNamespaceAndPath(Aria.MODID, "textures/gui/disturbing.png"));
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.options.hideGui) return;

        Player player = mc.player;
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        ItemStack coreStack = null;
        if (isAriaCore(mainHand)) coreStack = mainHand;
        else if (isAriaCore(offHand)) coreStack = offHand;
        if (coreStack == null) return;

        AriaDataComponents.AriaCoreData data = coreStack.getOrDefault(
                AriaDataComponents.ARIA_CORE.get(), AriaDataComponents.AriaCoreData.DEFAULT);

        int faceOrdinal = data.faceState();
        AriaFaceState[] values = AriaFaceState.values();
        AriaFaceState face = (faceOrdinal >= 0 && faceOrdinal < values.length)
                ? values[faceOrdinal] : AriaFaceState.IDLE;

        ResourceLocation tex = HUD_TEXTURES.getOrDefault(face, HUD_TEXTURES.get(AriaFaceState.IDLE));

        GuiGraphics gui = event.getGuiGraphics();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        int size = 64;
        int margin = 4;
        int x = sw - margin - size;
        int y = sh - margin - size;

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.9F);
        gui.blit(tex, x, y, 0, 0, size, size, size, size);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static boolean isAriaCore(ItemStack stack) {
        return stack.is(Aria.ARIA_CORE.get());
    }
}
