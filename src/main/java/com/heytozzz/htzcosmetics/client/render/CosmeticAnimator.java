package com.heytozzz.htzcosmetics.client.render;

import com.heytozzz.htzcosmetics.core.model.AnimationClip;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;

import java.util.List;

/**
 * Poses a cosmetic's bones for the current frame from its keyframe clips. Pure arithmetic, no allocation.
 *
 * <p>Clip choice: SNEAK while crouching, WALK while moving, otherwise IDLE (falling back to IDLE, then rest pose).
 * Time comes from the player's age, so every player's cosmetic runs on its own clock and stays smooth.
 *
 * <p>Baked parts are shared by every player wearing the cosmetic, so the pose is set right before each draw
 * (render thread only, one draw at a time).
 */
final class CosmeticAnimator {
    private static final float WALK_THRESHOLD = 0.1F;
    private static final float[] VALUE = new float[3];

    private CosmeticAnimator() {
    }

    /** Which clip fits the player's current situation. */
    static AnimationClip.Trigger trigger(PlayerRenderState state) {
        return state.isCrouching ? AnimationClip.Trigger.SNEAK
            : state.walkAnimationSpeed > WALK_THRESHOLD ? AnimationClip.Trigger.WALK
            : AnimationClip.Trigger.IDLE;
    }

    /** Poses the cosmetic at {@code seconds} for the given situation (also used by menu thumbnails). */
    static void poseAt(BakedCosmetic cosmetic, float seconds, AnimationClip.Trigger wanted) {
        List<AnimationClip> clips = cosmetic.animations();
        if (clips.isEmpty()) return;
        ModelPart[] bones = cosmetic.bones();
        for (ModelPart bone : bones) bone.resetPose();

        AnimationClip clip = choose(clips, wanted);
        if (clip == null) return;
        float time = seconds % clip.length();

        for (AnimationClip.BoneTrack track : clip.tracks()) {
            ModelPart part = bones[track.boneIndex()];
            if (track.rotation() != null) {
                track.rotation().sample(time, VALUE);
                part.xRot += (float) Math.toRadians(VALUE[0]);
                part.yRot += (float) Math.toRadians(VALUE[1]);
                part.zRot += (float) Math.toRadians(VALUE[2]);
            }
            if (track.position() != null) {
                track.position().sample(time, VALUE);
                part.x += VALUE[0];
                part.y += VALUE[1];
                part.z += VALUE[2];
            }
            if (track.scale() != null) {
                track.scale().sample(time, VALUE);
                part.xScale *= VALUE[0];
                part.yScale *= VALUE[1];
                part.zScale *= VALUE[2];
            }
        }
    }

    /** Gives the bloom copy {@code to} the current pose of the model's bones {@code from} (same bone order). */
    static void copyPose(ModelPart[] from, ModelPart[] to) {
        for (int i = 0; i < from.length; i++) {
            ModelPart a = from[i];
            ModelPart b = to[i];
            b.x = a.x;
            b.y = a.y;
            b.z = a.z;
            b.xRot = a.xRot;
            b.yRot = a.yRot;
            b.zRot = a.zRot;
            b.xScale = a.xScale;
            b.yScale = a.yScale;
            b.zScale = a.zScale;
        }
    }

    private static AnimationClip choose(List<AnimationClip> clips, AnimationClip.Trigger wanted) {
        AnimationClip idle = null;
        for (AnimationClip c : clips) {
            if (c.trigger() == wanted) return c;
            if (c.trigger() == AnimationClip.Trigger.IDLE) idle = c;
        }
        return idle;
    }
}
