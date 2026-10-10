package com.example.axiomai.core;

import java.nio.file.Path;

/**
 * Run this class from IntelliJ (green triangle next to "main") to test the AI without starting Minecraft.
 * It creates the folder "test-config" next to your project, reads config.json from there
 * and saves the generated brush into the folder "test-output".
 */
public final class ManualTest {
    // Change this text to ask for another brush:
    private static final String REQUEST = "A brush that scatters random flowers on grass, with a density slider";

    public static void main(String[] args) {
        Path configDir = Path.of("test-config").toAbsolutePath();
        Path outDir = Path.of("test-output").toAbsolutePath();
        System.out.println("Config folder : " + configDir);
        System.out.println("Output folder : " + outDir);
        try {
            AiConfig config = AiConfig.loadOrCreate(configDir);
            LlmClient client = config.createClient(configDir);
            System.out.println("Asking the model, please wait (up to a minute)...");
            BrushGenerator.Result r = new BrushGenerator(client, outDir).generate(REQUEST);
            System.out.println();
            System.out.println("DONE. Preset name : " + r.presetName());
            System.out.println("Saved to          : " + r.file());
            System.out.println("---------------- generated code ----------------");
            System.out.println(r.script());
        } catch (Exception e) {
            System.out.println();
            System.out.println("FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
