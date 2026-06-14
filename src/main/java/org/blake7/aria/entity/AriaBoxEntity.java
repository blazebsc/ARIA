package org.blake7.aria.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.blake7.aria.Aria;
import org.blake7.aria.data.AriaDataComponents;

public class AriaBoxEntity extends PathfinderMob {

    public enum BoxState {
        CLOSED,
        FLOATING,
        OPENING,
        DISAPPEARING
    }

    private static final EntityDataAccessor<Integer> BOX_STATE =
            SynchedEntityData.defineId(AriaBoxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> OWNER_NAME =
            SynchedEntityData.defineId(AriaBoxEntity.class, EntityDataSerializers.STRING);

    private int animationTicks = 0;
    private static final int FLOAT_DURATION = 20;
    private static final int OPEN_DURATION = 15;
    private static final int DISAPPEAR_DURATION = 10;

    private AriaDataComponents.AriaCoreData ariaData;

    public AriaBoxEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.setNoAi(true);
        this.setSilent(true);
        this.noCulling = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BOX_STATE, BoxState.CLOSED.ordinal());
        builder.define(OWNER_NAME, "");
    }

    @Override
    protected void registerGoals() {
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 999.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    public boolean isNoGravity() {
        return this.getBoxState() == BoxState.FLOATING;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return true;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    public BoxState getBoxState() {
        int ordinal = this.entityData.get(BOX_STATE);
        BoxState[] values = BoxState.values();
        if (ordinal >= 0 && ordinal < values.length) {
            return values[ordinal];
        }
        return BoxState.CLOSED;
    }

    public void setBoxState(BoxState state) {
        this.entityData.set(BOX_STATE, state.ordinal());
    }

    public String getOwnerName() {
        return this.entityData.get(OWNER_NAME);
    }

    public void setOwnerName(String name) {
        this.entityData.set(OWNER_NAME, name);
    }

    public void setAriaData(AriaDataComponents.AriaCoreData data) {
        this.ariaData = data;
    }

    public AriaDataComponents.AriaCoreData getAriaData() {
        return this.ariaData;
    }

    public int getAnimationTicks() {
        return this.animationTicks;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (this.getBoxState() != BoxState.CLOSED) {
            return InteractionResult.PASS;
        }

        this.setBoxState(BoxState.FLOATING);
        this.animationTicks = 0;
        this.playSound(SoundEvents.BARREL_OPEN, 1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide()) {
            return;
        }

        BoxState state = getBoxState();
        switch (state) {
            case FLOATING -> {
                animationTicks++;
                this.setDeltaMovement(0, 0.05, 0);
                if (animationTicks >= FLOAT_DURATION) {
                    this.setBoxState(BoxState.OPENING);
                    this.animationTicks = 0;
                    this.playSound(SoundEvents.BARREL_OPEN, 1.0F, 1.2F);
                }
            }
            case OPENING -> {
                animationTicks++;
                this.setDeltaMovement(0, 0.02, 0);
                if (this.level().random.nextInt(3) == 0) {
                    this.level().addParticle(ParticleTypes.CLOUD,
                            this.getX() + (this.level().random.nextDouble() - 0.5) * 0.8,
                            this.getY() + this.getBbHeight(),
                            this.getZ() + (this.level().random.nextDouble() - 0.5) * 0.8,
                            0, 0.05, 0);
                }
                if (animationTicks >= OPEN_DURATION) {
                    this.setBoxState(BoxState.DISAPPEARING);
                    this.animationTicks = 0;
                }
            }
            case DISAPPEARING -> {
                animationTicks++;
                this.setDeltaMovement(0, -0.1, 0);
                if (animationTicks >= DISAPPEAR_DURATION) {
                    spawnAria();
                    this.discard();
                }
            }
            default -> {}
        }
    }

    private void spawnAria() {
        if (this.level().isClientSide()) return;

        AriaEntity aria = new AriaEntity(Aria.ARIA_ENTITY.get(), this.level());
        if (this.ariaData != null) {
            aria.loadFromData(this.ariaData);
        }
        aria.setPos(this.getX(), this.getY() + 1.0, this.getZ());
        aria.setDeltaMovement(0, -0.2, 0);
        this.level().addFreshEntity(aria);

        for (int i = 0; i < 20; i++) {
            this.level().addParticle(ParticleTypes.POOF,
                    this.getX() + (this.level().random.nextDouble() - 0.5) * 1.0,
                    this.getY() + 0.5,
                    this.getZ() + (this.level().random.nextDouble() - 0.5) * 1.0,
                    (this.level().random.nextDouble() - 0.5) * 0.1,
                    this.level().random.nextDouble() * 0.1,
                    (this.level().random.nextDouble() - 0.5) * 0.1);
        }

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.5F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("BoxState", this.entityData.get(BOX_STATE));
        tag.putString("OwnerName", this.entityData.get(OWNER_NAME));
        tag.putInt("AnimationTicks", this.animationTicks);
        if (this.ariaData != null) {
            CompoundTag ariaTag = new CompoundTag();
            ariaTag.putInt("Stage", this.ariaData.stage());
            ariaTag.putInt("DayActivated", this.ariaData.dayActivated());
            ariaTag.putString("OwnerName", this.ariaData.ownerName());
            ariaTag.putInt("FaceState", this.ariaData.faceState());
            net.minecraft.nbt.ListTag historyTag = new net.minecraft.nbt.ListTag();
            for (String entry : this.ariaData.conversationHistory()) {
                historyTag.add(net.minecraft.nbt.StringTag.valueOf(entry));
            }
            ariaTag.put("ConversationHistory", historyTag);
            tag.put("AriaData", ariaTag);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("BoxState")) {
            this.entityData.set(BOX_STATE, tag.getInt("BoxState"));
        }
        if (tag.contains("OwnerName")) {
            this.entityData.set(OWNER_NAME, tag.getString("OwnerName"));
        }
        this.animationTicks = tag.getInt("AnimationTicks");
        if (tag.contains("AriaData")) {
            CompoundTag ariaTag = tag.getCompound("AriaData");
            net.minecraft.nbt.ListTag historyTag = ariaTag.getList("ConversationHistory", 8);
            java.util.List<String> history = new java.util.ArrayList<>();
            for (int i = 0; i < historyTag.size(); i++) {
                history.add(historyTag.getString(i));
            }
            this.ariaData = new AriaDataComponents.AriaCoreData(
                    ariaTag.getInt("Stage"),
                    ariaTag.getInt("DayActivated"),
                    history,
                    ariaTag.getString("OwnerName"),
                    ariaTag.getInt("FaceState")
            );
        }
    }
}
