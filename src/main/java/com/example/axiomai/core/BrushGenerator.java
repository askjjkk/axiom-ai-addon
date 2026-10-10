package com.example.axiomai.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Asks the model for a brush, cleans up the answer and saves it as an Axiom preset. */
public final class BrushGenerator {
    /** A model together with its prices (roubles per million tokens), used to report what a brush cost. */
    public record Engine(LlmClient client, String label, double priceIn, double priceOut) {
        public static Engine plain(LlmClient client) {
            return new Engine(client, "", 0, 0);
        }
    }

    /**
     * @param assistantText the cleaned answer ("-- NAME: ..." line + script); store it in the chat history
     * @param note          what the automatic check found or fixed (empty if no checker is used)
     * @param meta          which model(s) were used, the tokens and the cost, ready to show under the answer
     * @param costRub       the cost of everything that was asked for this brush, in roubles (0 if unknown)
     * @param altNames      other name ideas from the AI (may be empty)
     * @param updated       true when an existing preset was changed instead of a new one being saved
     */
    public record Result(String presetName, Path file, String script, String assistantText, String note,
                         String meta, double costRub, List<String> altNames, boolean updated) {
    }

    private static final Pattern FENCED = Pattern.compile("```[a-zA-Z]*\\R(.*?)```", Pattern.DOTALL);
    private static final Pattern THINK = Pattern.compile("(?s)<think(?:ing)?>.*?</think(?:ing)?>");
    private static final Pattern NAMES_LINE = Pattern.compile("(?m)^[ \\t]*--[ \\t]*NAMES:[ \\t]*(.+?)[ \\t]*$");
    private static final Pattern NAME_LINE = Pattern.compile("(?m)^[ \\t]*--[ \\t]*NAME:[ \\t]*(.+?)[ \\t]*$");

    /** Told what the generator is doing right now (codes: ASK|model, TEST, FIX|n, ESCALATE|model, SAVE). */
    public interface Progress {
        void stage(String code);
    }

    private Progress progress = code -> { };
    private final Engine primary;
    private final Engine escalation;
    private final Path presetFolder;
    private final ScriptChecker checker;
    private final int maxFixes;

    public BrushGenerator(LlmClient client, Path presetFolder) {
        this(client, presetFolder, null, 0);
    }

    /**
     * @param checker  tests every generated script (may be null)
     * @param maxFixes how many times the model may be asked to repair a script that failed the check
     */
    public BrushGenerator(LlmClient client, Path presetFolder, ScriptChecker checker, int maxFixes) {
        this(Engine.plain(client), null, presetFolder, checker, maxFixes);
    }

    /**
     * @param escalation a stronger model that is asked once if the primary model's brush still fails the check
     *                   (may be null)
     */
    public BrushGenerator(Engine primary, Engine escalation, Path presetFolder, ScriptChecker checker, int maxFixes) {
        this.primary = primary;
        this.escalation = escalation;
        this.presetFolder = presetFolder;
        this.checker = checker;
        this.maxFixes = Math.max(0, maxFixes);
    }

    public BrushGenerator withProgress(Progress listener) {
        this.progress = listener == null ? code -> { } : listener;
        return this;
    }

    /** One-shot request without any history. */
    public Result generate(String request) throws Exception {
        if (request == null || request.isBlank()) throw new IllegalArgumentException("Describe the brush first.");
        return generate(List.of(ChatMessage.user(request.trim())));
    }

    public Result generate(List<ChatMessage> history) throws Exception {
        return generate(history, null);
    }

