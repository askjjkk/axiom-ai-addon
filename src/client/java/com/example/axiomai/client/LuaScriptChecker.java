package com.example.axiomai.client;

import com.example.axiomai.core.ScriptChecker;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JseMathLib;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tests a generated brush on a small invented world before it is saved. It finds broken syntax, runtime errors,
 * unknown block ids and brushes that do not do what their own PLAN says (for example an ON_TOP brush that
 * overwrites the ground). The text of the finding is sent back to the AI so it can repair the script.
 * Only plain Lua is available to the script (no io, os or require) and it is stopped after a fixed
 * number of instructions.
 */
final class LuaScriptChecker implements ScriptChecker {
    private static final int SIZE = 12;          // the test world is SIZE x SIZE columns
    private static final int BASE_Y = 6;         // surface height
    private static final int HOOK_STEP = 100_000;
    private static final int HOOK_LIMIT = 200;   // about 20 million Lua instructions in total

    private static final Pattern MODE_LINE = Pattern.compile("(?m)^\\s*--\\s*MODE:\\s*([A-Za-z_]+)");
    private static final Pattern TPL_NUMBER = Pattern.compile("\\$(int|float)\\(([^,()]*),([^,()]*),([^,()]*),([^,()]*)\\)\\$");
    private static final Pattern TPL_BOOLEAN = Pattern.compile("\\$boolean\\(([^,()]*),\\s*(true|false)\\s*\\)\\$");
    private static final Pattern TPL_BLOCK = Pattern.compile("\\$blockState\\(([^,()]*),\\s*([A-Za-z0-9_:]+)\\s*\\)\\$");
    private static final Pattern TPL_ONCE = Pattern.compile("\\$once\\$");
    private static final Pattern BLOCK_REF = Pattern.compile("\\bblocks\\.([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern BLOCK_REF_INDEX = Pattern.compile("\\bblocks\\[\\s*\"([A-Za-z0-9_]+)\"\\s*\\]");

    private static final Set<String> NON_SOLID = Set.of(
            "air", "cave_air", "void_air", "water", "lava", "snow", "short_grass", "tall_grass", "fern", "large_fern",
            "dead_bush", "short_dry_grass", "tall_dry_grass", "poppy", "dandelion", "cornflower", "allium",
            "azure_bluet", "oxeye_daisy", "lily_of_the_valley", "wither_rose", "torchflower", "brown_mushroom",
            "red_mushroom", "vine", "glow_lichen", "moss_carpet", "pale_moss_carpet", "leaf_litter", "pink_petals",
            "wildflowers", "sweet_berry_bush", "torch", "wall_torch", "cobweb", "fire", "kelp", "seagrass",
            "tall_seagrass", "sugar_cane", "lilac", "sunflower", "peony", "rose_bush", "hanging_roots", "light");
    private static final String[] NON_SOLID_SUFFIX = {"_carpet", "_button", "_pressure_plate", "_sapling", "_tulip",
            "_orchid", "_roots", "_fungus", "_sign", "_rail", "_banner", "_torch", "_candle", "_flower", "_bush"};

    private static Set<String> registry;
    private static boolean registryTried;

    // ------------------------------------------------------------------ public entry

    @Override
    public String check(String script) {
        String mode = readMode(script);
        if (mode == null) {
            return "The header is incomplete. The script must start with the comment lines "
                    + "'-- NAME: ...', '-- PLAN', '-- MODE: ON_TOP or REPLACE or BUILD or REMOVE', '-- TARGET: ...', "
                    + "'-- ACTION: ...', '-- BLOCKS: ...', '-- SETTINGS: ...' and the code must do what the PLAN says.";
        }

        Expanded expanded = expand(script);
        if (expanded.problem != null) return expanded.problem;

        Set<String> names = referencedBlocks(script);
        String unknown = unknownBlocks(names);
        if (unknown != null) return unknown;

        return new Run(expanded.lua, names, mode, expanded.once).execute();
    }

    private static String readMode(String script) {
        Matcher m = MODE_LINE.matcher(script);
        if (!m.find()) return null;
        String mode = m.group(1).toUpperCase(java.util.Locale.ROOT);
        return switch (mode) {
            case "ON_TOP", "REPLACE", "BUILD", "REMOVE" -> mode;
            default -> null;
        };
    }

    // ------------------------------------------------------------------ template variables

    private record Expanded(String lua, boolean once, String problem) {
    }

    /** Replaces the Axiom template variables with their default values, like Axiom does before running. */
    static Expanded expand(String script) {
        boolean once = TPL_ONCE.matcher(script).find();
        String lua = TPL_ONCE.matcher(script).replaceAll("--once");
        lua = TPL_NUMBER.matcher(lua).replaceAll(m -> Matcher.quoteReplacement(number(m.group(1), m.group(3), m.group(4), m.group(5))));
        lua = TPL_BOOLEAN.matcher(lua).replaceAll(m -> m.group(2));
        lua = TPL_BLOCK.matcher(lua).replaceAll(m ->
                Matcher.quoteReplacement("blocks[\"" + stripNamespace(m.group(2)) + "\"]"));
        int dollar = lua.indexOf('$');
        if (dollar >= 0) {
            int end = Math.min(lua.length(), dollar + 50);
            String around = lua.substring(Math.max(0, dollar - 5), end).replace('\n', ' ');
            return new Expanded(lua, once, "A setting is written incorrectly near: " + around
                    + "  Allowed forms: $int(Title,default,min,max)$  $float(Title,default,min,max)$  "
                    + "$boolean(Title,true)$  $blockState(Title,block_id)$  $once$  (no commas inside titles).");
        }
        return new Expanded(lua, once, null);
    }

    /**
     * int sliders give whole numbers. float sliders give decimals: a whole default such as 2 is tested as 2.5
     * (inside the slider range), because a real slider can be dragged to any decimal value and that is
     * what breaks scripts that put a float into a property string.
     */
    private static String number(String kind, String raw, String rawMin, String rawMax) {
        String t = raw.trim();
        try {
            double value = Double.parseDouble(t);
            String text;
            if (kind.equals("int")) {
                text = Long.toString(Math.round(value));
            } else if (value == Math.rint(value)) {
                double max = parseOr(rawMax, value + 1);
                double min = parseOr(rawMin, value - 1);
                double shifted = value + 0.5 <= max ? value + 0.5 : (value - 0.5 >= min ? value - 0.5 : value);
                text = Double.toString(shifted);
            } else {
                text = t;
            }
            return text.startsWith("-") ? "(" + text + ")" : text;
        } catch (NumberFormatException e) {
            return "0";
        }
    }

    private static double parseOr(String raw, double fallback) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String stripNamespace(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    // ------------------------------------------------------------------ block ids

    private static Set<String> referencedBlocks(String script) {
        Set<String> names = new LinkedHashSet<>();
        for (Matcher m = BLOCK_REF.matcher(script); m.find(); ) names.add(m.group(1));
        for (Matcher m = BLOCK_REF_INDEX.matcher(script); m.find(); ) names.add(m.group(1));
        for (Matcher m = TPL_BLOCK.matcher(script); m.find(); ) names.add(stripNamespace(m.group(2)));
        return names;
    }

    /** The real block ids of this Minecraft version, read from the game. Null if they cannot be read. */
    private static synchronized Set<String> registryNames() {
        if (registryTried) return registry;
        registryTried = true;
        try {
            Class<?> builtIn = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            Object blockRegistry = builtIn.getField("BLOCK").get(null);
            Class<?> registryType = Class.forName("net.minecraft.core.Registry");
            Set<?> keys = (Set<?>) registryType.getMethod("keySet").invoke(blockRegistry);
            Set<String> out = new LinkedHashSet<>();
            for (Object key : keys) {
                String id = key.toString();
                if (id.startsWith("minecraft:")) out.add(id.substring("minecraft:".length()));
            }
            if (!out.isEmpty()) registry = out;
        } catch (Throwable ignored) {
            registry = null; // the check is simply skipped
        }
        return registry;
    }

    private static String unknownBlocks(Set<String> names) {
        Set<String> known = registryNames();
        if (known == null) return null;
        List<String> bad = new ArrayList<>();
        for (String n : names) if (!known.contains(n)) bad.add(n);
        if (bad.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("These block ids do not exist in this Minecraft version: ");
        int shown = 0;
        for (String n : bad) {
            if (shown++ == 5) break;
            sb.append(n).append(" (similar real ids: ").append(String.join(", ", similar(n, known))).append("); ");
        }
        return sb.append("Use only real block ids.").toString();
    }

    private static List<String> similar(String name, Set<String> known) {
        List<String> scored = new ArrayList<>(known);
        scored.sort((a, b) -> Integer.compare(distance(name, a), distance(name, b)));
        return scored.subList(0, Math.min(3, scored.size()));
    }

    private static int distance(String a, String b) {
        int bonus = (a.contains(b) || b.contains(a)) ? -3 : 0;
        int[] prev = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] cur = new int[b.length() + 1];
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            }
            prev = cur;
        }
        return prev[b.length()] + bonus;
    }

