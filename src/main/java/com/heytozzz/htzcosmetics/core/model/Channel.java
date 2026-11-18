package com.heytozzz.htzcosmetics.core.model;

/**
 * One animated property (3 values) over time. Keyframe times are strictly increasing and already validated.
 * Sampling is plain arithmetic: no allocation, safe to call every frame.
 */
public final class Channel {
    public enum Interpolation {
        LINEAR, STEP, SMOOTH
    }

    private final Interpolation interpolation;
    private final float[] times;
    private final float[] values; // 3 per keyframe

    public Channel(Interpolation interpolation, float[] times, float[] values) {
        if (times.length == 0 || values.length != times.length * 3) throw new IllegalArgumentException("bad channel");
        this.interpolation = interpolation;
        this.times = times.clone();
        this.values = values.clone();
    }

    public int keyframes() {
        return times.length;
    }

    /** Writes the value at {@code time} (seconds) into {@code out[0..2]}. Holds the first/last value outside. */
    public void sample(float time, float[] out) {
        int n = times.length;
        if (n == 1 || time <= times[0]) {
            copy(0, out);
            return;
        }
        if (time >= times[n - 1]) {
            copy(n - 1, out);
            return;
        }
        int k = 0;
        while (k + 1 < n && times[k + 1] <= time) k++; // at most 64 keyframes: a scan is fine
        if (interpolation == Interpolation.STEP) {
            copy(k, out);
            return;
        }
        float t = (time - times[k]) / (times[k + 1] - times[k]);
        for (int axis = 0; axis < 3; axis++) {
            float p1 = values[k * 3 + axis];
            float p2 = values[(k + 1) * 3 + axis];
            if (interpolation == Interpolation.LINEAR) {
                out[axis] = p1 + (p2 - p1) * t;
            } else {
                float p0 = values[Math.max(k - 1, 0) * 3 + axis];
                float p3 = values[Math.min(k + 2, n - 1) * 3 + axis];
                out[axis] = catmullRom(t, p0, p1, p2, p3);
            }
        }
    }

    private void copy(int k, float[] out) {
        out[0] = values[k * 3];
        out[1] = values[k * 3 + 1];
        out[2] = values[k * 3 + 2];
    }

    /** Same curve as Minecraft's own animations (Mth.catmullrom). */
    static float catmullRom(float t, float p0, float p1, float p2, float p3) {
        return 0.5F * (2.0F * p1 + (p2 - p0) * t + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t * t
            + (3.0F * p1 - p0 - 3.0F * p2 + p3) * t * t * t);
    }
}
