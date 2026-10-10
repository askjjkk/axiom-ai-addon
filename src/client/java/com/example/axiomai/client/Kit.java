package com.example.axiomai.client;

import imgui.moulberry92.ImDrawList;
import imgui.moulberry92.ImGui;
import imgui.moulberry92.ImVec2;
import imgui.moulberry92.flag.ImGuiMouseCursor;

/** Colors and small drawing tools (rounded boxes, icon buttons, icons) for the modern chat look. */
final class Kit {
    static final float[] BG_MAIN = {0.055f, 0.060f, 0.072f, 1f};
    static final float[] BG_SIDEBAR = {0.072f, 0.078f, 0.092f, 1f};
    static final float[] BG_CARD = {0.105f, 0.113f, 0.133f, 1f};
    static final float[] BG_CODE = {0.043f, 0.048f, 0.060f, 1f};
    static final float[] BORDER = {0.19f, 0.21f, 0.26f, 1f};
    static final float[] ACCENT = {0.31f, 0.53f, 0.96f, 1f};
    static final float[] BUBBLE = {0.17f, 0.31f, 0.56f, 1f};
    static final float[] TEXT = {0.91f, 0.93f, 0.96f, 1f};
    static final float[] DIM = {0.58f, 0.62f, 0.69f, 1f};
    static final float[] GREEN = {0.42f, 0.86f, 0.56f, 1f};
    static final float[] AMBER = {1.00f, 0.76f, 0.36f, 1f};
    static final float[] RED = {1.00f, 0.46f, 0.46f, 1f};
    static final float[] WHITE = {1f, 1f, 1f, 1f};

    private static final ImVec2 TMP = new ImVec2();

    private Kit() {
    }

    /** ImGui color number. The window's current alpha (fade animations) is applied automatically. */
    static int c(float[] col) {
        return ImGui.getColorU32(col[0], col[1], col[2], col[3]);
    }

    static int c(float[] col, float alpha) {
        return ImGui.getColorU32(col[0], col[1], col[2], col[3] * alpha);
    }

