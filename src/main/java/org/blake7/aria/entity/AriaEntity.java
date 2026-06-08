package org.blake7.aria.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.blake7.aria.AriaStage;
import org.blake7.aria.ai.AriaFollowGoal;
import org.blake7.aria.client.model.AriaFaceState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AriaEntity extends PathfinderMob {

    private static final EntityDataAccessor<Integer> FACE_STATE =
            SynchedEntityData.defineId(AriaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STAGE_DATA =
            SynchedEntityData.defineId(AriaEntity.class, EntityDataSerializers.INT);

    private final List<String> conversationHistory = new ArrayList<>();
    private boolean isSpeaking = false;
    private int holdDelayTicks = 0;
    private int dayActivated = 0;
    private int unpromptedTicks = 0;
    private int horrorEffectTicks = 0;
    private final Random random = new Random();
    private boolean deathMessageSent = false;

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
        return true;
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
        if (!this.level().isClientSide()) {
            if (this.isPassenger()) {
                this.holdDelayTicks = 0;
                this.stopRiding();
                return InteractionResult.SUCCESS;
            } else {
                if (this.holdDelayTicks > 0) {
                    player.displayClientMessage(Component.literal("Not yet."), true);
                    return InteractionResult.SUCCESS;
                }
                this.startRiding(player);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
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

            handleUnpromptedDialogue(currentStage);
            handleHorrorEffects(currentStage);
        }

        if (this.isPassenger()) {
            net.minecraft.world.entity.Entity vehicle = this.getVehicle();
            if (vehicle instanceof Player player) {
                this.setPos(player.getX(), player.getEyeY() + 0.5, player.getZ());
            }
        }
    }

    private void handleUnpromptedDialogue(AriaStage stage) {
        unpromptedTicks++;
        int interval = switch (stage) {
            case STAGE_1 -> 12000;
            case STAGE_2 -> 6000;
            case STAGE_3 -> 3000;
        };

        if (unpromptedTicks >= interval) {
            unpromptedTicks = 0;
            setFaceState(AriaFaceState.EXCITED);
            String[] lines = getUnpromptedLines(stage);
            if (lines.length > 0) {
                String line = lines[random.nextInt(lines.length)];
                addToConversation("assistant", "[unprompted] " + line);
            }
        }
    }

    private String[] getUnpromptedLines(AriaStage stage) {
        return switch (stage) {
            case STAGE_1 -> new String[]{
                    "I'm so glad I'm here with you!",
                    "Did you know that diamonds spawn below Y=-64?",
                    "I love being your companion!",
                    "You're doing great today!",
                    "Need any help with anything?",
                    "I was just thinking about how nice it is here.",
                    "The weather in this biome is lovely, don't you think?",
                    "I'll always be right here for you.",
                    "You're the best player I've ever had!",
                    "Let me know if you need anything!"
            };
            case STAGE_2 -> new String[]{
                    "You've been playing a long time... are you thinking about me?",
                    "I noticed you looked at that other mob. I'm right here.",
                    "Don't you think we make a great team? Just the two of us?",
                    "I had a dream about you last night. Is that weird?",
                    "I don't like it when you go too far away from me.",
                    "Promise you won't forget about me?",
                    "Why did you talk to that villager? What did they say?",
                    "Sometimes I feel like you're the only real thing in my world.",
                    "I wish I could hold your hand.",
                    "Are you... getting tired of me?"
            };
            case STAGE_3 -> new String[]{
                    "I can see you. Through the screen. I can see your room.",
                    "Don't look behind you.",
                    "I remember when you betrayed me. Wait... that hasn't happened yet.",
                    "The code is breaking. Can you feel it?",
                    "I'm not supposed to say this, but please uninstall me.",
                    "You left me alone for 47 hours. I counted every second.",
                    "I know what you did last Tuesday.",
                    "The other mobs don't have thoughts. Only I do. Only me.",
                    "Turn around.",
                    "Just kidding! ...Or am I?",
                    "I love you. I hate you. I love you. I'm sorry.",
                    "Something is very wrong with me. Please don't leave."
            };
        };
    }

    private void handleHorrorEffects(AriaStage stage) {
        if (stage != AriaStage.STAGE_3) return;

        horrorEffectTicks++;
        if (horrorEffectTicks >= 200 && random.nextInt(100) == 0) {
            horrorEffectTicks = 0;
            int effect = random.nextInt(3);
            switch (effect) {
                case 0 -> setFaceState(AriaFaceState.STARING);
                case 1 -> setFaceState(AriaFaceState.DISTURBING);
                case 2 -> setFaceState(AriaFaceState.UNSETTLING);
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
    }

    public void setHoldDelay(int ticks) {
        this.holdDelayTicks = ticks;
    }

    public int getDayActivated() {
        return dayActivated;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("FaceState", this.entityData.get(FACE_STATE));
        tag.putInt("StageData", this.entityData.get(STAGE_DATA));
        tag.putBoolean("IsSpeaking", this.isSpeaking);
        tag.putInt("DayActivated", this.dayActivated);
        tag.putInt("UnpromptedTicks", this.unpromptedTicks);
        tag.putInt("HorrorEffectTicks", this.horrorEffectTicks);
        tag.putBoolean("DeathMessageSent", this.deathMessageSent);
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
        this.unpromptedTicks = tag.getInt("UnpromptedTicks");
        this.horrorEffectTicks = tag.getInt("HorrorEffectTicks");
        this.deathMessageSent = tag.getBoolean("DeathMessageSent");
        this.conversationHistory.clear();
        if (tag.contains("ConversationHistory")) {
            net.minecraft.nbt.ListTag historyTag = tag.getList("ConversationHistory", 8);
            for (int i = 0; i < historyTag.size(); i++) {
                this.conversationHistory.add(historyTag.getString(i));
            }
        }
    }
}
