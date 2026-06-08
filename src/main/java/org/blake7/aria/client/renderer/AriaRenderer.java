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
import net.minecraft.util.Mth;
import org.blake7.aria.client.AriaClientEvents;
import org.blake7.aria.client.model.AriaFaceState;
import org.blake7.aria.entity.AriaEntity;
import org.joml.Matrix4f;

public class AriaRenderer extends EntityRenderer<AriaEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("aria", "textures/entity/aria.png");

    private static final int LONGITUDE_SEGMENTS = 16;
    private static final int LATITUDE_SEGMENTS = 12;
    private static final float RADIUS = 0.3F;

    private static final int FULL_BRIGHT = (15 << 20) | (15 << 4);

    private static final int FACE_R = 20;
    private static final int FACE_G = 20;
    private static final int FACE_B = 20;

    private static final float FACE_TRANSITION_SPEED = 0.15F;

    private AriaFaceState displayFace = AriaFaceState.IDLE;
    private AriaFaceState previousFace = AriaFaceState.IDLE;
    private float transitionProgress = 1.0F;

    public AriaRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(AriaEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float bob = Mth.sin((entity.tickCount + partialTick) * 0.1F) * 0.05F;

        AriaClientEvents.tryStartChat(entity);

        AriaFaceState targetFace = entity.getFaceState();
        if (targetFace != displayFace) {
            previousFace = displayFace;
            displayFace = targetFace;
            transitionProgress = 0.0F;
        }
        transitionProgress = Math.min(1.0F, transitionProgress + partialTick * FACE_TRANSITION_SPEED);

        poseStack.pushPose();
        poseStack.translate(0.0F, entity.getBbHeight() * 0.5F + bob, 0.0F);

        Matrix4f matrix = poseStack.last().pose();
        PoseStack.Pose pose = poseStack.last();

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        buildSphere(consumer, matrix, pose, RADIUS, 255, 230, 0, 255, FULL_BRIGHT);

        // Rotate face to match entity yaw
        poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw));

        float faceAlpha = transitionProgress < 1.0F
                ? (float) Math.sin(transitionProgress * Math.PI) : 1.0F;
        drawFace(entity, poseStack, bufferSource, FULL_BRIGHT, faceAlpha);

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private void drawFace(AriaEntity entity, PoseStack poseStack, MultiBufferSource bufferSource, int light, float alpha) {
        if (alpha <= 0.01F) return;

        AriaFaceState face = transitionProgress < 1.0F ? previousFace : displayFace;
        VertexConsumer faceConsumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        int faceA = (int) (255 * alpha);

        drawEyes(faceConsumer, poseStack, face, light, faceA);

        if (face != AriaFaceState.NO_MOUTH && face != AriaFaceState.STARING) {
            drawMouth(faceConsumer, poseStack, face, light, faceA);
        }
    }

    private void drawEyes(VertexConsumer consumer, PoseStack poseStack, AriaFaceState face, int light, int alpha) {
        Matrix4f matrix = poseStack.last().pose();
        PoseStack.Pose pose = poseStack.last();

        float eyeSize = 0.04F;
        float eyeY = 0.06F;
        float eyeZ = RADIUS + 0.005F;

        if (face == AriaFaceState.STARING) {
            drawSingleEye(consumer, matrix, pose, 0, eyeY, eyeZ, eyeSize * 2.5F, light, alpha);
            return;
        }

        if (face == AriaFaceState.EXCITED) {
            eyeSize = 0.05F;
        } else if (face == AriaFaceState.THINKING || face == AriaFaceState.LISTENING) {
            eyeSize = 0.035F;
        }

        float leftEyeX = -0.07F;
        float rightEyeX = 0.07F;

        drawEyeQuad(consumer, matrix, pose, leftEyeX, eyeY, eyeZ, eyeSize, light, alpha);
        drawEyeQuad(consumer, matrix, pose, rightEyeX, eyeY, eyeZ, eyeSize, light, alpha);

        if (face == AriaFaceState.EXCITED) {
            float pupilSize = eyeSize * 0.4F;
            drawEyeQuad(consumer, matrix, pose, leftEyeX, eyeY, eyeZ + 0.002F, pupilSize, light, alpha);
            drawEyeQuad(consumer, matrix, pose, rightEyeX, eyeY, eyeZ + 0.002F, pupilSize, light, alpha);
        }
    }

    private void drawSingleEye(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                               float x, float y, float z, float size, int light, int alpha) {
        float half = size / 2;
        emitFaceQuad(consumer, matrix, pose, x - half, y - half, z, x + half, y - half, z,
                x + half, y + half, z, x - half, y + half, z, light, alpha);
    }

    private void drawEyeQuad(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                             float cx, float cy, float cz, float size, int light, int alpha) {
        float half = size / 2;
        emitFaceQuad(consumer, matrix, pose,
                cx - half, cy - half, cz,
                cx + half, cy - half, cz,
                cx + half, cy + half, cz,
                cx - half, cy + half, cz, light, alpha);
    }

    private void drawMouth(VertexConsumer consumer, PoseStack poseStack, AriaFaceState face, int light, int alpha) {
        Matrix4f matrix = poseStack.last().pose();
        PoseStack.Pose pose = poseStack.last();

        float mouthY = -0.05F;
        float mouthZ = RADIUS + 0.005F;

        switch (face) {
            case IDLE -> drawCurvedMouth(consumer, matrix, pose, mouthY, mouthZ, 0.06F, 0.015F, false, light, alpha);
            case EXCITED -> drawCurvedMouth(consumer, matrix, pose, mouthY, mouthZ, 0.09F, 0.025F, false, light, alpha);
            case THINKING -> drawFlatMouth(consumer, matrix, pose, mouthY, mouthZ, 0.05F, light, alpha);
            case LISTENING -> drawCurvedMouth(consumer, matrix, pose, mouthY, mouthZ, 0.04F, 0.01F, false, light, alpha);
            case UNSETTLING -> drawCurvedMouth(consumer, matrix, pose, mouthY, mouthZ, 0.06F, 0.015F, true, light, alpha);
            case DISTURBING -> {
                drawCurvedMouth(consumer, matrix, pose, mouthY, mouthZ, 0.12F, 0.03F, false, light, alpha);
                drawTeeth(consumer, matrix, pose, mouthY, mouthZ, light, alpha);
            }
        }
    }

    private void drawCurvedMouth(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                                 float y, float z, float width, float height, boolean downturn, int light, int alpha) {
        int segments = 8;
        float halfWidth = width / 2;
        for (int i = 0; i < segments; i++) {
            float t1 = (float) i / segments;
            float t2 = (float) (i + 1) / segments;
            float x1 = -halfWidth + t1 * width;
            float x2 = -halfWidth + t2 * width;
            float curve1 = (float) Math.sin(t1 * Math.PI) * height;
            float curve2 = (float) Math.sin(t2 * Math.PI) * height;
            float yOff1 = downturn ? -curve1 : curve1;
            float yOff2 = downturn ? -curve2 : curve2;
            float thickness = 0.008F;

            emitFaceQuad(consumer, matrix, pose,
                    x1, y - yOff1 - thickness, z,
                    x2, y - yOff2 - thickness, z,
                    x2, y - yOff2 + thickness, z,
                    x1, y - yOff1 + thickness, z, light, alpha);
        }
    }

    private void drawFlatMouth(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                               float y, float z, float width, int light, int alpha) {
        float halfWidth = width / 2;
        float thickness = 0.006F;
        emitFaceQuad(consumer, matrix, pose,
                -halfWidth, y - thickness, z,
                halfWidth, y - thickness, z,
                halfWidth, y + thickness, z,
                -halfWidth, y + thickness, z, light, alpha);
    }

    private void drawTeeth(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                           float mouthY, float mouthZ, int light, int alpha) {
        int teethCount = 6;
        float mouthWidth = 0.10F;
        float toothWidth = mouthWidth / teethCount * 0.6F;
        float toothHeight = 0.012F;
        float teethY = mouthY + 0.015F;

        for (int i = 0; i < teethCount; i++) {
            float tx = -mouthWidth / 2 + (i + 0.5F) * (mouthWidth / teethCount);
            float halfW = toothWidth / 2;
            emitFaceQuad(consumer, matrix, pose,
                    tx - halfW, teethY, mouthZ,
                    tx + halfW, teethY, mouthZ,
                    tx + halfW, teethY - toothHeight, mouthZ,
                    tx - halfW, teethY - toothHeight, mouthZ, light, alpha);
        }
    }

    private void emitFaceQuad(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                              float x1, float y1, float z1,
                              float x2, float y2, float z2,
                              float x3, float y3, float z3,
                              float x4, float y4, float z4, int light, int alpha) {
        consumer.addVertex(matrix, x1, y1, z1).setColor(FACE_R, FACE_G, FACE_B, alpha)
                .setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, 0, 0, 1);
        consumer.addVertex(matrix, x2, y2, z2).setColor(FACE_R, FACE_G, FACE_B, alpha)
                .setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, 0, 0, 1);
        consumer.addVertex(matrix, x3, y3, z3).setColor(FACE_R, FACE_G, FACE_B, alpha)
                .setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, 0, 0, 1);
        consumer.addVertex(matrix, x4, y4, z4).setColor(FACE_R, FACE_G, FACE_B, alpha)
                .setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, 0, 0, 1);
    }

    private void buildSphere(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                             float radius, int r, int g, int b, int a, int light) {
        for (int lat = 0; lat < LATITUDE_SEGMENTS; lat++) {
            float theta1 = (float) (Math.PI * lat / LATITUDE_SEGMENTS);
            float theta2 = (float) (Math.PI * (lat + 1) / LATITUDE_SEGMENTS);
            float vt = (float) lat / LATITUDE_SEGMENTS;
            float vb = (float) (lat + 1) / LATITUDE_SEGMENTS;

            for (int lon = 0; lon < LONGITUDE_SEGMENTS; lon++) {
                float phi1 = (float) (2.0 * Math.PI * lon / LONGITUDE_SEGMENTS);
                float phi2 = (float) (2.0 * Math.PI * (lon + 1) / LONGITUDE_SEGMENTS);
                float u1 = (float) lon / LONGITUDE_SEGMENTS;
                float u2 = (float) (lon + 1) / LONGITUDE_SEGMENTS;

                float[] v1 = sphereVertex(radius, theta1, phi1);
                float[] v2 = sphereVertex(radius, theta1, phi2);
                float[] v3 = sphereVertex(radius, theta2, phi2);
                float[] v4 = sphereVertex(radius, theta2, phi1);

                emitVertex(consumer, matrix, pose, v1, u1, vt, r, g, b, a, light, radius);
                emitVertex(consumer, matrix, pose, v2, u2, vt, r, g, b, a, light, radius);
                emitVertex(consumer, matrix, pose, v3, u2, vb, r, g, b, a, light, radius);
                emitVertex(consumer, matrix, pose, v4, u1, vb, r, g, b, a, light, radius);
            }
        }
    }

    private float[] sphereVertex(float radius, float theta, float phi) {
        return new float[]{
                (float) (radius * Math.sin(theta) * Math.cos(phi)),
                (float) (radius * Math.cos(theta)),
                (float) (radius * Math.sin(theta) * Math.sin(phi))
        };
    }

    private void emitVertex(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                            float[] pos, float u, float v,
                            int r, int g, int b, int a, int light, float radius) {
        consumer.addVertex(matrix, pos[0], pos[1], pos[2])
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, pos[0] / radius, pos[1] / radius, pos[2] / radius);
    }

    @Override
    public ResourceLocation getTextureLocation(AriaEntity entity) {
        return TEXTURE;
    }
}
