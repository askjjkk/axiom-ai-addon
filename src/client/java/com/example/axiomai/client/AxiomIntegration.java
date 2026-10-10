package com.example.axiomai.client;

import com.moulberry.axiomclientapi.service.ToolRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;

/** Registers the "AI Create" tool in Axiom through Axiom's official client API. */
final class AxiomIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger("AxiomAIAddon");

    private AxiomIntegration() {
    }

    static void register() {
        try {
            ToolRegistryService service = ServiceLoader
                    .load(ToolRegistryService.class, ToolRegistryService.class.getClassLoader())
                    .findFirst()
                    .orElse(null);
            if (service == null) {
                LOGGER.error("Axiom's tool registry was not found. The AI Create tool is not available.");
                return;
            }
            service.register(new AiCreateTool());
            LOGGER.info("AI Create tool registered in Axiom.");
        } catch (Throwable t) {
            LOGGER.error("Could not register the AI Create tool in Axiom", t);
        }
    }
}
