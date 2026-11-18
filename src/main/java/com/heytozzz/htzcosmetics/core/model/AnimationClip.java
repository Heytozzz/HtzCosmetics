package com.heytozzz.htzcosmetics.core.model;

import java.util.List;

/**
 * A looping, purely declarative animation: keyframes per bone, nothing is ever evaluated as code.
 *
 * @param trigger when it plays
 * @param length  loop length in seconds
 * @param tracks  animated bones (bones without a track keep their rest pose)
 */
public record AnimationClip(Trigger trigger, float length, List<BoneTrack> tracks) {
    /** When a clip plays. The most specific available one wins: SNEAK, then WALK, then IDLE. */
    public enum Trigger {
        IDLE, WALK, SNEAK
    }

    /**
     * Keyframed offsets for one bone, added on top of its rest pose. Any channel may be null.
     *
     * @param rotation degrees around X, Y, Z (added)
     * @param position model pixels (added)
     * @param scale    multipliers (1 = unchanged)
     */
    public record BoneTrack(int boneIndex, Channel rotation, Channel position, Channel scale) {
    }
}
