package com.example.axiomai.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class AxiomAIClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Axiom classes are touched only when Axiom is really installed.
        if (FabricLoader.getInstance().isModLoaded("axiom")) {
            AxiomIntegration.register();
        }
    }
}
