package com.example.axiomai.client;

import com.example.axiomai.core.BrushGenerator;
import com.example.axiomai.core.Chat;
import com.example.axiomai.core.ChatMessage;
import com.example.axiomai.core.ModelProfile;
import imgui.moulberry92.ImDrawList;
import imgui.moulberry92.ImGui;
import imgui.moulberry92.ImVec2;
import imgui.moulberry92.flag.ImDrawFlags;
import imgui.moulberry92.flag.ImGuiCol;
import imgui.moulberry92.flag.ImGuiComboFlags;
import imgui.moulberry92.flag.ImGuiCond;
import imgui.moulberry92.flag.ImGuiInputTextFlags;
import imgui.moulberry92.flag.ImGuiKey;
import imgui.moulberry92.flag.ImGuiMouseCursor;
import imgui.moulberry92.flag.ImGuiStyleVar;
import imgui.moulberry92.flag.ImGuiWindowFlags;
import imgui.moulberry92.type.ImString;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The modern chat look: sidebar with grouped chats, message bubbles, code cards with syntax colors,
 * a rounded input bar and smooth animations. All state lives in ChatUi; this class only draws and reacts.
 */
final class ChatView {
    private static final int COLLAPSED_LINES = 16;
    private static final double TYPE_SPEED = 2600; // characters per second of the "typing" reveal

    private final ChatUi ui;
    private final ImVec2 tmp = new ImVec2();
    private final Map<String, Double> seen = new HashMap<>();
    private final Map<Long, Double> revealStart = new HashMap<>();
    private final Map<String, Boolean> expanded = new HashMap<>();
    private final Map<String, Float> heights = new HashMap<>();   // measured height of every message block
    private final ImString renameBuffer = new ImString(120);
    private boolean renameFocus;
    private boolean openDeletePopup;
    private boolean openSettingsPopup;
    private float gearX, gearY;
    private boolean follow = true;
    private Chat lastSelected;
    private double switchedAt = -10;
    private String copiedKey = "";
    private double copiedAt = -10;
    private float fade = 1f;
    private String currentKey = "";
    private String ownNameKey = "";                    // message whose "own name" input is open
    private final ImString ownName = new ImString(120);
    private boolean ownNameFocus;

    ChatView(ChatUi ui) {
        this.ui = ui;
    }

    private boolean toggleModernInFrame;

    /** Test only: switches the modern look off in the middle of the next frame, like a click on the checkbox. */
    void testToggleModernNextFrame() {
        toggleModernInFrame = true;
    }

    void testOpenSettings() {
        openSettingsPopup = true;
    }

    void testOpenDelete() {
        openDeletePopup = true;
    }

    private static float unit() {
        return ImGui.getFontSize() / 16f;
    }

    // ------------------------------------------------------------------ whole window

    void draw() {
        Kit.frameFontSize = ImGui.getFontSize();
        float u = unit();
        ImVec2 avail = ImGui.getContentRegionAvail();
        float w = avail.x;
        float h = avail.y;
        detectChatSwitch();

        float sidebarFull = Math.max(190f * u, Math.min(300f * u, w * 0.25f));
        float openness = Anim.smooth("ai.sidebar", ui.sidebarOpen ? 1f : 0f, 14f);
        float sidebarW = sidebarFull * openness;
        if (sidebarW > 2f) {
            sidebar(sidebarW, h, u);
            ImGui.sameLine(0f, 0f);
        }
        main(Math.max(50f, w - sidebarW), h, u);
        popups(u);
        if (toggleModernInFrame) {
            toggleModernInFrame = false;
            ui.modern = false;
        }
    }

    private void detectChatSwitch() {
        if (ui.selected == lastSelected) return;
        lastSelected = ui.selected;
        switchedAt = ImGui.getTime();
        follow = true;
        if (ui.selected != null) {
            int i = 0;
            for (ChatMessage ignored : ui.selected.messages) seen.put(ui.selected.id + "#" + i++, -100.0);
        }
    }

    private void startChild(float[] bg) {
        ImGui.pushStyleColor(ImGuiCol.ChildBg, bg[0], bg[1], bg[2], bg[3]);
    }

    private void applyScale() {
        ImGui.setWindowFontScale(ui.fontScale[0]);
    }

    // ------------------------------------------------------------------ sidebar

