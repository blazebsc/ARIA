package org.blake7.aria.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import org.blake7.aria.entity.AriaEntity;

import java.util.EnumSet;

public class AriaFollowGoal extends Goal {

    private final AriaEntity aria;
    private final double speedModifier;
    private final float startDistance;
    private final float stopDistance;
    private Player targetPlayer;

    public AriaFollowGoal(AriaEntity aria, double speedModifier, float startDistance, float stopDistance) {
        this.aria = aria;
        this.speedModifier = speedModifier;
        this.startDistance = startDistance;
        this.stopDistance = stopDistance;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Player nearest = aria.level().getNearestPlayer(aria, startDistance);
        if (nearest == null) return false;
        this.targetPlayer = nearest;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return targetPlayer != null && targetPlayer.isAlive() && aria.distanceTo(targetPlayer) > stopDistance;
    }

    @Override
    public void start() {
        aria.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (targetPlayer == null || !targetPlayer.isAlive()) return;

        aria.getLookControl().setLookAt(targetPlayer, 30.0F, 30.0F);

        double dist = aria.distanceTo(targetPlayer);
        if (dist > stopDistance) {
            // Smooth floating movement via direct position interpolation
            double targetX = targetPlayer.getX();
            double targetY = targetPlayer.getEyeY();
            double targetZ = targetPlayer.getZ();

            double dx = targetX - aria.getX();
            double dy = targetY - aria.getY();
            double dz = targetZ - aria.getZ();

            double moveSpeed = speedModifier * 0.05;
            double moveDist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (moveDist > 0.1) {
                double moveAmount = Math.min(moveSpeed, moveDist);
                double moveX = (dx / moveDist) * moveAmount;
                double moveY = (dy / moveDist) * moveAmount;
                double moveZ = (dz / moveDist) * moveAmount;

                aria.setPos(aria.getX() + moveX, aria.getY() + moveY, aria.getZ() + moveZ);
            }
        }
    }

    @Override
    public void stop() {
        targetPlayer = null;
        aria.getNavigation().stop();
    }
}