    static boolean isNonSolidName(String name) {
        if (NON_SOLID.contains(name)) return true;
        for (String suffix : NON_SOLID_SUFFIX) if (name.endsWith(suffix)) return true;
        return false;
    }

    // ------------------------------------------------------------------ the simulated world and the test run

    private record Placement(int x, int y, int z, int block, int previous) {
    }

    private static final class Run {
        private final String lua;
        private final Set<String> names;
        private final String mode;
        private final boolean once;

        private final List<String> stateNames = new ArrayList<>();      // id -> full state text
        private final Map<String, Integer> idByState = new HashMap<>();
        private final Map<Long, Integer> world = new HashMap<>();
        private final List<Placement> placements = new ArrayList<>();
        private final List<String> groundNames = new ArrayList<>();

        Run(String lua, Set<String> names, String mode, boolean once) {
            this.lua = lua;
            this.names = names;
            this.mode = mode;
            this.once = once;
            idOf("air");
        }

        String execute() {
            buildWorld();
            Globals g = new Globals();
            g.load(new BaseLib());
            g.load(new TableLib());
            g.load(new StringLib());
            g.load(new JseMathLib());
            g.load(new DebugLib());
            LoadState.install(g);
            LuaC.install(g);
            installApi(g);
            g.set("dofile", LuaValue.NIL);
            g.set("loadfile", LuaValue.NIL);
            g.load("local n = 0\n"
                    + "debug.sethook(function() n = n + 1 if n > " + HOOK_LIMIT + " then "
                    + "error('The script is too slow or loops forever') end end, '', " + HOOK_STEP + ")", "limit").call();
            g.set("debug", LuaValue.NIL);

            LuaValue chunk;
            try {
                chunk = g.load(lua, "script");
            } catch (LuaError e) {
                return "Lua syntax error: " + cleanMessage(e);
            }

            int positions = 0;
            try {
                if (once) {
                    int cx = SIZE / 2, cz = SIZE / 2;
                    positions = 1;
                    runAt(g, chunk, cx, surfaceY(cx, cz), cz);
                } else {
                    for (int x = 0; x < SIZE; x++) {
                        for (int z = 0; z < SIZE; z++) {
                            for (int y = BASE_Y - 2; y <= BASE_Y + 4; y++) {
                                positions++;
                                runAt(g, chunk, x, y, z);
                            }
                        }
                    }
                }
            } catch (LuaError e) {
                return "Runtime error in the simulated world: " + cleanMessage(e)
                        + "  (Remember: block ids come from the blocks table, functions must exist in the list of "
                        + "available functions, and 'return' must be the last statement of a block.)";
            }
            return judge(positions);
        }