    static float[] mix(float[] a, float[] b, float t) {
        t = Anim.clamp01(t);
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t, a[3] + (b[3] - a[3]) * t};
    }

    private static final java.util.Map<String, Float> WIDTHS = new java.util.HashMap<>();
    private static final java.util.Map<String, String> SHORTENED = new java.util.HashMap<>();
    /** Font size of the current frame, set once per frame by the view (saves many native calls). */
    static float frameFontSize = 16f;

    static float textWidth(String s) {
        String key = s + '\0' + (int) (ImGui.getFontSize() * 10);
        Float cached = WIDTHS.get(key);
        if (cached != null) return cached;
        if (WIDTHS.size() > 3000) WIDTHS.clear();
        ImGui.calcTextSize(TMP, s);
        WIDTHS.put(key, TMP.x);
        return TMP.x;
    }

    /** Shortens the text with "..." so that it fits into maxWidth. */
    static String ellipsize(String s, float maxWidth) {
        String key = s + '\0' + (int) maxWidth + '\0' + (int) (ImGui.getFontSize() * 10);
        String cached = SHORTENED.get(key);
        if (cached != null) return cached;
        if (SHORTENED.size() > 1500) SHORTENED.clear();
        String result = s;
        if (textWidth(s) > maxWidth) {
            int lo = 0, hi = s.length();
            while (lo < hi) {
                int mid = (lo + hi + 1) >>> 1;
                if (textWidth(s.substring(0, mid) + "...") <= maxWidth) lo = mid;
                else hi = mid - 1;
            }
            result = s.substring(0, lo).stripTrailing() + "...";
        }
        SHORTENED.put(key, result);
        return result;
    }

    static void box(ImDrawList dl, float x0, float y0, float x1, float y1, int color, float rounding) {
        dl.addRectFilled(x0, y0, x1, y1, color, rounding);
    }

    static void outline(ImDrawList dl, float x0, float y0, float x1, float y1, int color, float rounding) {
        dl.addRect(x0, y0, x1, y1, color, rounding);
    }

    static void label(ImDrawList dl, float x, float y, float[] col, String s) {
        dl.addText(x, y, c(col), s);
    }

    static void tooltip(String text) {
        if (ImGui.isItemHovered() && !ImGui.isMouseDown(0)) ImGui.setTooltip(text);
    }

    // ------------------------------------------------------------------ buttons

    interface Icon {
        void draw(ImDrawList dl, float cx, float cy, float size, int color);
    }

    /**
     * A soft button: the background fades from normal to hover while the mouse is over it (both are RGBA, so a
     * "ghost" button has a transparent normal color). Draws the optional icon and label centered and returns
     * true when it was clicked.
     */
    static boolean button(String id, String label, Icon icon, float x, float y, float w, float h,
                          float[] normal, float[] hover, float[] textColor, float rounding) {
        ImGui.setCursorScreenPos(x, y);
        boolean clicked = ImGui.invisibleButton(id, w, h);
        boolean hovered = ImGui.isItemHovered();
        boolean held = ImGui.isItemActive();
        if (hovered) ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        float t = Anim.smooth("btn." + id, hovered ? (held ? 1.5f : 1f) : 0f, 20f);
        float[] bg = mix(normal, hover, Math.min(t, 1f));
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(x, y, x + w, y + h, c(bg), rounding);
        int fg = c(textColor);
        float unit = ImGui.getFontSize() / 16f;
        float iconSize = Math.min(h * 0.50f, 20f * unit);
        float labelW = label == null || label.isEmpty() ? 0 : textWidth(label);
        float gap = icon != null && labelW > 0 ? 6f * unit : 0;
        float total = (icon != null ? iconSize : 0) + gap + labelW;
        float cx = x + (w - total) / 2f;
        if (icon != null) {
            icon.draw(dl, cx + iconSize / 2f, y + h / 2f, iconSize, fg);
            cx += iconSize + gap;
        }
        if (labelW > 0) dl.addText(cx, y + (h - ImGui.getFontSize()) / 2f, fg, label);
        return clicked;
    }

    static final float[] GHOST = {1f, 1f, 1f, 0f};
    static final float[] GHOST_HOVER = {1f, 1f, 1f, 0.11f};
    static final float[] ACCENT_HOVER = {0.40f, 0.62f, 1.00f, 1f};
    static final float[] CARD_HOVER = {0.145f, 0.158f, 0.190f, 1f};
    static final float[] DANGER = {0.80f, 0.27f, 0.27f, 1f};
    static final float[] DANGER_HOVER = {0.92f, 0.34f, 0.34f, 1f};
    static final float[] AVATAR = {0.22f, 0.66f, 0.55f, 1f};

    // ------------------------------------------------------------------ icons (drawn with lines, no font needed)

    static float thick() {
        return Math.max(1.4f, 1.6f * frameFontSize / 16f);
    }

    static final Icon PLUS = (dl, cx, cy, s, col) -> {
        float r = s * 0.42f;
        dl.addLine(cx - r, cy, cx + r, cy, col, thick());
        dl.addLine(cx, cy - r, cx, cy + r, col, thick());
    };
    static final Icon CROSS = (dl, cx, cy, s, col) -> {
        float r = s * 0.34f;
        dl.addLine(cx - r, cy - r, cx + r, cy + r, col, thick());
        dl.addLine(cx - r, cy + r, cx + r, cy - r, col, thick());
    };
    static final Icon MENU = (dl, cx, cy, s, col) -> {
        float r = s * 0.46f;
        for (int i = -1; i <= 1; i++) dl.addLine(cx - r, cy + i * s * 0.30f, cx + r, cy + i * s * 0.30f, col, thick());
    };
    static final Icon CHECK = (dl, cx, cy, s, col) -> {
        dl.addLine(cx - s * 0.40f, cy + s * 0.02f, cx - s * 0.12f, cy + s * 0.30f, col, thick());
        dl.addLine(cx - s * 0.12f, cy + s * 0.30f, cx + s * 0.42f, cy - s * 0.30f, col, thick());
    };
    static final Icon COPY = (dl, cx, cy, s, col) -> {
        float r = s * 0.30f;
        dl.addRect(cx - r * 0.55f, cy - r * 0.55f, cx + r * 1.45f, cy + r * 1.45f, col, 2f, 0, thick() * 0.85f);
        dl.addRect(cx - r * 1.45f, cy - r * 1.45f, cx + r * 0.55f, cy + r * 0.55f, col, 2f, 0, thick() * 0.85f);
    };
    static final Icon TRASH = (dl, cx, cy, s, col) -> {
        float r = s * 0.36f;
        dl.addLine(cx - r * 1.2f, cy - r * 0.85f, cx + r * 1.2f, cy - r * 0.85f, col, thick());
        dl.addLine(cx - r * 0.35f, cy - r * 1.15f, cx + r * 0.35f, cy - r * 1.15f, col, thick());
        dl.addRect(cx - r * 0.85f, cy - r * 0.85f, cx + r * 0.85f, cy + r * 1.25f, col, 2f, 0, thick() * 0.85f);
        dl.addLine(cx - r * 0.25f, cy - r * 0.35f, cx - r * 0.25f, cy + r * 0.8f, col, thick() * 0.7f);
        dl.addLine(cx + r * 0.25f, cy - r * 0.35f, cx + r * 0.25f, cy + r * 0.8f, col, thick() * 0.7f);
    };
    static final Icon SLIDERS = (dl, cx, cy, s, col) -> {
        float r = s * 0.46f;
        float[] ys = {-0.30f, 0f, 0.30f};
        float[] ks = {-0.20f, 0.25f, -0.05f};
        for (int i = 0; i < 3; i++) {
            float yy = cy + ys[i] * s;
            dl.addLine(cx - r, yy, cx + r, yy, col, thick() * 0.8f);
            dl.addCircleFilled(cx + ks[i] * s, yy, s * 0.10f, col, 12);
        }
    };
    static final Icon ARROW_UP = (dl, cx, cy, s, col) -> {
        float r = s * 0.40f;
        dl.addLine(cx, cy + r, cx, cy - r, col, thick() * 1.2f);
        dl.addLine(cx - r * 0.75f, cy - r * 0.25f, cx, cy - r, col, thick() * 1.2f);
        dl.addLine(cx + r * 0.75f, cy - r * 0.25f, cx, cy - r, col, thick() * 1.2f);
    };
    static final Icon STOP = (dl, cx, cy, s, col) -> {
        float r = s * 0.30f;
        dl.addRectFilled(cx - r, cy - r, cx + r, cy + r, col, 2.5f);
    };
    static final Icon REFRESH = (dl, cx, cy, s, col) -> {
        float r = s * 0.36f;
        int steps = 14;
        float prevX = 0, prevY = 0;
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(-40 + i * (290.0 / steps));
            float px = cx + (float) Math.cos(a) * r, py = cy + (float) Math.sin(a) * r;
            if (i > 0) dl.addLine(prevX, prevY, px, py, col, thick() * 0.9f);
            prevX = px;
            prevY = py;
        }
        double a0 = Math.toRadians(-40);
        float ex = cx + (float) Math.cos(a0) * r, ey = cy + (float) Math.sin(a0) * r;
        dl.addTriangleFilled(ex + r * 0.55f, ey - r * 0.05f, ex - r * 0.30f, ey - r * 0.55f, ex - r * 0.10f, ey + r * 0.45f, col);
    };
    static final Icon CHEVRON_DOWN = (dl, cx, cy, s, col) -> {
        float r = s * 0.28f;
        dl.addLine(cx - r, cy - r * 0.5f, cx, cy + r * 0.5f, col, thick());
        dl.addLine(cx, cy + r * 0.5f, cx + r, cy - r * 0.5f, col, thick());
    };
    static final Icon CHEVRON_UP = (dl, cx, cy, s, col) -> {
        float r = s * 0.28f;
        dl.addLine(cx - r, cy + r * 0.5f, cx, cy - r * 0.5f, col, thick());
        dl.addLine(cx, cy - r * 0.5f, cx + r, cy + r * 0.5f, col, thick());
    };
}
