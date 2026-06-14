package org.blake7.aria.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.blake7.aria.Aria;
import org.blake7.aria.entity.AriaBoxEntity;
import org.joml.Matrix4f;

public class AriaBoxRenderer extends EntityRenderer<AriaBoxEntity> {

    private static final ResourceLocation BOX_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Aria.MODID, "textures/entity/aria_box.png");

    private static final float BOX_W = 0.8F;
    private static final float BOX_H = 0.7F;
    private static final float BOX_D = 0.8F;
    private static final float FLAP_H = 0.25F;
    private static final int FULL_BRIGHT = (15 << 20) | (15 << 4);

    public AriaBoxRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
    }

    @Override
    public void render(AriaBoxEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        float yOff = entity.getBbHeight() * 0.5F;
        AriaBoxEntity.BoxState state = entity.getBoxState();
        float ticks = entity.getAnimationTicks() + partialTick;

        switch (state) {
            case FLOATING -> {
                float p = Math.min(1.0F, ticks / 20.0F);
                float bob = (float) Math.sin(p * Math.PI) * 0.4F;
                poseStack.translate(0.0F, yOff + bob, 0.0F);
            }
            case OPENING -> {
                float p = Math.min(1.0F, ticks / 15.0F);
                poseStack.translate(0.0F, yOff + 0.4F + p * 0.3F, 0.0F);
            }
            case DISAPPEARING -> {
                float p = Math.min(1.0F, ticks / 10.0F);
                float s = Math.max(0.01F, 1.0F - p);
                poseStack.scale(s, s, s);
                poseStack.translate(0.0F, yOff + 0.7F + p * 0.5F, 0.0F);
            }
            default -> poseStack.translate(0.0F, yOff, 0.0F);
        }

        float openP = 0.0F;
        if (state == AriaBoxEntity.BoxState.OPENING) {
            openP = Math.min(1.0F, ticks / 15.0F);
        } else if (state == AriaBoxEntity.BoxState.DISAPPEARING) {
            openP = 1.0F;
        }

        float alpha = state == AriaBoxEntity.BoxState.DISAPPEARING
                ? 1.0F - Math.min(1.0F, ticks / 10.0F) : 1.0F;

        drawBoxBody(poseStack, bufferSource, alpha);
        drawFlaps(poseStack, bufferSource, openP, alpha);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private void drawBoxBody(PoseStack ps, MultiBufferSource buf, float alpha) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(BOX_TEXTURE));
        Matrix4f m = ps.last().pose();
        PoseStack.Pose p = ps.last();
        int a = (int) (255 * alpha);
        float hw = BOX_W / 2, hh = BOX_H / 2, hd = BOX_D / 2;

        addFace(vc, m, p, a, 0, 1, 0,
                -hw, hh, -hd, 0, 0,
                -hw, hh, hd, 0, 0.5F,
                hw, hh, hd, 0.5F, 0.5F,
                hw, hh, -hd, 0.5F, 0);

        addFace(vc, m, p, a, 0, -1, 0,
                -hw, -hh, hd, 0, 0,
                -hw, -hh, -hd, 0, 0.5F,
                hw, -hh, -hd, 0.5F, 0.5F,
                hw, -hh, hd, 0.5F, 0);

        addFace(vc, m, p, a, -1, 0, 0,
                -hw, -hh, -hd, 0, 0,
                -hw, -hh, hd, 0, 0.5F,
                -hw, hh, hd, 0.5F, 0.5F,
                -hw, hh, -hd, 0.5F, 0);

        addFace(vc, m, p, a, 1, 0, 0,
                hw, -hh, hd, 0, 0,
                hw, -hh, -hd, 0, 0.5F,
                hw, hh, -hd, 0.5F, 0.5F,
                hw, hh, hd, 0.5F, 0);

        addFace(vc, m, p, a, 0, 0, -1,
                -hw, -hh, -hd, 0, 0,
                hw, -hh, -hd, 0.5F, 0,
                hw, hh, -hd, 0.5F, 0.5F,
                -hw, hh, -hd, 0, 0.5F);

        addFace(vc, m, p, a, 0, 0, 1,
                hw, -hh, hd, 0, 0,
                -hw, -hh, hd, 0.5F, 0,
                -hw, hh, hd, 0.5F, 0.5F,
                hw, hh, hd, 0, 0.5F);
    }

    private void drawFlaps(PoseStack ps, MultiBufferSource buf, float openP, float alpha) {
        float hw = BOX_W / 2, hh = BOX_H / 2, hd = BOX_D / 2;
        int a = (int) (255 * alpha);
        float angle = (float) Math.toRadians(80.0F * openP);

        ps.pushPose();
        ps.translate(0, hh, -hd);
        ps.mulPose(Axis.XP.rotation(angle));
        drawQuad(buf, a, ps, -hw, 0, 0, hw, 0, 0, hw, FLAP_H, 0, -hw, FLAP_H, 0, 0, 0, -1);
        ps.popPose();

        ps.pushPose();
        ps.translate(0, hh, hd);
        ps.mulPose(Axis.XN.rotation(angle));
        drawQuad(buf, a, ps, -hw, 0, 0, hw, 0, 0, hw, FLAP_H, 0, -hw, FLAP_H, 0, 0, 0, 1);
        ps.popPose();

        ps.pushPose();
        ps.translate(-hw, hh, 0);
        ps.mulPose(Axis.ZP.rotation(angle));
        drawQuad(buf, a, ps, 0, 0, -hd, 0, 0, hd, 0, FLAP_H, hd, 0, FLAP_H, -hd, -1, 0, 0);
        ps.popPose();

        ps.pushPose();
        ps.translate(hw, hh, 0);
        ps.mulPose(Axis.ZN.rotation(angle));
        drawQuad(buf, a, ps, 0, 0, -hd, 0, 0, hd, 0, FLAP_H, hd, 0, FLAP_H, -hd, 1, 0, 0);
        ps.popPose();
    }

    private void drawQuad(MultiBufferSource buf, int a, PoseStack ps,
                          float x1, float y1, float z1,
                          float x2, float y2, float z2,
                          float x3, float y3, float z3,
                          float x4, float y4, float z4,
                          float nx, float ny, float nz) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(BOX_TEXTURE));
        Matrix4f m = ps.last().pose();
        PoseStack.Pose p = ps.last();
        vc.addVertex(m, x1, y1, z1).setColor(255, 255, 255, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, nx, ny, nz);
        vc.addVertex(m, x2, y2, z2).setColor(255, 255, 255, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, nx, ny, nz);
        vc.addVertex(m, x3, y3, z3).setColor(255, 255, 255, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, nx, ny, nz);
        vc.addVertex(m, x4, y4, z4).setColor(255, 255, 255, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, nx, ny, nz);
    }

    private void addFace(VertexConsumer vc, Matrix4f m, PoseStack.Pose p, int a,
                         float nx, float ny, float nz,
                         float x1, float y1, float z1, float u1, float v1,
                         float x2, float y2, float z2, float u2, float v2,
                         float x3, float y3, float z3, float u3, float v3,
                         float x4, float y4, float z4, float u4, float v4) {
        vc.addVertex(m, x1, y1, z1).setColor(255, 255, 255, a).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, nx, ny, nz);
        vc.addVertex(m, x2, y2, z2).setColor(255, 255, 255, a).setUv(u2, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, nx, ny, nz);
        vc.addVertex(m, x3, y3, z3).setColor(255, 255, 255, a).setUv(u3, v3).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, nx, ny, nz);
        vc.addVertex(m, x4, y4, z4).setColor(255, 255, 255, a).setUv(u4, v4).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(AriaBoxEntity entity) {
        return BOX_TEXTURE;
    }
}