    /**
     * The conversation so far; the last message must be the user's newest request.
     * @param updatePreset file name (inside the script_brush folder) of the preset to change when this chat already
     *                     made one, or null to save a new preset
     */
    public Result generate(List<ChatMessage> history, String updatePreset) throws Exception {
        if (history.isEmpty()) throw new IllegalArgumentException("Describe the brush first.");
        String fallbackName = previousName(history);
        List<ChatMessage> work = new ArrayList<>(history);

        Engine active = primary;
        boolean escalated = false;
        Parsed best = null;
        String remaining = "";
        boolean checkerBroken = false;
        int fixes = 0;
        long tokensIn = 0;
        long tokensOut = 0;
        double cost = 0;
        java.util.Set<String> labels = new java.util.LinkedHashSet<>();
        while (true) {
            progress.stage((escalated ? "ESCALATE|" : "ASK|") + active.label());
            String raw = active.client().complete(PromptBuilder.system(), work);
            LlmClient.Usage usage = active.client().usage();
            tokensIn += usage.promptTokens();
            tokensOut += usage.completionTokens();
            cost += (usage.promptTokens() * active.priceIn() + usage.completionTokens() * active.priceOut()) / 1_000_000.0;
            if (!active.label().isEmpty()) labels.add(active.label());

            Parsed parsed = null;
            String problem;
            try {
                parsed = parse(raw, fallbackName);
                problem = null;
                if (checker != null && !checkerBroken) {
                    progress.stage("TEST");
                    try {
                        problem = checker.check(parsed.script);
                    } catch (Throwable t) {
                        checkerBroken = true; // a broken checker must never block brush creation
                    }
                }
            } catch (IOException e) {
                problem = e.getMessage();
            }
            if (parsed != null) best = parsed; // always keep the newest usable script
            if (problem == null) {
                remaining = "";
                break;
            }
            remaining = problem;
            if (fixes >= maxFixes) {
                if (escalation == null || escalated) break;
                escalated = true;          // one last try with the stronger model
                active = escalation;
            } else {
                fixes++;
                progress.stage("FIX|" + fixes);
            }
            work.add(ChatMessage.assistant(raw, ""));
            work.add(ChatMessage.user("AUTOMATIC CHECK FAILED: " + problem
                    + "\nFix the script and answer again in the same format with the COMPLETE script."));
        }
        if (best == null) throw new IOException(remaining);

        progress.stage("SAVE");
        Path existing = updatePreset == null || updatePreset.isBlank() ? null : presetFolder.resolve(updatePreset);
        boolean update = existing != null && Files.exists(existing);
        String keptName = update ? NbtPresetWriter.readName(existing) : "";
        NbtPresetWriter.Saved saved = update
                ? NbtPresetWriter.overwrite(existing, keptName.isBlank() ? "AI " + best.name : keptName, best.script)
                : NbtPresetWriter.write(presetFolder, "AI " + best.name, best.script);
        String shownName = update ? (saved.presetName().startsWith("AI ") ? saved.presetName().substring(3) : saved.presetName()) : best.name;
        String assistantText = "-- NAME: " + shownName + "\n"
                + (best.altNames.isEmpty() ? "" : "-- NAMES: " + String.join(" | ", best.altNames) + "\n") + best.script;
        return new Result(saved.presetName(), saved.file(), best.script, assistantText,
                note(remaining, fixes, checkerBroken, escalated ? escalation.label() : null),
                meta(labels, tokensIn, tokensOut, cost), cost, best.altNames, update);
    }