        private void runAt(Globals g, LuaValue chunk, int x, int y, int z) {
            g.set("x", LuaValue.valueOf(x));
            g.set("y", LuaValue.valueOf(y));
            g.set("z", LuaValue.valueOf(z));
            Varargs result = chunk.invoke();
            LuaValue first = result.arg1();
            if (first.isnumber()) {
                placements.add(new Placement(x, y, z, first.toint(), blockAt(x, y, z)));
            } else if (!first.isnil() && !first.isboolean()) {
                throw new LuaError("The script returned a " + first.typename()
                        + ". It may only return a blockstate (from the blocks table or withBlockProperty) or nil.");
            }
        }

        // ---------------------------------------------------------------- verdict

        private String judge(int positions) {
            if (placements.isEmpty()) {
                return "In a simulated test world (" + positions + " positions; the ground consisted of "
                        + String.join(", ", groundNames) + " with air above) the script placed no blocks at all. "
                        + "The conditions can probably never be true: check the block ids it compares with, the "
                        + "heights (y, y + 1) and that the target block is the one named in the PLAN. "
                        + "Also make sure default settings (density, chance, thresholds) are not near zero.";
            }

            int same = 0;
            int overwritten = 0;
            Placement firstOverwrite = null;
            Placement firstNotAir = null;
            for (Placement p : placements) {
                if (p.block == p.previous) same++;
                String oldName = baseName(p.previous);
                if (!isNonSolidName(oldName)) {
                    overwritten++;
                    if (firstOverwrite == null) firstOverwrite = p;
                }
                if (p.block != 0 && firstNotAir == null) firstNotAir = p;
            }

            if (same == placements.size()) {
                return "The script only places blocks that are already there, so nothing visibly changes ("
                        + placements.size() + " placements). Place a different block, or place it at a different position.";
            }
            if (mode.equals("ON_TOP") && overwritten > 0) {
                Placement p = firstOverwrite;
                return "The PLAN says MODE: ON_TOP, but the script overwrote " + overwritten + " solid block(s) of "
                        + "the ground, for example " + baseName(p.previous) + " at height " + (p.y - BASE_Y)
                        + " relative to the surface was replaced by " + label(p.block) + ". In ON_TOP mode the target "
                        + "block must stay untouched: check getBlock(x, y, z) for the target and that getBlock(x, y + 1, z) "
                        + "is air, then place the new blocks with setBlock(x, y + 1, z, ...) (and y + 2 for tall plants). "
                        + "Never return a block and never place at y or below.";
            }
            if (mode.equals("REMOVE") && firstNotAir != null) {
                return "The PLAN says MODE: REMOVE, but the script places " + label(firstNotAir.block)
                        + " instead of air. A remove brush must return blocks.air.";
            }
            return null;
        }

