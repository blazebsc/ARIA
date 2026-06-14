package org.blake7.aria.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.blake7.aria.Aria;
import org.blake7.aria.AriaStage;
import org.blake7.aria.ai.AriaFollowGoal;
import org.blake7.aria.client.model.AriaFaceState;
import org.blake7.aria.data.AriaDataComponents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AriaEntity extends PathfinderMob {

    private static final EntityDataAccessor<Integer> FACE_STATE =
            SynchedEntityData.defineId(AriaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STAGE_DATA =
            SynchedEntityData.defineId(AriaEntity.class, EntityDataSerializers.INT);

    private static final int MAX_CONVERSATION_HISTORY = 100;
    private final List<String> conversationHistory = new ArrayList<>();
    private boolean isSpeaking = false;
    private int holdDelayTicks = 0;
    private int dayActivated = 0;
    private int horrorEffectTicks = 0;
    private final Random random = new Random();

    public AriaEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.setNoAi(false);
        this.setSilent(true);
        this.noCulling = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FACE_STATE, AriaFaceState.IDLE.ordinal());
        builder.define(STAGE_DATA, AriaStage.STAGE_1.ordinal());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new AriaFollowGoal(this, 0.25D, 16.0F, 4.0F));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 999.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    public boolean isNoGravity() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() != null && source.getEntity() instanceof Player) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected SoundEvent getAmbientSound() { return null; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return null; }

    @Override
    protected SoundEvent getDeathSound() { return null; }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (this.isPassenger()) {
            this.stopRiding();
        }

        ItemStack ariaStack = this.createItemStack(player);

        if (!player.getInventory().add(ariaStack)) {
            player.drop(ariaStack, false);
        }

        this.discard();
        return InteractionResult.SUCCESS;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide()) {
            if (this.holdDelayTicks > 0) {
                this.holdDelayTicks--;
            }

            if (this.dayActivated == 0) {
                this.dayActivated = (int) (this.level().getDayTime() / 24000L) + 1;
            }

            int currentDay = (int) (this.level().getDayTime() / 24000L) + 1;
            AriaStage newStage = AriaStage.fromDay(currentDay);
            AriaStage currentStage = AriaStage.fromOrdinal(this.entityData.get(STAGE_DATA));
            if (newStage != currentStage) {
                this.entityData.set(STAGE_DATA, newStage.ordinal());
            }

            handleHorrorEffects(currentStage);
        }

        if (this.isPassenger()) {
            net.minecraft.world.entity.Entity vehicle = this.getVehicle();
            if (vehicle instanceof Player player) {
                float sideOffset = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 0.3F : -0.3F;
                double posX = player.getX() + sideOffset * (double) Mth.cos((float) Math.toRadians(player.getYRot()));
                double posZ = player.getZ() + sideOffset * (double) Mth.sin((float) Math.toRadians(player.getYRot()));
                this.setPos(posX, player.getY() + 0.8, posZ);
            }
        }
    }

    private void handleHorrorEffects(AriaStage stage) {
        if (stage != AriaStage.STAGE_3) return;

        horrorEffectTicks++;
        if (horrorEffectTicks >= 200 && random.nextInt(100) == 0) {
            horrorEffectTicks = 0;
            int effect = random.nextInt(6);
            switch (effect) {
                case 0 -> setFaceState(AriaFaceState.STARING);
                case 1 -> setFaceState(AriaFaceState.DISTURBING);
                case 2 -> setFaceState(AriaFaceState.UNSETTLING);
                case 3 -> setFaceState(AriaFaceState.HURT);
                case 4 -> setFaceState(AriaFaceState.MOUTH_OPEN);
                case 5 -> setFaceState(AriaFaceState.DISTURBING_MOUTH_OPEN);
            }
        }
    }

    public void setFaceState(AriaFaceState state) {
        this.entityData.set(FACE_STATE, state.ordinal());
    }

    public AriaFaceState getFaceState() {
        int ordinal = this.entityData.get(FACE_STATE);
        AriaFaceState[] values = AriaFaceState.values();
        if (ordinal >= 0 && ordinal < values.length) {
            return values[ordinal];
        }
        return AriaFaceState.IDLE;
    }

    public AriaStage getStage() {
        return AriaStage.fromOrdinal(this.entityData.get(STAGE_DATA));
    }

    public void setSpeaking(boolean speaking) {
        this.isSpeaking = speaking;
    }

    public boolean isSpeaking() {
        return this.isSpeaking;
    }

    public List<String> getConversationHistory() {
        return this.conversationHistory;
    }

    public void addToConversation(String role, String content) {
        this.conversationHistory.add(role + ": " + content);
        if (this.conversationHistory.size() > MAX_CONVERSATION_HISTORY) {
            this.conversationHistory.subList(0, this.conversationHistory.size() - MAX_CONVERSATION_HISTORY).clear();
        }
    }

    public void setHoldDelay(int ticks) {
        this.holdDelayTicks = ticks;
    }

    public int getDayActivated() {
        return dayActivated;
    }

    public ItemStack createItemStack(Player player) {
        ItemStack stack = new ItemStack(Aria.ARIA_CORE.get());
        AriaDataComponents.AriaCoreData data = new AriaDataComponents.AriaCoreData(
                this.entityData.get(STAGE_DATA),
                this.dayActivated,
                new ArrayList<>(this.conversationHistory),
                player.getName().getString(),
                this.entityData.get(FACE_STATE)
        );
        stack.set(AriaDataComponents.ARIA_CORE.get(), data);
        return stack;
    }

    public void loadFromData(AriaDataComponents.AriaCoreData data) {
        this.entityData.set(STAGE_DATA, data.stage());
        this.dayActivated = data.dayActivated();
        this.entityData.set(FACE_STATE, data.faceState());
        this.conversationHistory.clear();
        this.conversationHistory.addAll(data.conversationHistory());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("FaceState", this.entityData.get(FACE_STATE));
        tag.putInt("StageData", this.entityData.get(STAGE_DATA));
        tag.putBoolean("IsSpeaking", this.isSpeaking);
        tag.putInt("DayActivated", this.dayActivated);
        tag.putInt("HorrorEffectTicks", this.horrorEffectTicks);
        net.minecraft.nbt.ListTag historyTag = new net.minecraft.nbt.ListTag();
        for (String entry : this.conversationHistory) {
            historyTag.add(net.minecraft.nbt.StringTag.valueOf(entry));
        }
        tag.put("ConversationHistory", historyTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("FaceState")) {
            this.entityData.set(FACE_STATE, tag.getInt("FaceState"));
        }
        if (tag.contains("StageData")) {
            this.entityData.set(STAGE_DATA, tag.getInt("StageData"));
        }
        this.isSpeaking = tag.getBoolean("IsSpeaking");
        this.dayActivated = tag.getInt("DayActivated");
        this.horrorEffectTicks = tag.getInt("HorrorEffectTicks");
        this.conversationHistory.clear();
        if (tag.contains("ConversationHistory")) {
            net.minecraft.nbt.ListTag historyTag = tag.getList("ConversationHistory", 8);
            for (int i = 0; i < historyTag.size(); i++) {
                this.conversationHistory.add(historyTag.getString(i));
            }
        }
    }
}
