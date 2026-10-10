package com.example.axiomai.core;

/**
 * One model the user can choose in the chat window. Prices are in roubles per one million tokens and are
 * only used to show the cost of a brush. autoFix: how many repairs this model may make (-1 = use the global value).
 */
public record ModelProfile(String name, String model, double priceIn, double priceOut, int autoFix) {
    public double cost(long tokensIn, long tokensOut) {
        return (tokensIn * priceIn + tokensOut * priceOut) / 1_000_000.0;
    }

    public String priceText() {
        if (priceIn <= 0 && priceOut <= 0) return "";
        return String.format(java.util.Locale.ROOT, "in %.0f / out %.0f RUB per 1M tokens", priceIn, priceOut);
    }
}