        // ---------------------------------------------------------------- world

        private void buildWorld() {
            List<String> candidates = new ArrayList<>();
            for (String n : names) {
                if (!isNonSolidName(n) && !n.equals("bedrock") && candidates.size() < 6) candidates.add(n);
            }
            if (candidates.isEmpty()) candidates.addAll(List.of("grass_block", "dirt", "stone", "sand"));
            groundNames.addAll(candidates);

            // Brushes that remove or replace plants, snow layers or water need some of them to exist.
            List<String> decor = new ArrayList<>();
            if (mode.equals("REMOVE") || mode.equals("REPLACE")) {
                for (String n : names) {
                    if (isNonSolidName(n) && !n.equals("air") && decor.size() < 6) decor.add(n);
                }
            }

            for (int x = 0; x < SIZE; x++) {
                for (int z = 0; z < SIZE; z++) {
                    int top = surfaceY(x, z);
                    String surface = candidates.get(((x / 3) + (z / 3)) % candidates.size());
                    for (int y = 0; y <= top; y++) {
                        world.put(key(x, y, z), idOf(y == top ? surface : "stone"));
                    }
                    if (!decor.isEmpty() && (x + z) % 2 == 0) {
                        world.put(key(x, top + 1, z), idOf(decor.get(((x / 2) + (z / 2)) % decor.size())));
                    }
                }
            }
            if (!decor.isEmpty()) groundNames.add("plus " + String.join(", ", decor) + " standing on it");
        }

        private static int surfaceY(int x, int z) {
            return BASE_Y + (((x / 4) + (z / 4)) % 2);
        }

        private static long key(int x, int y, int z) {
            return ((long) (x + 4096) << 40) | ((long) (y + 4096) << 20) | (long) (z + 4096);
        }

        private int blockAt(int x, int y, int z) {
            Integer id = world.get(key(x, y, z));
            return id == null ? 0 : id;
        }

        private int idOf(String state) {
            Integer id = idByState.get(state);
            if (id != null) return id;
            int created = stateNames.size();
            stateNames.add(state);
            idByState.put(state, created);
            return created;
        }

        private String baseName(int id) {
            if (id < 0 || id >= stateNames.size()) return "air";
            String s = stateNames.get(id);
            int bracket = s.indexOf('[');
            return bracket < 0 ? s : s.substring(0, bracket);
        }

        private String label(int id) {
            return id >= 0 && id < stateNames.size() ? stateNames.get(id) : "unknown";
        }

        private int baseId(int id) {
            return idOf(baseName(id));
        }

        // ---------------------------------------------------------------- Lua API of the simulated Axiom

