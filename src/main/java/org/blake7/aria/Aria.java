package org.blake7.aria;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.blake7.aria.data.AriaDataComponents;
import org.blake7.aria.entity.AriaEntity;
import org.blake7.aria.item.AriaCoreItem;
import org.slf4j.Logger;

@Mod(Aria.MODID)
public class Aria {
    public static final String MODID = "aria";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<AriaEntity>> ARIA_ENTITY =
            ENTITIES.register("aria", () ->
                    EntityType.Builder.of(AriaEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 0.6F)
                            .clientTrackingRange(10)
                            .build("aria")
            );

    public static final DeferredHolder<Item, AriaCoreItem> ARIA_CORE =
            ITEMS.register("aria_core", () -> new AriaCoreItem(new Item.Properties()));

    public Aria(IEventBus modEventBus, ModContainer modContainer) {
        ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        AriaDataComponents.DATA_COMPONENTS.register(modEventBus);

        modEventBus.addListener(this::onEntityAttributeCreation);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);

        LOGGER.info("ARIA mod loaded");
    }

    public void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ARIA_ENTITY.get(), AriaEntity.createAttributes().build());
    }
}
