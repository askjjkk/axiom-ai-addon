package com.example.axiomai.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;

/**
 * Axiom reads its list of Script Brush presets from disk only once; a file that was added later shows up only after
 * Axiom reloads the list for some other reason (for example when you delete a preset). This class tells Axiom's
 * preset list to read the folder again. It uses Axiom's internals through reflection and never breaks anything:
 * if Axiom changes, it just does nothing.
 */
final class PresetRefresher {
    private static final Logger LOGGER = LoggerFactory.getLogger("AxiomAIAddon");
    private static boolean warned;
    /** How many times a reload was requested (for the tests). */
    static int requests;

    private PresetRefresher() {
    }

    /** Must run on the render thread. Returns true if the Script Brush preset list was told to reload. */
    static boolean refreshScriptBrush() {
        requests++;
        return refresh(PresetRefresher::axiomTools);
    }

    /** All tools of Axiom (empty when Axiom is not there or its internals changed). */
    private static List<?> axiomTools() {
        try {
            Class<?> manager = Class.forName("com.moulberry.axiom.tools.ToolManager");
            Method getTools = manager.getMethod("getTools");
            return (List<?>) getTools.invoke(null);
        } catch (Throwable t) {
            warnOnce("Could not reach Axiom's tool list", t);
            return List.of();
        }
    }

    /** Looks for the Script Brush tool in the given tools and clears the "loaded" flag of its preset list. */
    static boolean refresh(Supplier<List<?>> tools) {
        try {
            for (Object tool : tools.get()) {
                if (!tool.getClass().getName().endsWith("ScriptBrushTool")) continue;
                Field presetWidget = tool.getClass().getDeclaredField("presetWidget");
                presetWidget.setAccessible(true);
                Object widget = presetWidget.get(tool);
                Field loaded = widget.getClass().getDeclaredField("loaded");
                loaded.setAccessible(true);
                loaded.setBoolean(widget, false);
                return true;
            }
        } catch (Throwable t) {
            warnOnce("Could not make Axiom reload its presets", t);
        }
        return false;
    }

    private static void warnOnce(String text, Throwable t) {
        if (warned) return;
        warned = true;
        LOGGER.warn("{} ({}). New presets may need a refresh in Axiom.", text, t.toString());
    }
}
