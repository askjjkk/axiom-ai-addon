package com.example.axiomai.client;

import com.example.axiomai.core.LuaTokens;
import imgui.moulberry92.ImDrawList;
import imgui.moulberry92.ImGui;
import imgui.moulberry92.ImVec2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lays out a script as coloured, soft-wrapped lines and draws it with the draw list (fast, no widgets). */
final class CodeView {
    record Piece(float x, String text, LuaTokens.Type type) {
    }

    record Layout(List<List<Piece>> lines, float lineHeight) {
    }

    private static final ImVec2 TMP = new ImVec2();
    private static final Map<String, Layout> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Layout> eldest) {
            return size() > 48;
        }
    };

    private CodeView() {
    }

    static Layout layout(String code, float maxWidth, boolean cache) {
        String key = code.hashCode() + "|" + code.length() + "|" + (int) maxWidth + "|" + (int) (ImGui.getFontSize() * 10);
        if (cache) {
            Layout hit = CACHE.get(key);
            if (hit != null) return hit;
        }
        float lineHeight = ImGui.getFontSize() * 1.38f;
        List<List<Piece>> out = new ArrayList<>();
        for (String raw : code.replace("\t", "    ").split("\n", -1)) {
            wrapLine(raw, maxWidth, out);
        }
        Layout layout = new Layout(out, lineHeight);
        if (cache) CACHE.put(key, layout);
        return layout;
    }

    private static float width(String s) {
        ImGui.calcTextSize(TMP, s);
        return TMP.x;
    }

    private static void wrapLine(String line, float maxWidth, List<List<Piece>> out) {
        List<Piece> current = new ArrayList<>();
        float x = 0;
        int leading = 0;
        while (leading < line.length() && line.charAt(leading) == ' ') leading++;
        float indent = leading == 0 ? 0 : width(line.substring(0, leading));
        float wrapIndent = indent + width("  ");
        for (LuaTokens.Token token : splitForWrapping(LuaTokens.tokenize(line))) {
            float w = width(token.text());
            boolean space = token.type() == LuaTokens.Type.SPACE;
            if (x + w > maxWidth && x > wrapIndent && !current.isEmpty()) {
                out.add(current);
                current = new ArrayList<>();
                x = wrapIndent;
                if (space) continue;
            }
            current.add(new Piece(x, token.text(), token.type()));
            x += w;
        }
        out.add(current);
    }

    /** Long comments and strings are cut into words (same color), so that they can wrap at spaces. */
    private static List<LuaTokens.Token> splitForWrapping(List<LuaTokens.Token> tokens) {
        List<LuaTokens.Token> out = new ArrayList<>();
        for (LuaTokens.Token t : tokens) {
            boolean splittable = t.type() == LuaTokens.Type.COMMENT || t.type() == LuaTokens.Type.STRING;
            if (!splittable || t.text().indexOf(' ') < 0) {
                out.add(t);
                continue;
            }
            String text = t.text();
            int i = 0;
            while (i < text.length()) {
                int j = i;
                boolean space = text.charAt(i) == ' ';
                while (j < text.length() && (text.charAt(j) == ' ') == space) j++;
                out.add(new LuaTokens.Token(text.substring(i, j), space ? LuaTokens.Type.SPACE : t.type()));
                i = j;
            }
        }
        return out;
    }

    static float[] color(LuaTokens.Type type) {
        return switch (type) {
            case KEYWORD -> new float[]{0.80f, 0.58f, 0.96f, 1f};
            case COMMENT -> new float[]{0.45f, 0.60f, 0.52f, 1f};
            case STRING -> new float[]{0.93f, 0.71f, 0.46f, 1f};
            case NUMBER -> new float[]{0.55f, 0.80f, 1.00f, 1f};
            case TEMPLATE -> new float[]{1.00f, 0.86f, 0.42f, 1f};
            case BUILTIN -> new float[]{0.45f, 0.86f, 0.92f, 1f};
            default -> new float[]{0.86f, 0.88f, 0.92f, 1f};
        };
    }

    /** Draws at most maxLines lines starting at (x, y); the caller sets a clip rectangle if needed. */
    static void draw(ImDrawList dl, Layout layout, float x, float y, int maxLines) {
        int n = Math.min(maxLines, layout.lines().size());
        for (int i = 0; i < n; i++) {
            float ly = y + i * layout.lineHeight();
            for (Piece p : layout.lines().get(i)) {
                if (p.type() == LuaTokens.Type.SPACE) continue;
                dl.addText(x + p.x(), ly, Kit.c(color(p.type())), p.text());
            }
        }
    }
}
