package com.example.axiomai.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Called from the mixin on every frame of Axiom's editor UI. Must never throw into Axiom's drawing code. */
public final class AxiomHooks {
    private static final Logger LOGGER = LoggerFactory.getLogger("AxiomAIAddon");
    private static int failures;

    private AxiomHooks() {
    }

    public static void afterMenuBar() {
        if (failures >= 3) return;
        try {
            ChatUi.get().drawFromHook();
        } catch (Throwable t) {
            failures++;
            LOGGER.error("The AI chat window failed to draw ({} of 3)", failures, t);
        }
    }
}