        private void installApi(Globals g) {
            Set<String> known = registryNames();
            LuaTable blocks = new LuaTable();
            LuaTable meta = new LuaTable();
            meta.set(LuaValue.INDEX, new TwoArgFunction() {
                @Override
                public LuaValue call(LuaValue table, LuaValue key) {
                    String name = key.tojstring();
                    if (known != null && !known.contains(name) && !name.equals("air")) return LuaValue.NIL;
                    return LuaValue.valueOf(idOf(name));
                }
            });
            blocks.setmetatable(meta);
            g.set("blocks", blocks);

            g.set("getBlock", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs a) {
                    return LuaValue.valueOf(baseId(blockAt(a.checkint(1), a.checkint(2), a.checkint(3))));
                }
            });
            g.set("getBlockState", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs a) {
                    return LuaValue.valueOf(blockAt(a.checkint(1), a.checkint(2), a.checkint(3)));
                }
            });
            g.set("setBlock", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs a) {
                    int x = a.checkint(1), y = a.checkint(2), z = a.checkint(3);
                    if (!a.arg(4).isnumber()) {
                        throw new LuaError("setBlock: the 4th argument must be a blockstate, but it is "
                                + a.arg(4).typename() + " (probably an unknown block id)");
                    }
                    placements.add(new Placement(x, y, z, a.checkint(4), blockAt(x, y, z)));
                    return LuaValue.NONE;
                }
            });
            g.set("isSolid", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs a) {
                    if (!a.arg(1).isnumber()) throw new LuaError("isSolid: argument must be a blockstate, got " + a.arg(1).typename());
                    return LuaValue.valueOf(!isNonSolidName(baseName(a.checkint(1))));
                }
            });
            g.set("withBlockProperty", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs a) {
                    if (!a.arg(1).isnumber()) throw new LuaError("withBlockProperty: the first argument must be a blockstate, got " + a.arg(1).typename());
                    Map<String, String> props = new java.util.TreeMap<>();
                    String current = label(a.checkint(1));
                    int br = current.indexOf('[');
                    if (br >= 0) {
                        for (String part : current.substring(br + 1, current.length() - 1).split(",")) {
                            String[] kv = part.split("=", 2);
                            if (kv.length == 2) props.put(kv[0], kv[1]);
                        }
                    }
                    for (int i = 2; i <= a.narg(); i++) {
                        String text = a.arg(i).isstring() || a.arg(i).isnumber() ? a.arg(i).tojstring() : null;
                        if (text == null || !text.contains("=")) {
                            throw new LuaError("withBlockProperty expects strings like \"property=value\"");
                        }
                        String[] kv = text.split("=", 2);
                        if (kv[1].matches("-?\\d+\\.\\d+")) {
                            throw new LuaError("withBlockProperty got the decimal value '" + text
                                    + "'. Property values must be integers: use math.floor(...)");
                        }
                        props.put(kv[0].trim(), kv[1].trim());
                    }
                    StringBuilder sb = new StringBuilder(baseName(a.checkint(1)));
                    if (!props.isEmpty()) {
                        sb.append('[');
                        boolean first = true;
                        for (Map.Entry<String, String> e : props.entrySet()) {
                            if (!first) sb.append(',');
                            first = false;
                            sb.append(e.getKey()).append('=').append(e.getValue());
                        }
                        sb.append(']');
                    }
                    return LuaValue.valueOf(idOf(sb.toString()));
                }
            });
            g.set("getBlockProperty", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs a) {
                    String state = label(a.checkint(1));
                    String prop = a.checkjstring(2) + "=";
                    int br = state.indexOf('[');
                    if (br < 0) return LuaValue.NIL;
                    for (String part : state.substring(br + 1, state.length() - 1).split(",")) {
                        if (part.startsWith(prop)) return LuaValue.valueOf(part.substring(prop.length()));
                    }
                    return LuaValue.NIL;
                }
            });
            g.set("isBlockTagged", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs a) {
                    String name = baseName(a.checkint(1));
                    String tag = a.checkjstring(2).toLowerCase(java.util.Locale.ROOT);
                    boolean tagged = switch (tag) {
                        case "logs" -> name.endsWith("_log") || name.endsWith("_wood");
                        case "leaves" -> name.endsWith("_leaves");
                        case "fences" -> name.endsWith("_fence");
                        case "planks" -> name.endsWith("_planks");
                        default -> false;
                    };
                    return LuaValue.valueOf(tagged);
                }
            });
            g.set("getHighestBlockYAt", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs a) {
                    return LuaValue.valueOf(surfaceY(Math.floorMod(a.checkint(1), SIZE), Math.floorMod(a.checkint(2), SIZE)));
                }
            });
            VarArgFunction noise = new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs a) {
                    double v = Math.sin(a.checkdouble(1) * 12.9898 + a.checkdouble(2) * 78.233
                            + a.checkdouble(3) * 37.719 + (a.arg(4).isnumber() ? a.checkdouble(4) : 0) * 0.1) * 43758.5453;
                    return LuaValue.valueOf(v - Math.floor(v));
                }
            };
            g.set("getSimplexNoise", noise);
            g.set("getVoroniEdgeNoise", noise);
            g.set("getBlockRGB", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs a) {
                    return baseName(a.checkint(1)).equals("air") ? LuaValue.NIL : LuaValue.valueOf(0x808080);
                }
            });
            g.set("findClosestBlockToRGB", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs a) {
                    return LuaValue.valueOf(idOf("stone"));
                }
            });
            g.set("print", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs a) {
                    return LuaValue.NONE;
                }
            });
        }

        private static String cleanMessage(LuaError e) {
            String m = e.getMessage();
            if (m == null) return "unknown error";
            int trace = m.indexOf("\nstack traceback");
            if (trace >= 0) m = m.substring(0, trace);
            return m.replace("script:", "line ").strip();
        }
    }
}