    private void sidebar(float w, float h, float u) {
        startChild(Kit.BG_SIDEBAR);
        ImGui.beginChild("##aisb", w, h, 0, ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
        applyScale();
        ImDrawList dl = ImGui.getWindowDrawList();
        float x0 = ImGui.getWindowPosX();
        float y0 = ImGui.getWindowPosY();
        float pad = 12f * u;
        float iw = Math.max(20f, w - 2 * pad);
        float y = y0 + pad;

        if (Kit.button("##ainew", Tr.t("New chat"), Kit.PLUS, x0 + pad, y, iw, 36f * u,
                Kit.ACCENT, Kit.ACCENT_HOVER, Kit.WHITE, 10f * u)) {
            ui.newChat();
        }
        y += 46f * u;

        ImGui.setCursorScreenPos(x0 + pad, y);
        ImGui.setNextItemWidth(iw);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 10f * u);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 10f * u, 7f * u);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.115f, 0.125f, 0.155f, 1f);
        ImGui.inputTextWithHint("##aisearch", Tr.t("Search chats..."), ui.search);
        ImGui.popStyleColor();
        ImGui.popStyleVar(2);
        y += 42f * u;

        float footerH = 52f * u;
        float listH = Math.max(20f, h - (y - y0) - footerH);
        ImGui.setCursorScreenPos(x0, y);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0f, 0f, 0f, 0f);
        ImGui.beginChild("##ailist", w, listH, 0);
        applyScale();
        chatList(unit());
        ImGui.endChild();
        ImGui.popStyleColor();

        if (ui.retentionDays > 0) {
            ImGui.setCursorScreenPos(x0 + pad, y0 + h - footerH + 8f * u);
            ImGui.pushStyleColor(ImGuiCol.Text, Kit.DIM[0], Kit.DIM[1], Kit.DIM[2], 0.85f);
            ImGui.pushTextWrapPos(ImGui.getCursorPosX() + iw);
            ImGui.textUnformatted(Tr.t("Chats with no activity for %d days are deleted automatically.", ui.retentionDays));
            ImGui.popTextWrapPos();
            ImGui.popStyleColor();
        }
        ImGui.endChild();
        ImGui.popStyleColor();
    }

    private void chatList(float u) {
        String query = ui.search.get().trim().toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now();
        String lastGroup = null;
        for (Chat chat : ui.chats) {
            if (!query.isEmpty() && !matches(chat, query)) continue;
            String group = groupOf(chat, today);
            if (!group.equals(lastGroup)) {
                lastGroup = group;
                float gy = ImGui.getCursorScreenPosY();
                Kit.label(ImGui.getWindowDrawList(), ImGui.getWindowPosX() + 18f * u, gy + 8f * u, Kit.DIM, group);
                ImGui.dummy(1f, ImGui.getTextLineHeight() + 14f * u);
            }
            row(chat, u);
        }
        ImGui.dummy(1f, 8f * u);
    }

    private void row(Chat chat, float u) {
        ImDrawList dl = ImGui.getWindowDrawList();
        float rx = ImGui.getWindowPosX() + 8f * u;
        float rw = Math.max(40f, ImGui.getContentRegionAvailX() - 16f * u);
        float rh = ImGui.getTextLineHeight() + 18f * u;
        float ry = ImGui.getCursorScreenPosY();

        if (chat.id.equals(ui.renamingId)) {
            renameRow(chat, rx, ry, rw, rh, u);
            return;
        }

        ImGui.setCursorScreenPos(rx, ry);
        boolean clicked = ImGui.invisibleButton("##row" + chat.id, rw, rh);
        boolean hovered = ImGui.isItemHovered();
        boolean selected = chat == ui.selected;
        ImGui.getMousePos(tmp);
        boolean deleteHot = hovered && tmp.x > rx + rw - 32f * u;
        if (hovered) ImGui.setMouseCursor(ImGuiMouseCursor.Hand);

        float t = Anim.smooth("row." + chat.id, hovered ? 1f : 0f, 18f);
        if (selected) {
            dl.addRectFilled(rx, ry, rx + rw, ry + rh, Kit.c(Kit.ACCENT, 0.20f), 9f * u);
            dl.addRectFilled(rx, ry + 8f * u, rx + 3f * u, ry + rh - 8f * u, Kit.c(Kit.ACCENT), 2f);
        } else if (t > 0.01f) {
            dl.addRectFilled(rx, ry, rx + rw, ry + rh, Kit.c(Kit.WHITE, 0.075f * t), 9f * u);
        }

        String age = age(chat.lastActive);
        float ageW = Kit.textWidth(age);
        float titleMax = rw - 28f * u - (hovered ? 28f * u : ageW + 8f * u);
        String title = Kit.ellipsize(chat.title().replace('\n', ' '), titleMax);
        float ty = ry + (rh - ImGui.getFontSize()) / 2f;
        Kit.label(dl, rx + 14f * u, ty, selected ? Kit.TEXT : Kit.mix(Kit.TEXT, Kit.DIM, 0.25f), title);
        if (hovered) {
            Kit.CROSS.draw(dl, rx + rw - 18f * u, ry + rh / 2f, 18f * u, Kit.c(deleteHot ? Kit.RED : Kit.DIM));
        } else {
            Kit.label(dl, rx + rw - 14f * u - ageW, ty, Kit.DIM, age);
        }
        if (clicked) {
            if (deleteHot) {
                ui.pendingDelete = chat;
                openDeletePopup = true;
            } else {
                ui.selectChat(chat);
            }
        }
        if (hovered && !deleteHot && ImGui.isMouseDoubleClicked(0)) {
            ui.renamingId = chat.id;
            renameBuffer.set(chat.title());
            renameFocus = true;
        }
    }

    private void renameRow(Chat chat, float rx, float ry, float rw, float rh, float u) {
        ImGui.setCursorScreenPos(rx, ry + 2f * u);
        ImGui.setNextItemWidth(rw);
        if (renameFocus) {
            ImGui.setKeyboardFocusHere();
            renameFocus = false;
        }
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 8f * u);
        boolean enter = ImGui.inputText("##ren" + chat.id, renameBuffer,
                ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll);
        ImGui.popStyleVar();
        boolean cancel = ImGui.isKeyPressed(ImGuiKey.Escape);
        boolean lostFocus = ImGui.isItemDeactivated() && !enter;
        if (cancel) {
            ui.renamingId = null;
        } else if (enter || lostFocus) {
            ui.renameChat(chat, renameBuffer.get());
        }
        ImGui.setCursorScreenPos(rx, ry + rh);
        ImGui.dummy(1f, 1f);
    }

    private boolean matches(Chat chat, String query) {
        if (chat.title().toLowerCase(Locale.ROOT).contains(query)) return true;
        for (ChatMessage m : chat.messages) {
            if (m.isUser() && m.text().toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return false;
    }

    private static String groupOf(Chat chat, LocalDate today) {
        LocalDate day = Instant.ofEpochMilli(chat.lastActive).atZone(ZoneId.systemDefault()).toLocalDate();
        if (!day.isBefore(today)) return Tr.t("Today");
        if (day.equals(today.minusDays(1))) return Tr.t("Yesterday");
        return Tr.t("Earlier");
    }

    private static String age(long timestamp) {
        long minutes = Math.max(0, (System.currentTimeMillis() - timestamp) / 60_000L);
        if (minutes < 1) return Tr.t("now");
        if (minutes < 60) return minutes + " " + Tr.t("min");
        long hours = minutes / 60;
        if (hours < 48) return hours + " " + Tr.t("h");
        return (hours / 24) + " " + Tr.t("d");
    }

    // ------------------------------------------------------------------ main pane

    private void main(float w, float h, float u) {
        startChild(Kit.BG_MAIN);
        ImGui.beginChild("##aimain", w, h, 0, ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
        applyScale();
        float x0 = ImGui.getWindowPosX();
        float y0 = ImGui.getWindowPosY();
        float headerH = 48f * u;
        float barH = Math.max(72f * u, ImGui.getTextLineHeight() * 3f + 26f * u);
        float areaH = barH + 40f * u;

        header(w, x0, y0, headerH, u);
        ImGui.setCursorScreenPos(x0, y0 + headerH);
        messages(w, Math.max(40f, h - headerH - areaH), u);
        inputBar(x0, y0 + h - areaH + 6f * u, w, barH, u);

        ImGui.endChild();
        ImGui.popStyleColor();
    }

    private void header(float w, float x0, float y0, float hh, float u) {
        ImDrawList dl = ImGui.getWindowDrawList();
        float s = 32f * u;
        float by = y0 + (hh - s) / 2f;
        if (Kit.button("##aimenu", null, Kit.MENU, x0 + 10f * u, by, s, s, Kit.GHOST, Kit.GHOST_HOVER, Kit.DIM, 8f * u)) {
            ui.sidebarOpen = !ui.sidebarOpen;
        }
        Kit.tooltip(Tr.t("Chats"));

        float right = x0 + w - 10f * u;
        if (Kit.button("##aigear", null, Kit.SLIDERS, right - s, by, s, s, Kit.GHOST, Kit.GHOST_HOVER, Kit.DIM, 8f * u)) {
            openSettingsPopup = true;
        }
        gearX = right;
        gearY = by + s + 4f * u;
        Kit.tooltip(Tr.t("Settings"));
        right -= s + 4f * u;
        if (ui.selected != null) {
            if (Kit.button("##aitrash", null, Kit.TRASH, right - s, by, s, s, Kit.GHOST, Kit.GHOST_HOVER, Kit.DIM, 8f * u)) {
                ui.pendingDelete = ui.selected;
                openDeletePopup = true;
            }
            Kit.tooltip(Tr.t("Delete"));
            right -= s + 4f * u;
        }

        if (ui.cachedConfig != null && ui.cachedConfig.hasModelChoice()) {
            float cw = 200f * u;
            right -= 6f * u;
            ImGui.setCursorScreenPos(right - cw, by + 1f * u);
            ImGui.setNextItemWidth(cw);
            ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 9f * u);
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 10f * u, 6f * u);
            ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.115f, 0.125f, 0.155f, 1f);
            ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, 0.14f, 0.15f, 0.19f, 1f);
            if (ImGui.beginCombo("##aimodel", ui.selectedModel, ImGuiComboFlags.NoArrowButton)) {
                for (ModelProfile m : ui.cachedConfig.models) {
                    String label = m.name() + (m.priceText().isEmpty() ? "" : "   (" + m.priceText() + ")");
                    if (ImGui.selectable(label + "##model_" + m.name(), m.name().equals(ui.selectedModel))) {
                        ui.selectedModel = m.name();
                    }
                }
                ImGui.endCombo();
            }
            Kit.CHEVRON_DOWN.draw(dl, right - 16f * u, by + 1f * u + ImGui.getFrameHeight() / 2f, 18f * u, Kit.c(Kit.DIM));
            ImGui.popStyleColor(2);
            ImGui.popStyleVar(2);
            right -= cw + 10f * u;
            if (ui.sessionCost > 0) {
                String cost = Tr.t("spent this session: about %.2f RUB", ui.sessionCost);
                float tw = Kit.textWidth(cost);
                Kit.label(dl, right - tw, y0 + (hh - ImGui.getFontSize()) / 2f, Kit.DIM, cost);
                right -= tw + 12f * u;
            }
        }

        float left = x0 + 10f * u + s + 12f * u;
        String title = ui.selected == null ? Tr.t("New chat") : ui.selected.title().replace('\n', ' ');
        Kit.label(dl, left, y0 + (hh - ImGui.getFontSize()) / 2f, Kit.TEXT, Kit.ellipsize(title, Math.max(40f, right - left - 8f * u)));
        dl.addLine(x0, y0 + hh, x0 + w, y0 + hh, Kit.c(Kit.BORDER), 1f);
    }

    // ------------------------------------------------------------------ messages

    private void messages(float w, float h, float u) {
        float switchFade = Anim.easeOut((float) ((ImGui.getTime() - switchedAt) / 0.25));
        fade = 0.30f + 0.70f * switchFade;
        ImGui.pushStyleVar(ImGuiStyleVar.Alpha, fade);
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarSize, 9f * u);
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarRounding, 6f * u);
        startChild(Kit.BG_MAIN);
        ImGui.beginChild("##aimsgs", w, h, 0);
        applyScale();
        float uu = unit();
        float availW = ImGui.getContentRegionAvailX();
        float colW = Math.min(availW - 32f * uu, 820f * uu);
        float colX = ImGui.getWindowPosX() + (availW - colW) / 2f;
        ImGui.dummy(1f, 14f * uu);

        Chat chat = ui.selected;
        if (chat == null || chat.messages.isEmpty()) {
            welcome(colX, colW, h, uu);
        } else {
            List<ChatMessage> list = chat.messages;
            int last = list.size() - 1;
            int i = 0;
            for (ChatMessage m : list) {
                ImGui.pushID(i);
                message(chat, i, m, i == last, colX, colW, uu);
                ImGui.popID();
                i++;
            }
        }
        if (chat != null && !ui.busy && ui.error == null && !chat.messages.isEmpty()
                && chat.messages.get(chat.messages.size() - 1).isUser()) {
            unansweredCard(chat.messages.get(chat.messages.size() - 1).text(), colX, colW, uu);
        }
        if (ui.busy) typing(colX, colW, uu);
        if (ui.error != null) errorCard(colX, colW, uu);
        ImGui.dummy(1f, 28f * uu);
        autoScroll();

        ImGui.endChild();
        ImGui.popStyleColor();
        ImGui.popStyleVar(3);
    }

    private void autoScroll() {
        if (ui.scrollRequest) {
            follow = true;
            ui.scrollRequest = false;
        }
        float max = ImGui.getScrollMaxY();
        float y = ImGui.getScrollY();
        if (ImGui.isWindowHovered() && ImGui.getIO().getMouseWheel() > 0) follow = false;
        if (y >= max - 3f) follow = true;
        if (follow && max > y + 0.5f) {
            ImGui.setScrollY(y + (max - y) * (1f - (float) Math.exp(-13f * Anim.dt())));
        }
    }

    private float appear(String key) {
        double now = ImGui.getTime();
        double first = seen.computeIfAbsent(key, k -> now);
        return Anim.easeOut((float) ((now - first) / 0.30));
    }

    private void message(Chat chat, int index, ChatMessage m, boolean isLast, float colX, float colW, float u) {
        String key = chat.id + "#" + index;

        // Messages that are far outside the visible area are not drawn: only their (remembered) height is reserved.
        String sizeKey = key + "|" + (int) colW + "|" + (int) (ImGui.getFontSize() * 10) + "|"
                + expanded.getOrDefault(key, false) + "|" + isLast + "|" + ui.busy + "|" + ui.fresh.contains(m.time())
                + "|" + key.equals(ownNameKey) + "|" + m.preset() + "|" + (ui.selected != null && !ui.selected.presetFile.isEmpty());
        Float known = heights.get(sizeKey);
        float startY = ImGui.getCursorScreenPosY();
        float spacing = ImGui.getTextLineHeightWithSpacing() - ImGui.getTextLineHeight();
        if (known != null && !ImGui.isRectVisible(colW, known + 40f * u)) {
            ImGui.dummy(colW, Math.max(1f, known - spacing));
            return;
        }

        float a = appear(key);
        ImGui.pushStyleVar(ImGuiStyleVar.Alpha, fade * (0.15f + 0.85f * a));
        float slide = (1f - a) * 10f * u;
        if (m.isUser()) userMessage(m, colX, colW, u, slide);
        else aiMessage(chat, key, m, isLast, colX, colW, u, slide);
        ImGui.popStyleVar();
        if (heights.size() > 3000) heights.clear();
        heights.put(sizeKey, ImGui.getCursorScreenPosY() - startY);
    }

    private void userMessage(ChatMessage m, float colX, float colW, float u, float slide) {
        ImDrawList dl = ImGui.getWindowDrawList();
        float y0 = ImGui.getCursorScreenPosY();
        float padX = 16f * u, padY = 11f * u;
        float maxBubble = colW * 0.80f;
        float wrap = maxBubble - 2 * padX;
        ImGui.calcTextSize(tmp, m.text(), false, wrap);
        float bw = Math.max(44f * u, Math.min(wrap, tmp.x) + 2 * padX);
        float bh = tmp.y + 2 * padY;
        float x1 = colX + colW;
        float x0 = x1 - bw;
        float y = y0 + slide;
        dl.addRectFilled(x0, y, x1, y + bh, Kit.c(Kit.BUBBLE), 17f * u);
        ImGui.setCursorScreenPos(x0 + padX, y + padY);
        ImGui.pushTextWrapPos(ImGui.getCursorPosX() + wrap);
        ImGui.textUnformatted(m.text());
        ImGui.popTextWrapPos();

        // copying: a small button appears next to the bubble, and the right mouse button opens a menu
        ImGui.setCursorScreenPos(x0, y);
        ImGui.invisibleButton("##userbubble", bw, bh);
        boolean overBubble = ImGui.isItemHovered();
        copyMenu("##ctxuser", m.text());
        if (overBubble || ImGui.isMouseHoveringRect(x0 - 44f * u, y, x0, y + bh)) {
            float cs = 28f * u;
            if (Kit.button("##copyuser", null, Kit.COPY, x0 - cs - 8f * u, y + (bh - cs) / 2f, cs, cs,
                    Kit.GHOST, Kit.GHOST_HOVER, Kit.DIM, 8f * u)) {
                ImGui.setClipboardText(m.text());
            }
            Kit.tooltip(Tr.t("Copy"));
        }
        ImGui.setCursorScreenPos(colX, y0);
        ImGui.dummy(colW, bh + 16f * u);
    }

    private void aiMessage(Chat chat, String key, ChatMessage m, boolean isLast, float colX, float colW, float u, float slide) {
        ImDrawList dl = ImGui.getWindowDrawList();
        float startY = ImGui.getCursorScreenPosY();
        float y = startY + slide;
        float av = 28f * u;
        float cx = colX + 38f * u;
        float cw = colW - 38f * u;

        avatar(dl, colX + av / 2f, y + av / 2f + 2f * u, av);
        String name = "AI Create";
        Kit.label(dl, cx, y + 5f * u, Kit.TEXT, name);
        String model = modelOf(m.meta());
        if (!model.isEmpty()) Kit.label(dl, cx + Kit.textWidth(name) + 10f * u, y + 5f * u, Kit.DIM, model);
        y += 36f * u;

        // what the AI understood
        String mode = planValue(m.text(), "MODE");
        String action = planValue(m.text(), "ACTION");
        if (!mode.isEmpty() || !action.isEmpty()) {
            float pad = 11f * u;
            float badgeW = mode.isEmpty() ? 0 : Kit.textWidth(mode) + 16f * u;
            float textX = cx + pad + badgeW + (badgeW > 0 ? 10f * u : 0);
            float wrap = Math.max(60f, cx + cw - pad - textX);
            ImGui.calcTextSize(tmp, action, false, wrap);
            float hh = Math.max(tmp.y, ImGui.getFontSize()) + 2 * pad;
            Kit.box(dl, cx, y, cx + cw, y + hh, Kit.c(Kit.BG_CARD), 11f * u);
            if (badgeW > 0) {
                float[] mc = modeColor(mode);
                dl.addRectFilled(cx + pad, y + pad - 2f * u, cx + pad + badgeW, y + pad + ImGui.getFontSize() + 2f * u, Kit.c(mc, 0.20f), 6f * u);
                Kit.label(dl, cx + pad + 8f * u, y + pad, mc, mode);
            }
            ImGui.setCursorScreenPos(textX, y + pad);
            ImGui.pushStyleColor(ImGuiCol.Text, 0.78f, 0.81f, 0.86f, 1f);
            ImGui.pushTextWrapPos(ImGui.getCursorPosX() + wrap);
            ImGui.textUnformatted(action);
            ImGui.popTextWrapPos();
            ImGui.popStyleColor();
            ImGui.setCursorScreenPos(cx, y);
            ImGui.invisibleButton("##planarea", cw, hh);
            copyMenu("##ctxplan", action);
            y += hh + 10f * u;
        }

        // the script
        String code = withoutNameLine(m.text());
        boolean typing = ui.fresh.contains(m.time());
        String shown = code;
        if (typing) {
            double now = ImGui.getTime();
            double start = revealStart.computeIfAbsent(m.time(), k -> now);
            int chars = (int) ((now - start) * TYPE_SPEED);
            if (chars >= code.length()) {
                ui.fresh.remove(m.time());
                revealStart.remove(m.time());
                typing = false;
            } else {
                shown = code.substring(0, Math.max(0, chars));
            }
        }
        currentKey = key;
        y = codeCard(dl, key, code, shown, typing, cx, cw, y, u);

        if (!typing) {
            y = footer(dl, m, isLast, cx, cw, y, u);
        }
        ImGui.setCursorScreenPos(colX, startY);
        ImGui.dummy(colW, Math.max(10f, y - startY) + 6f * u);
    }

    private float codeCard(ImDrawList dl, String key, String code, String shown, boolean typing,
                           float cx, float cw, float y, float u) {
        float pad = 14f * u;
        CodeView.Layout layout = CodeView.layout(shown, cw - 2 * pad, !typing);
        int total = layout.lines().size();
        int fullLines = typing ? total : total;
        boolean canCollapse = fullLines > COLLAPSED_LINES && !typing;
        boolean open = typing || expanded.getOrDefault(key, false);
        int visible = open || !canCollapse ? total : COLLAPSED_LINES;
        float targetH = visible * layout.lineHeight() + 22f * u;
        float bodyH = typing ? targetH : Anim.smooth("code." + key, targetH, 14f);
        float headH = 34f * u;

        dl.addRectFilled(cx, y, cx + cw, y + headH, Kit.c(Kit.BG_CARD), 11f * u, ImDrawFlags.RoundCornersTop);
        Kit.label(dl, cx + pad, y + (headH - ImGui.getFontSize()) / 2f, Kit.DIM,
                "Lua  -  " + countLines(code) + " " + Tr.t("lines"));

        float bh = 26f * u;
        float by = y + (headH - bh) / 2f;
        boolean justCopied = key.equals(copiedKey) && ImGui.getTime() - copiedAt < 1.6;
        String copyLabel = justCopied ? Tr.t("Copied") : Tr.t("Copy");
        float copyW = Kit.textWidth(copyLabel) + 44f * u;
        float bx = cx + cw - copyW - 6f * u;
        if (Kit.button("##copy" + key, copyLabel, justCopied ? Kit.CHECK : Kit.COPY, bx, by, copyW, bh,
                Kit.GHOST, Kit.GHOST_HOVER, justCopied ? Kit.GREEN : Kit.DIM, 7f * u)) {
            ImGui.setClipboardText(code);
            copiedKey = key;
            copiedAt = ImGui.getTime();
        }
        if (canCollapse) {
            String toggle = open ? Tr.t("Collapse") : Tr.t("Show all %d lines", total);
            float tw = Kit.textWidth(toggle) + 44f * u;
            bx -= tw + 4f * u;
            if (Kit.button("##exp" + key, toggle, open ? Kit.CHEVRON_UP : Kit.CHEVRON_DOWN, bx, by, tw, bh,
                    Kit.GHOST, Kit.GHOST_HOVER, Kit.DIM, 7f * u)) {
                expanded.put(key, !open);
            }
        }

        float top = y + headH;
        dl.addRectFilled(cx, top, cx + cw, top + bodyH, Kit.c(Kit.BG_CODE), 11f * u, ImDrawFlags.RoundCornersBottom);
        dl.pushClipRect(cx, top, cx + cw, top + bodyH, true);
        int linesToDraw = Math.min(total, (int) Math.ceil(bodyH / layout.lineHeight()) + 1);   // only what is visible
        CodeView.draw(dl, layout, cx + pad, top + 11f * u, linesToDraw);
        if (canCollapse && !open) {
            float[] clear = {Kit.BG_CODE[0], Kit.BG_CODE[1], Kit.BG_CODE[2], 0f};
            dl.addRectFilledMultiColor(cx, top + bodyH - 34f * u, cx + cw, top + bodyH, Kit.c(clear), Kit.c(clear),
                    Kit.c(Kit.BG_CODE), Kit.c(Kit.BG_CODE));
        }
        dl.popClipRect();
        ImGui.setCursorScreenPos(cx, top);
        ImGui.invisibleButton("##codearea" + key, cw, Math.max(1f, bodyH));
        copyMenu("##ctxcode" + key, code);
        return top + bodyH + 10f * u;
    }

    private float footer(ImDrawList dl, ChatMessage m, boolean isLast, float cx, float cw, float y, float u) {
        float fs = ImGui.getFontSize();
        if (!m.preset().isEmpty()) {
            String text = Kit.ellipsize(Tr.t(m.updated() ? "updated preset: %s" : "saved as preset: %s", m.preset()), cw - 44f * u);
            float chipW = Kit.textWidth(text) + 40f * u;
            float chipH = fs + 12f * u;
            dl.addRectFilled(cx, y, cx + chipW, y + chipH, Kit.c(Kit.GREEN, 0.13f), chipH / 2f);
            Kit.CHECK.draw(dl, cx + 16f * u, y + chipH / 2f, 16f * u, Kit.c(Kit.GREEN));
            Kit.label(dl, cx + 28f * u, y + 6f * u, Kit.GREEN, text);
            y += chipH + 7f * u;
            if (isLast && !ui.busy && ui.selected != null && !ui.selected.presetFile.isEmpty()) {
                y = nameRow(dl, key(m), m, cx, cw, y, u);
            }
        }
        String meta = m.meta().isEmpty() ? "" : Tr.meta(metaAfterModel(m.meta()));
        if (!meta.isEmpty()) {
            Kit.label(dl, cx + 2f * u, y, Kit.DIM, Kit.ellipsize(meta, cw));
            y += fs + 8f * u;
        }
        String rawNote = m.note();
        String note = Tr.note(rawNote);
        if (!rawNote.isEmpty()) {
            boolean good = rawNote.startsWith("Auto-check passed") || rawNote.startsWith("Auto-check: ");
            float[] col = good ? Kit.GREEN : Kit.AMBER;
            ImGui.calcTextSize(tmp, note, false, cw);
            ImGui.setCursorScreenPos(cx + 2f * u, y);
            ImGui.pushStyleColor(ImGuiCol.Text, col[0], col[1], col[2], 1f);
            ImGui.pushTextWrapPos(ImGui.getCursorPosX() + cw);
            ImGui.textUnformatted(note);
            ImGui.popTextWrapPos();
            ImGui.popStyleColor();
            y += tmp.y + 8f * u;
        }
        if (isLast && !ui.busy) {
            boolean choice = ui.cachedConfig != null && ui.cachedConfig.hasModelChoice();
            String label = choice ? Tr.t("Regenerate with %s", ui.selectedModel) : Tr.t("Regenerate");
            float bw = Kit.textWidth(label) + 46f * u;
            float bh = 30f * u;
            if (Kit.button("##regen", label, Kit.REFRESH, cx, y + 2f * u, bw, bh, Kit.GHOST, Kit.GHOST_HOVER, Kit.DIM, 9f * u)) {
                ui.regenerate();
            }
            Kit.tooltip(Tr.t("same request, new answer (costs again)"));
            y += bh + 6f * u;
        }
        return y;
    }

    private String key(ChatMessage m) {
        return currentKey;
    }

    /** Right mouse button on the last item opens a small menu with "Copy". */
    private void copyMenu(String id, String text) {
        if (ImGui.isItemHovered() && ImGui.isMouseClicked(1)) ImGui.openPopup(id);
        if (ImGui.beginPopup(id)) {
            if (ImGui.selectable(Tr.t("Copy") + "##" + id)) ImGui.setClipboardText(text);
            ImGui.endPopup();
        }
    }

    /** "Name ideas: [A] [B] [C] [Own name...]" under the answer; a click renames the preset. */
    private float nameRow(ImDrawList dl, String key, ChatMessage m, float cx, float cw, float y, float u) {
        float fs = ImGui.getFontSize();
        float rowH = 28f * u;
        float gap = 6f * u;
        String label = Tr.t("Name ideas:");
        float x = cx;
        Kit.label(dl, x + 2f * u, y + (rowH - fs) / 2f, Kit.DIM, label);
        x += Kit.textWidth(label) + 12f * u;

        List<String> chips = new java.util.ArrayList<>(BrushGenerator.altNamesOf(m.text()));
        chips.add(null); // the "own name" chip
        for (String alt : chips) {
            String text = alt == null ? Tr.t("Own name...") : alt;
            float w = Kit.textWidth(text) + 28f * u;
            if (x + w > cx + cw && x > cx) {
                x = cx;
                y += rowH + gap;
            }
            boolean own = alt == null;
            if (Kit.button("##alt" + key + text, text, null, x, y, w, rowH, Kit.BG_CARD, Kit.CARD_HOVER,
                    own ? Kit.DIM : Kit.TEXT, rowH / 2f)) {
                if (own) {
                    ownNameKey = key.equals(ownNameKey) ? "" : key;
                    ownName.set(ui.selected == null ? "" : ui.selected.presetName);
                    ownNameFocus = true;
                } else {
                    ui.renamePreset(alt);
                    ownNameKey = "";
                }
            }
            dl.addRect(x, y, x + w, y + rowH, Kit.c(Kit.BORDER), rowH / 2f);
            x += w + gap;
        }
        y += rowH + 6f * u;

        if (key.equals(ownNameKey)) {
            float iw = Math.min(320f * u, cw - 110f * u);
            ImGui.setCursorScreenPos(cx, y);
            ImGui.setNextItemWidth(iw);
            if (ownNameFocus) {
                ImGui.setKeyboardFocusHere();
                ownNameFocus = false;
            }
            ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 9f * u);
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 10f * u, 6f * u);
            boolean enter = ImGui.inputText("##ownname" + key, ownName,
                    ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll);
            ImGui.popStyleVar(2);
            float bw = Kit.textWidth(Tr.t("Apply")) + 28f * u;
            boolean apply = Kit.button("##ownapply" + key, Tr.t("Apply"), null, cx + iw + 8f * u, y, bw, 30f * u,
                    Kit.ACCENT, Kit.ACCENT_HOVER, Kit.WHITE, 9f * u);
            if (enter || apply) {
                ui.renamePreset(ownName.get());
                ownNameKey = "";
            } else if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
                ownNameKey = "";
            }
            y += 38f * u;
        }
        return y + 2f * u;
    }

    /** Shown when the last request has no answer (it was stopped, or the game was closed in the meantime). */
    private void unansweredCard(String requestText, float colX, float colW, float u) {
        ImDrawList dl = ImGui.getWindowDrawList();
        float y0 = ImGui.getCursorScreenPosY();
        float pad = 14f * u;
        float wrap = colW - 2 * pad - 8f * u;
        String text = Tr.t("This request has no answer yet (it may have been stopped).");
        ImGui.calcTextSize(tmp, text, false, wrap);
        float bh = 32f * u;
        float hh = pad + tmp.y + 12f * u + bh + pad;
        dl.addRectFilled(colX, y0, colX + colW, y0 + hh, Kit.c(Kit.BG_CARD), 12f * u);
        dl.addRectFilled(colX, y0 + 10f * u, colX + 3f * u, y0 + hh - 10f * u, Kit.c(Kit.AMBER), 2f);
        ImGui.setCursorScreenPos(colX + pad + 4f * u, y0 + pad);
        ImGui.pushStyleColor(ImGuiCol.Text, Kit.DIM[0], Kit.DIM[1], Kit.DIM[2], 1f);
        ImGui.pushTextWrapPos(ImGui.getCursorPosX() + wrap);
        ImGui.textUnformatted(text);
        ImGui.popTextWrapPos();
        ImGui.popStyleColor();
        float by = y0 + hh - pad - bh;
        float bx = colX + pad + 4f * u;
        String resend = Tr.t("Resend");
        float rw = Kit.textWidth(resend) + 46f * u;
        if (Kit.button("##resend", resend, Kit.REFRESH, bx, by, rw, bh, Kit.ACCENT, Kit.ACCENT_HOVER, Kit.WHITE, 10f * u)) {
            ui.retry();
        }
        bx += rw + 8f * u;
        String copy = Tr.t("Copy request");
        float cw2 = Kit.textWidth(copy) + 46f * u;
        if (Kit.button("##copyreq", copy, Kit.COPY, bx, by, cw2, bh, Kit.GHOST_HOVER, Kit.CARD_HOVER, Kit.DIM, 10f * u)) {
            ImGui.setClipboardText(requestText);
        }
        ImGui.setCursorScreenPos(colX, y0);
        ImGui.dummy(colW, hh + 14f * u);
    }

    private void avatar(ImDrawList dl, float cx, float cy, float size) {
        dl.addCircleFilled(cx, cy, size / 2f, Kit.c(Kit.AVATAR), 28);
        float w = Kit.textWidth("AI");
        Kit.label(dl, cx - w / 2f, cy - ImGui.getFontSize() / 2f, Kit.WHITE, "AI");
    }

    private void typing(float colX, float colW, float u) {
        ImDrawList dl = ImGui.getWindowDrawList();
        float y0 = ImGui.getCursorScreenPosY();
        float av = 28f * u;
        avatar(dl, colX + av / 2f, y0 + av / 2f + 2f * u, av);
        String stage = stageText(ui.stage);
        float bx = colX + 38f * u;
        float bw = 66f * u + (stage.isEmpty() ? 0 : Kit.textWidth(stage) + 10f * u);
        float bh = 34f * u;
        dl.addRectFilled(bx, y0 + 2f * u, bx + bw, y0 + 2f * u + bh, Kit.c(Kit.BG_CARD), bh / 2f);
        double t = ImGui.getTime();
        for (int k = 0; k < 3; k++) {
            double phase = t * 5.0 - k * 0.75;
            float pulse = (float) (0.5 + 0.5 * Math.sin(phase));
            float r = (2.4f + 1.6f * pulse) * u;
            dl.addCircleFilled(bx + 20f * u + k * 13f * u, y0 + 2f * u + bh / 2f, r, Kit.c(Kit.ACCENT, 0.45f + 0.55f * pulse), 14);
        }
        if (!stage.isEmpty()) {
            Kit.label(dl, bx + 62f * u, y0 + 2f * u + (bh - ImGui.getFontSize()) / 2f, Kit.DIM, stage);
        }
        ImGui.dummy(colW, bh + 16f * u);
    }

    private String stageText(String code) {
        if (code == null || code.isEmpty()) return Tr.t("Asking the AI");
        String[] parts = code.split("\\|", 2);
        String arg = parts.length > 1 ? parts[1] : "";
        return switch (parts[0]) {
            case "ASK" -> arg.isEmpty() ? Tr.t("Asking the AI") : Tr.t("Asking %s", arg);
            case "TEST" -> Tr.t("Testing the brush in a simulated world");
            case "FIX" -> Tr.t("Fixing the brush (attempt %d)", parseInt(arg));
            case "ESCALATE" -> Tr.t("Asking the stronger model %s", arg);
            case "SAVE" -> Tr.t("Saving the preset");
            default -> Tr.t("Asking the AI");
        };
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void errorCard(float colX, float colW, float u) {
        ImDrawList dl = ImGui.getWindowDrawList();
        float y0 = ImGui.getCursorScreenPosY();
        float pad = 14f * u;
        String text = ui.error;
        float wrap = colW - 2 * pad - 30f * u;
        ImGui.calcTextSize(tmp, text, false, wrap);
        boolean canRetry = !ui.busy && ui.selected != null && !ui.selected.messages.isEmpty()
                && ui.selected.messages.get(ui.selected.messages.size() - 1).isUser();
        float bh = 30f * u;
        float hh = pad + ImGui.getFontSize() + 8f * u + tmp.y + 12f * u + bh + pad;
        dl.addRectFilled(colX, y0, colX + colW, y0 + hh, Kit.c(Kit.RED, 0.09f), 12f * u);
        dl.addRect(colX, y0, colX + colW, y0 + hh, Kit.c(Kit.RED, 0.35f), 12f * u);
        dl.addCircleFilled(colX + pad + 9f * u, y0 + pad + ImGui.getFontSize() / 2f, 9f * u, Kit.c(Kit.RED), 18);
        Kit.label(dl, colX + pad + 9f * u - Kit.textWidth("!") / 2f, y0 + pad, Kit.WHITE, "!");
        Kit.label(dl, colX + pad + 28f * u, y0 + pad, Kit.RED, Tr.t("Error"));
        ImGui.setCursorScreenPos(colX + pad + 28f * u, y0 + pad + ImGui.getFontSize() + 8f * u);
        ImGui.pushTextWrapPos(ImGui.getCursorPosX() + wrap);
        ImGui.textUnformatted(text);
        ImGui.popTextWrapPos();
        float by = y0 + hh - pad - bh;
        float bx = colX + pad + 28f * u;
        if (canRetry) {
            float rw = Kit.textWidth(Tr.t("Retry")) + 44f * u;
            if (Kit.button("##retry", Tr.t("Retry"), Kit.REFRESH, bx, by, rw, bh, Kit.ACCENT, Kit.ACCENT_HOVER, Kit.WHITE, 9f * u)) {
                ui.retry();
            }
            bx += rw + 8f * u;
        }
        String copy = Tr.t("Copy config folder path");
        float cw = Kit.textWidth(copy) + 44f * u;
        if (Kit.button("##cfgpath", copy, Kit.COPY, bx, by, cw, bh, Kit.GHOST_HOVER, Kit.GHOST_HOVER, Kit.DIM, 9f * u)) {
            ImGui.setClipboardText(ui.configDir.toString());
        }
        ImGui.setCursorScreenPos(colX, y0);
        ImGui.dummy(colW, hh + 14f * u);
    }

    // ------------------------------------------------------------------ welcome screen

    private void welcome(float colX, float colW, float areaH, float u) {
        ImDrawList dl = ImGui.getWindowDrawList();
        float y0 = ImGui.getCursorScreenPosY();
        float top = Math.max(6f * u, areaH * 0.5f - 175f * u);
        float y = y0 + top;

        float base = ui.fontScale[0];
        ImGui.setWindowFontScale(base * 1.4f);
        String title = Tr.t("What brush do you want to create?");
        float tw = Kit.textWidth(title);
        float availW = colW;
        if (tw > availW) {
            ImGui.setWindowFontScale(base * 1.2f);
            tw = Kit.textWidth(title);
        }
        Kit.label(dl, colX + (colW - tw) / 2f, y, Kit.TEXT, title);
        float titleH = ImGui.getFontSize();
        ImGui.setWindowFontScale(base);
        y += titleH + 10f * u;

        String sub = Tr.t("Describe it in your own words. Then keep chatting to adjust it.");
        float sw = Kit.textWidth(sub);
        Kit.label(dl, colX + Math.max(0f, (colW - sw) / 2f), y, Kit.DIM, Kit.ellipsize(sub, colW));
        y += ImGui.getFontSize() + 26f * u;

        float gap = 12f * u;
        float cw = (colW - gap) / 2f;
        float ch = 80f * u;
        List<Ideas.Idea> shown = ui.ideas;
        for (int i = 0; i < shown.size(); i++) {
            float cx = colX + (i % 2) * (cw + gap);
            float cy = y + (i / 2) * (ch + gap);
            Ideas.Idea idea = shown.get(i);
            ideaCard("##idea" + i, idea.title(), idea.desc(), idea.prompt(), cx, cy, cw, ch, u);
        }
        int rows = (shown.size() + 1) / 2;
        float after = y + rows * (ch + gap);
        String other = Tr.t("Other ideas");
        float ow = Kit.textWidth(other) + 48f * u;
        if (Kit.button("##otherideas", other, Kit.REFRESH, colX + (colW - ow) / 2f, after + 2f * u, ow, 32f * u,
                Kit.GHOST, Kit.GHOST_HOVER, Kit.DIM, 10f * u)) {
            ui.rollIdeas();
        }
        ImGui.setCursorScreenPos(colX, y0);
        ImGui.dummy(colW, top + titleH + ImGui.getFontSize() + 80f * u + rows * (ch + gap) + 44f * u);
    }

    private void ideaCard(String id, String title, String desc, String prompt, float x, float y, float w, float h, float u) {
        ImDrawList dl = ImGui.getWindowDrawList();
        ImGui.setCursorScreenPos(x, y);
        boolean clicked = ImGui.invisibleButton(id, w, h);
        boolean hovered = ImGui.isItemHovered();
        if (hovered) ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        float t = Anim.smooth("card." + id, hovered ? 1f : 0f, 18f);
        float[] bg = Kit.mix(Kit.BG_CARD, Kit.CARD_HOVER, t);
        dl.addRectFilled(x, y, x + w, y + h, Kit.c(bg), 13f * u);
        dl.addRect(x, y, x + w, y + h, Kit.c(Kit.mix(Kit.BORDER, Kit.ACCENT, t * 0.8f)), 13f * u);
        float pad = 14f * u;
        Kit.label(dl, x + pad, y + 11f * u, Kit.TEXT, Kit.ellipsize(title, w - 2 * pad));
        ImGui.setCursorScreenPos(x + pad, y + 11f * u + ImGui.getFontSize() + 5f * u);
        ImGui.pushStyleColor(ImGuiCol.Text, Kit.DIM[0], Kit.DIM[1], Kit.DIM[2], 1f);
        ImGui.pushTextWrapPos(ImGui.getCursorPosX() + w - 2 * pad);
        ImGui.textUnformatted(desc);
        ImGui.popTextWrapPos();
        ImGui.popStyleColor();
        if (clicked) ui.fillInput(prompt);
    }

    // ------------------------------------------------------------------ input bar

    private void inputBar(float x0, float y, float w, float barH, float u) {
        ImDrawList dl = ImGui.getWindowDrawList();
        float colW = Math.min(w - 32f * u, 820f * u);
        float colX = x0 + (w - colW) / 2f;
        float round = 20f * u;

        float send = 38f * u;
        float ix = colX + 18f * u;
        float iy = y + 12f * u;
        float iw = Math.max(40f, colW - 36f * u - send - 8f * u);
        float ih = barH - 24f * u;

        boolean active = ui.inputWasActive;
        float focus = Anim.smooth("inputfocus", active ? 1f : 0f, 16f);
        dl.addRectFilled(colX, y, colX + colW, y + barH, Kit.c(Kit.BG_CARD), round);
        dl.addRect(colX, y, colX + colW, y + barH, Kit.c(Kit.mix(Kit.BORDER, Kit.ACCENT, focus)), round, 0, 1f + focus * 0.8f);

        ImGui.setCursorScreenPos(ix, iy);
        if (ui.focusInput) {
            ImGui.setKeyboardFocusHere();
            ui.focusInput = false;
        }
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0f, 0f, 0f, 0f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, 0f, 0f, 0f, 0f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, 0f, 0f, 0f, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0f, 2f * u);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 0f);
        ImGui.inputTextMultiline("##aiinput" + ui.inputGen, ui.input, iw, ih);
        boolean nowActive = ImGui.isItemActive();
        ImGui.popStyleVar(2);
        ImGui.popStyleColor(3);
        ui.inputWasActive = nowActive;

        boolean enter = ImGui.isKeyPressed(ImGuiKey.Enter, false) || ImGui.isKeyPressed(ImGuiKey.KeypadEnter, false);
        boolean shift = ImGui.isKeyDown(ImGuiKey.LeftShift) || ImGui.isKeyDown(ImGuiKey.RightShift);
        if (nowActive && enter && !shift) ui.send();

        if (ui.input.get().isEmpty() && !nowActive) {
            dl.addText(ix, iy + 2f * u, Kit.c(Kit.DIM, 0.85f), Tr.t("Describe the brush..."));
        }

        float sx = colX + colW - 12f * u - send;
        float sy = y + barH - 12f * u - send;
        boolean canSend = !ui.busy && !ui.input.get().trim().isEmpty();
        ImGui.setCursorScreenPos(sx, sy);
        boolean pressed = ImGui.invisibleButton("##aisend", send, send);
        boolean hovered = ImGui.isItemHovered();
        float t = Anim.smooth("sendbtn", hovered && (canSend || ui.busy) ? 1f : 0f, 18f);
        float[] fill;
        Kit.Icon icon;
        float[] iconColor = Kit.WHITE;
        if (ui.busy) {
            fill = Kit.mix(Kit.DANGER, Kit.DANGER_HOVER, t);
            icon = Kit.STOP;
        } else if (canSend) {
            fill = Kit.mix(Kit.ACCENT, Kit.ACCENT_HOVER, t);
            icon = Kit.ARROW_UP;
        } else {
            fill = new float[]{0.20f, 0.22f, 0.27f, 1f};
            icon = Kit.ARROW_UP;
            iconColor = new float[]{0.50f, 0.54f, 0.60f, 1f};
        }
        dl.addCircleFilled(sx + send / 2f, sy + send / 2f, send / 2f, Kit.c(fill), 32);
        icon.draw(dl, sx + send / 2f, sy + send / 2f, send * 0.62f, Kit.c(iconColor));
        if (hovered && (canSend || ui.busy)) ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        Kit.tooltip(ui.busy ? Tr.t("Stop") : Tr.t("Send"));
        if (pressed) {
            if (ui.busy) ui.stop();
            else if (canSend) ui.send();
        }

        String hint = Tr.t("Enter - send, Shift+Enter - new line");
        float hw = Kit.textWidth(hint);
        Kit.label(dl, colX + (colW - hw) / 2f, y + barH + 8f * u, Kit.mix(Kit.DIM, Kit.BG_MAIN, 0.35f), hint);
    }

    // ------------------------------------------------------------------ popups

    private void popups(float u) {
        String deleteTitle = Tr.t("Delete this chat?") + "##aidelete";
        if (openDeletePopup) {
            ImGui.openPopup(deleteTitle);
            openDeletePopup = false;
        }
        ImGui.pushStyleVar(ImGuiStyleVar.PopupRounding, 12f * u);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 18f * u, 16f * u);
        if (ImGui.beginPopupModal(deleteTitle)) {
            ImGui.text(Tr.t("The chat history will be removed."));
            ImGui.textDisabled(Tr.t("Brushes already saved in Axiom stay where they are."));
            ImGui.spacing();
            ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 9f * u);
            ImGui.pushStyleColor(ImGuiCol.Button, Kit.DANGER[0], Kit.DANGER[1], Kit.DANGER[2], 1f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, Kit.DANGER_HOVER[0], Kit.DANGER_HOVER[1], Kit.DANGER_HOVER[2], 1f);
            if (ImGui.button(Tr.t("Delete") + "##confirmdel", 120f * u, 30f * u)) {
                ui.deletePending();
                ImGui.closeCurrentPopup();
            }
            ImGui.popStyleColor(2);
            ImGui.sameLine();
            if (ImGui.button(Tr.t("Cancel") + "##canceldel", 120f * u, 30f * u)) {
                ui.pendingDelete = null;
                ImGui.closeCurrentPopup();
            }
            ImGui.popStyleVar();
            ImGui.endPopup();
        }

        String settingsId = "##aisettings";
        if (openSettingsPopup) {
            ImGui.openPopup(settingsId);
            openSettingsPopup = false;
        }
        ImGui.setNextWindowPos(gearX, gearY, ImGuiCond.Appearing, 1f, 0f);
        if (ImGui.beginPopup(settingsId)) {
            ImGui.text(Tr.t("Settings"));
            ImGui.separator();
            ImGui.setNextItemWidth(180f * u);
            ImGui.sliderFloat(Tr.t("Text size") + "##aifs", ui.fontScale, 0.8f, 1.8f, "%.1f");
            boolean modern = ui.modern;
            if (ImGui.checkbox(Tr.t("Modern look") + "##aimodern", modern)) ui.modern = !modern;
            if (ImGui.checkbox(Tr.t("Change the same preset when refining") + "##aiupdate", ui.updatePreset)) {
                ui.updatePreset = !ui.updatePreset;
            }
            if (ImGui.button(Tr.t("Copy config folder path") + "##aicfg")) ImGui.setClipboardText(ui.configDir.toString());
            ImGui.endPopup();
        }
        ImGui.popStyleVar(2);
    }

    // ------------------------------------------------------------------ small helpers

    private static float[] modeColor(String mode) {
        return switch (mode.toUpperCase(Locale.ROOT)) {
            case "ON_TOP" -> new float[]{0.45f, 0.70f, 1.00f, 1f};
            case "REPLACE" -> Kit.AMBER;
            case "BUILD" -> Kit.GREEN;
            case "REMOVE" -> Kit.RED;
            default -> Kit.DIM;
        };
    }

    private static String modelOf(String meta) {
        if (meta == null || meta.isEmpty()) return "";
        int bar = meta.indexOf(" | ");
        String first = bar < 0 ? meta : meta.substring(0, bar);
        return first.contains("tokens") ? "" : first;
    }

    private static String metaAfterModel(String meta) {
        int bar = meta.indexOf(" | ");
        if (bar < 0) return meta.contains("tokens") ? meta : "";
        String first = meta.substring(0, bar);
        return first.contains("tokens") ? meta : meta.substring(bar + 3);
    }

    private static String planValue(String text, String key) {
        String prefix = "-- " + key + ":";
        for (String line : text.split("\n", 14)) {
            String t = line.trim();
            if (t.startsWith(prefix)) return t.substring(prefix.length()).trim();
        }
        return "";
    }

    private static String withoutNameLine(String text) {
        if (text.startsWith("-- NAME:")) {
            int nl = text.indexOf('\n');
            return nl < 0 ? "" : text.substring(nl + 1).stripTrailing();
        }
        return text.stripTrailing();
    }

    private static int countLines(String text) {
        int lines = 1;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') lines++;
        return lines;
    }
}