    private static String meta(java.util.Set<String> labels, long tokensIn, long tokensOut, double cost) {
        StringBuilder sb = new StringBuilder();
        if (!labels.isEmpty()) sb.append(String.join(" -> ", labels));
        if (tokensIn + tokensOut > 0) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(tokensIn).append(" in / ").append(tokensOut).append(" out tokens");
        }
        if (cost > 0) sb.append(String.format(java.util.Locale.ROOT, " | about %.2f RUB", cost));
        return sb.toString();
    }

    private String note(String remaining, int fixes, boolean checkerBroken, String escalatedTo) {
        if (checker == null || checkerBroken) return "";
        if (!remaining.isEmpty()) {
            String shortText = remaining.length() > 400 ? remaining.substring(0, 400) + "..." : remaining;
            return "Auto-check could not fully verify this brush: " + shortText;
        }
        if (escalatedTo != null) {
            return "Auto-check: the first model's brush had problems, so the stronger model"
                    + (escalatedTo.isEmpty() ? "" : " (" + escalatedTo + ")") + " rewrote it.";
        }
        return fixes > 0
                ? "Auto-check: the AI fixed " + fixes + " problem(s) in this brush before saving."
                : "Auto-check passed.";
    }

    /** The brush name used by the last AI answer in this chat, so that a follow-up keeps its name. */
    private static String previousName(List<ChatMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage m = history.get(i);
            if (!m.isUser()) {
                Matcher nm = NAME_LINE.matcher(m.text());
                if (nm.find()) return cleanName(nm.group(1));
            }
        }
        return "Brush";
    }

    record Parsed(String name, String script, List<String> altNames) {
    }

    /** The script that was saved as a preset, taken from the text stored in the chat (without the NAME/NAMES lines). */
    public static String scriptOf(String assistantText) {
        String[] lines = assistantText.split("\n", -1);
        int i = 0;
        while (i < lines.length && (lines[i].startsWith("-- NAME:") || lines[i].startsWith("-- NAMES:"))) i++;
        return String.join("\n", java.util.Arrays.copyOfRange(lines, i, lines.length));
    }

    /** The "-- NAMES:" ideas stored in an answer. */
    public static List<String> altNamesOf(String assistantText) {
        Matcher m = NAMES_LINE.matcher(assistantText);
        return m.find() ? splitNames(m.group(1), null) : List.of();
    }

    private static List<String> splitNames(String line, String exclude) {
        List<String> out = new ArrayList<>();
        for (String part : line.split("\\|")) {
            if (part.isBlank()) continue;
            String n = cleanName(part);
            if (!n.isEmpty() && !n.equalsIgnoreCase(exclude) && out.stream().noneMatch(n::equalsIgnoreCase) && out.size() < 3) out.add(n);
        }
        return out;
    }

    static Parsed parse(String raw) throws IOException {
        return parse(raw, "Brush");
    }

    /** Turns the raw model answer into a clean name + script, or throws if it is unusable. */
    static Parsed parse(String raw, String defaultName) throws IOException {
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        text = THINK.matcher(text).replaceAll("").strip();   // reasoning models may include their thoughts

        Matcher fence = FENCED.matcher(text);
        if (fence.find()) {
            text = fence.group(1).strip();
        } else {
            text = text.replaceAll("(?m)^```[a-zA-Z]*[ \\t]*$", "").strip(); // stray fence lines
        }

        String name = defaultName == null || defaultName.isBlank() ? "Brush" : defaultName;
        Matcher nm = NAME_LINE.matcher(text);
        if (nm.find()) {
            text = text.substring(nm.start());                // drop any chatter before the header
            nm = NAME_LINE.matcher(text);
            nm.find();
            String candidate = cleanName(nm.group(1));
            if (!candidate.isEmpty()) name = candidate;
            text = (text.substring(0, nm.start()) + text.substring(nm.end())).strip();
        }

        List<String> altNames = List.of();
        Matcher names = NAMES_LINE.matcher(text);
        if (names.find()) {
            altNames = splitNames(names.group(1), name);
            text = (text.substring(0, names.start()) + text.substring(names.end())).strip();
        }

        if (text.isEmpty()) throw new IOException("The model returned no code.");
        long dollars = text.chars().filter(c -> c == '$').count();
        if (dollars % 2 != 0) {
            throw new IOException("The generated code has a broken setting (odd number of '$'). Try again.");
        }
        if (!text.contains("setBlock") && !text.contains("return")) {
            throw new IOException("The generated code places no blocks. Try describing the brush differently.");
        }
        if (text.getBytes(StandardCharsets.UTF_8).length > 60_000) {
            throw new IOException("The generated code is too long.");
        }
        return new Parsed(name, text + "\n", altNames);
    }

    private static String cleanName(String raw) {
        String candidate = raw.replaceAll("[^\\p{L}\\p{N} _-]", "").strip();
        if (candidate.length() > 40) candidate = candidate.substring(0, 40).strip();
        return candidate.isEmpty() ? "Brush" : candidate;
    }
}
