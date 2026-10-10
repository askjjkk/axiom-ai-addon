package com.example.axiomai.client.mixin;

import com.example.axiomai.client.AxiomHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the AI chat window be drawn on every frame of Axiom's editor, not only while the "AI Create" tool is selected.
 * If a future Axiom version changes this place, the injection is simply skipped (require = 0) and the chat window is
 * drawn by the tool panel again.
 */
@Mixin(targets = "com.moulberry.axiom.editor.EditorUI", remap = false)
public abstract class EditorUIMixin {
    @Inject(
            method = "drawOverlayInternal",
            at = @At(value = "INVOKE",
                    target = "Lcom/moulberry/axiom/editor/windows/MainMenuBar;render()V",
                    shift = At.Shift.AFTER),
            require = 0,
            remap = false)
    private static void axiomaiaddon$afterMenuBar(CallbackInfo ci) {
        AxiomHooks.afterMenuBar();
    }
}
