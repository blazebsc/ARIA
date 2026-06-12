package org.blake7.aria.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.blake7.aria.Aria;
import org.blake7.aria.data.AriaDataComponents;
import org.blake7.aria.entity.AriaEntity;

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
            } else {
                AriaEntity aria = new AriaEntity(Aria.ARIA_ENTITY.get(), level);
                aria.loadFromData(data);
                aria.setPos(player.getX(), player.getY() + 0.5, player.getZ());
                level.addFreshEntity(aria);
                stack.shrink(1);
                player.displayClientMessage(Component.literal("Aria: I'm back!"), true);
            }
        }

        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        AriaDataComponents.AriaCoreData data = stack.getOrDefault(AriaDataComponents.ARIA_CORE.get(),
                AriaDataComponents.AriaCoreData.DEFAULT);
        tooltip.add(Component.literal("§8Right-click to place Aria"));
    }
}
