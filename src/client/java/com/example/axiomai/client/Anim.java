package com.example.axiomai.client;

import imgui.moulberry92.ImGui;

import java.util.HashMap;
import java.util.Map;

/** Tiny animation helpers for ImGui: values that glide towards a target instead of jumping. */
final class Anim {
    private static final Map<String, Float> VALUES = new HashMap<>();

    private Anim() {
    }

    static float dt() {
        return Math.min(0.1f, ImGui.getIO().getDeltaTime());
    }

    /** Moves the value stored under id towards target (exponential smoothing; speed 10-20 feels quick). */
    static float smooth(String id, float target, float speed) {
        return smoothFrom(id, target, target, speed);
    }

    /** Like smooth, but the very first time the value starts at initial (so things can animate in). */
    static float smoothFrom(String id, float initial, float target, float speed) {
        if (VALUES.size() > 4000) VALUES.clear();
        float cur = VALUES.getOrDefault(id, initial);
        cur += (target - cur) * (1f - (float) Math.exp(-speed * dt()));
        if (Math.abs(target - cur) < 0.002f) cur = target;
        VALUES.put(id, cur);
        return cur;
    }

    static void reset(String id, float value) {
        VALUES.put(id, value);
    }

    static float clamp01(float v) {
        return v < 0 ? 0 : Math.min(v, 1f);
    }

    static float easeOut(float t) {
        float k = 1f - clamp01(t);
        return 1f - k * k * k;
    }
}
