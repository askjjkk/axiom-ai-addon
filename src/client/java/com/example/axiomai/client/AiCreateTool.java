package com.example.axiomai.client;

import com.moulberry.axiomclientapi.CustomTool;

/** The tool that appears in Axiom's Tools panel. Its options panel opens the AI chat window. */
final class AiCreateTool implements CustomTool {
    @Override
    public String name() {
        return "AI Create";
    }

    @Override
    public void displayImguiOptions() {
        ChatUi.get().drawToolPanel();
    }
}
