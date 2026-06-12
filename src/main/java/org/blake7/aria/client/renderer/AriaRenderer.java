package org.blake7.aria.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.blake7.aria.Aria;
import org.blake7.aria.client.AriaClientEvents;
import org.blake7.aria.client.model.AriaFaceState;
import org.blake7.aria.entity.AriaEntity;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.Map;

public class AriaRenderer extends EntityRenderer<AriaEntity> {

    private static final ResourceLocation BODY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Aria.MODID, "textures/entity/aria.png");

    private static final Map<AriaFaceState, ResourceLocation> FACE_TEXTURES = new EnumMap<>(AriaFaceState.class);

    static {
        FACE_TEXTURES.put(AriaFaceState.IDLE, ResourceLocation.fromNamespaceAndPath(Aria.MODID, "textures/entity/aria_idle.png"));
        FACE_TEXTURES.put(AriaFaceState.EXCITED, ResourceLocation.fromNamespaceAndPath(Aria.MODID, "textures/entity/aria_excited.png"));
        FACE_TEXTURES.put(AriaFaceState.THINKING, ResourceLocation.fromNamespaceAndPath(Aria.MODID, "textures/entity/aria_thinking.png"));
        FACE_TEXTURES.put(AriaFaceState.LISTENING, ResourceLocation.fromNamespaceAndPath(Aria.MODID, "textures/entity/aria_listening.png"));
        FACE_TEXTURES.put(AriaFaceState.UNSETTLING, ResourceLocation.fromNamespaceAndPath(Aria.MODID, "textures/entity/aria_unsettling.png"));
        FACE_TEXTURES.put(AriaFaceState.NO_MOUTH, ResourceLocation.fromNamespaceAndPath(Aria.MODID, "textures/entity/aria_no_mouth.png"));
        FACE_TEXTURES.put(AriaFaceState.STARING, ResourceLocation.fromNamespaceAndPath(Aria.MODID, "textures/entity/aria_staring.png"));
        FACE_TEXTURES.put(AriaFaceState.DISTURBING, ResourceLocation.fromNamespaceAndPath(Aria.MODID, "textures/entity/aria_disturbing.png"));
    }

    private static final float SPHERE_RADIUS = 0.3F;
    private static final int FULL_BRIGHT = (15 << 20) | (15 << 4);
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
        AriaClientEvents.tryStartChat(entity);

        AriaFaceState targetFace = entity.getFaceState();
        if (targetFace != displayFace) {
            previousFace = displayFace;
            displayFace = targetFace;
            transitionProgress = 0.0F;
        }
        transitionProgress = Math.min(1.0F, transitionProgress + partialTick * FACE_TRANSITION_SPEED);

        poseStack.pushPose();
        poseStack.translate(0.0F, entity.getBbHeight() * 0.5F, 0.0F);

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        double dx = camPos.x - entity.getX();
        double dy = camPos.y - (entity.getY() + entity.getBbHeight() * 0.5);
        double dz = camPos.z - entity.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));

        drawSphere(poseStack, bufferSource, FULL_BRIGHT);

        float faceAlpha = transitionProgress < 1.0F
                ? (float) Math.sin(transitionProgress * Math.PI) : 1.0F;
        drawFace(poseStack, bufferSource, FULL_BRIGHT, faceAlpha);

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private void drawSphere(PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(BODY_TEXTURE));
        Matrix4f matrix = poseStack.last().pose();
        PoseStack.Pose pose = poseStack.last();

        int latSegments = 16;
        int lonSegments = 16;

        for (int lat = 0; lat < latSegments; lat++) {
            float theta1 = (float) Math.PI * lat / latSegments;
            float theta2 = (float) Math.PI * (lat + 1) / latSegments;

            for (int lon = 0; lon < lonSegments; lon++) {
                float phi1 = 2.0f * (float) Math.PI * lon / lonSegments;
                float phi2 = 2.0f * (float) Math.PI * (lon + 1) / lonSegments;

                float u1 = (float) lon / lonSegments;
                float u2 = (float) (lon + 1) / lonSegments;
                float v1 = (float) lat / latSegments;
                float v2 = (float) (lat + 1) / latSegments;

                float[] p1 = sphereVertex(theta1, phi1);
                float[] p2 = sphereVertex(theta2, phi1);
                float[] p3 = sphereVertex(theta2, phi2);
                float[] p4 = sphereVertex(theta1, phi2);

                float[] n1 = sphereNormal(theta1, phi1);
                float[] n2 = sphereNormal(theta2, phi1);
                float[] n3 = sphereNormal(theta2, phi2);
                float[] n4 = sphereNormal(theta1, phi2);

                consumer.addVertex(matrix, p1[0], p1[1], p1[2])
                        .setColor(255, 255, 255, 255)
                        .setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(pose, n1[0], n1[1], n1[2]);
                consumer.addVertex(matrix, p2[0], p2[1], p2[2])
                        .setColor(255, 255, 255, 255)
                        .setUv(u1, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(pose, n2[0], n2[1], n2[2]);
                consumer.addVertex(matrix, p3[0], p3[1], p3[2])
                        .setColor(255, 255, 255, 255)
                        .setUv(u2, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(pose, n3[0], n3[1], n3[2]);
                consumer.addVertex(matrix, p4[0], p4[1], p4[2])
                        .setColor(255, 255, 255, 255)
                        .setUv(u2, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(pose, n4[0], n4[1], n4[2]);
            }
        }
    }

    private float[] sphereVertex(float theta, float phi) {
        return new float[] {
            SPHERE_RADIUS * (float) Math.sin(theta) * (float) Math.cos(phi),
            SPHERE_RADIUS * (float) Math.cos(theta),
            SPHERE_RADIUS * (float) Math.sin(theta) * (float) Math.sin(phi)
        };
    }

    private float[] sphereNormal(float theta, float phi) {
        return new float[] {
            (float) Math.sin(theta) * (float) Math.cos(phi),
            (float) Math.cos(theta),
            (float) Math.sin(theta) * (float) Math.sin(phi)
        };
    }

    private void drawFace(PoseStack poseStack, MultiBufferSource bufferSource, int light, float alpha) {
        if (alpha <= 0.01F) return;

        AriaFaceState face = transitionProgress < 1.0F ? previousFace : displayFace;
        ResourceLocation faceTex = FACE_TEXTURES.getOrDefault(face, BODY_TEXTURE);

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(faceTex));
        Matrix4f matrix = poseStack.last().pose();
        PoseStack.Pose pose = poseStack.last();

        float faceHalf = SPHERE_RADIUS * 0.65F;
        float z = SPHERE_RADIUS * 0.99F;
        int a = (int) (255 * alpha);

        consumer.addVertex(matrix, -faceHalf, faceHalf, z)
                .setColor(255, 255, 255, a)
                .setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, 0, 0, 1);
        consumer.addVertex(matrix, -faceHalf, -faceHalf, z)
                .setColor(255, 255, 255, a)
                .setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, 0, 0, 1);
        consumer.addVertex(matrix, faceHalf, -faceHalf, z)
                .setColor(255, 255, 255, a)
                .setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, 0, 0, 1);
        consumer.addVertex(matrix, faceHalf, faceHalf, z)
                .setColor(255, 255, 255, a)
                .setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, 0, 0, 1);
    }

    @Override
    public ResourceLocation getTextureLocation(AriaEntity entity) {
        return BODY_TEXTURE;
    }
}
