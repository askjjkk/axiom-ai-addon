package com.example.axiomai.core;

/** Tests a generated script before it is saved, so obvious mistakes can be fixed automatically. */
public interface ScriptChecker {
    /**
     * @return null when the script looks fine, otherwise a short description of the problem,
     *         written so that the AI model can fix the script from it
     */
    String check(String script);
}
