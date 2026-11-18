package com.heytozzz.htzcosmetics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Smooth glow around one body part, in the style of the vanilla "glowing" effect but depth tested (it never shows
 * through walls) and configurable.
 *
 * <p>How: a flat panel facing the camera is placed just BEHIND the body part. The panel is the part's outline as
 * seen from the camera, grown by the glow's width, and drawn as the border of a 3x3 grid (nine-slice) with a soft
 * radial texture, so the fade has the same width on every side and has round corners; the middle is left empty, so
 * only the glow around the silhouette is visible. Panels of neighbouring parts blend into one
 * smooth glow instead of showing separate boxes.
 */
final class AuraGlow {
    // Render thread only: scratch objects.
    private static final Matrix4f INVERSE = new Matrix4f();
    private static final Vector3f TO_CAMERA = new Vector3f();
    private static final Vector3f RIGHT = new Vector3f();
    private static final Vector3f UP = new Vector3f();
    private static final Vector3f TMP = new Vector3f();
    private static final float[] EDGES_A = new float[4];
    private static final float[] EDGES_B = new float[4];
    private static final float[] UVS = {0.0F, 0.5F, 0.5F, 1.0F};

    private AuraGlow() {
    }

    /**
     * Computes the panel for a box centred on the current pose origin and writes its nine quads.
     *
     * @param half  half size of the box in model units (pixels / 16)
     * @param width how far the glow reaches beyond the silhouette, in model units
     * @param gui   true in menus (orthographic view), false in the world (camera at the origin of
     *              the camera-relative pose space)
     */
    static void write(PoseStack.Pose pose, VertexConsumer vertices, float[] half, float width, boolean gui, int color) {
        INVERSE.set(pose.pose()).invert();
        if (gui) {
            INVERSE.transformDirection(TO_CAMERA.set(0, 0, 1));
        } else {
            INVERSE.transformPosition(TO_CAMERA.set(0, 0, 0)); // the camera, seen from the part's centre
        }
        if (TO_CAMERA.lengthSquared() < 1e-8F) return;
        TO_CAMERA.normalize();

        // Panel axes: "up" follows the body part's length (so a swinging arm keeps a matching glow).
        UP.set(0, 1, 0);
        TO_CAMERA.cross(UP, RIGHT);
        if (RIGHT.lengthSquared() < 1e-6F) { // looking straight along the part
            UP.set(0, 0, 1);
            TO_CAMERA.cross(UP, RIGHT);
        }
        RIGHT.normalize();
        RIGHT.cross(TO_CAMERA, UP).normalize();

        // Outline of the box as seen from the camera, and its farthest point.
        float minR = Float.MAX_VALUE, maxR = -Float.MAX_VALUE, minU = Float.MAX_VALUE, maxU = -Float.MAX_VALUE;
        float back = Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            TMP.set((i & 1) == 0 ? -half[0] : half[0], (i & 2) == 0 ? -half[1] : half[1], (i & 4) == 0 ? -half[2] : half[2]);
            float r = TMP.dot(RIGHT);
            float u = TMP.dot(UP);
            minR = Math.min(minR, r);
            maxR = Math.max(maxR, r);
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            back = Math.min(back, TMP.dot(TO_CAMERA));
        }
        float depth = back - 0.01F; // just behind the part

        EDGES_A[0] = minR - width;
        EDGES_A[1] = minR;
        EDGES_A[2] = maxR;
        EDGES_A[3] = maxR + width;
        EDGES_B[0] = minU - width;
        EDGES_B[1] = minU;
        EDGES_B[2] = maxU;
        EDGES_B[3] = maxU + width;
        for (int col = 0; col < 3; col++) {
            for (int row = 0; row < 3; row++) {
                // The middle cell would be a solid block where the shape is not (hollow cosmetics like crowns,
                // or a box seen at an angle): only the border around the outline is drawn.
                if (col == 1 && row == 1) continue;
                // Both windings: the render type culls back faces, and menus mirror the view.
                for (int side = 0; side < 2; side++) {
                    int c0 = side == 0 ? col : col + 1;
                    int c1 = side == 0 ? col + 1 : col;
                    vertex(pose, vertices, EDGES_A[c0], EDGES_B[row], depth, UVS[c0], UVS[row], color);
                    vertex(pose, vertices, EDGES_A[c0], EDGES_B[row + 1], depth, UVS[c0], UVS[row + 1], color);
                    vertex(pose, vertices, EDGES_A[c1], EDGES_B[row + 1], depth, UVS[c1], UVS[row + 1], color);
                    vertex(pose, vertices, EDGES_A[c1], EDGES_B[row], depth, UVS[c1], UVS[row], color);
                }
            }
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices, float r, float u, float d,
                               float tu, float tv, int color) {
        float x = RIGHT.x * r + UP.x * u + TO_CAMERA.x * d;
        float y = RIGHT.y * r + UP.y * u + TO_CAMERA.y * d;
        float z = RIGHT.z * r + UP.z * u + TO_CAMERA.z * d;
        vertices.addVertex(pose, x, y, z)
            .setColor(color)
            .setUv(tu, tv)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(pose, TO_CAMERA.x, TO_CAMERA.y, TO_CAMERA.z);
    }
}
