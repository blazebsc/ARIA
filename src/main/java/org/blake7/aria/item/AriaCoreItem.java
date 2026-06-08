package org.blake7.aria.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.blake7.aria.data.AriaDataComponents;

import java.util.List;

public class AriaCoreItem extends Item {

    public AriaCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);

        if (!level.isClientSide()) {
            AriaDataComponents.AriaCoreData data = stack.getOrDefault(AriaDataComponents.ARIA_CORE.get(),
                    AriaDataComponents.AriaCoreData.DEFAULT);

            if (data.ownerName().isEmpty()) {
                stack.set(AriaDataComponents.ARIA_CORE.get(),
                        data.withOwner(player.getName().getString()));
                player.displayClientMessage(
                        Component.literal("Aria's Core is now bound to " + player.getName().getString()), true);
            }
        }

        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        AriaDataComponents.AriaCoreData data = stack.getOrDefault(AriaDataComponents.ARIA_CORE.get(),
                AriaDataComponents.AriaCoreData.DEFAULT);
        tooltip.add(Component.literal("§eAria's Core"));
        tooltip.add(Component.literal("§7Stage: " + data.stage()));
        if (!data.ownerName().isEmpty()) {
            tooltip.add(Component.literal("§7Owner: " + data.ownerName()));
        }
        tooltip.add(Component.literal("§8Right-click to bind"));
    }
}
