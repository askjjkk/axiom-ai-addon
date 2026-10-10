package com.example.axiomai.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Splits one line of a Script Brush script into coloured pieces (for the code view in the chat). */
public final class LuaTokens {
    public enum Type { PLAIN, SPACE, KEYWORD, COMMENT, STRING, NUMBER, TEMPLATE, BUILTIN }

    public record Token(String text, Type type) {
    }

    private static final Set<String> KEYWORDS = Set.of("and", "break", "do", "else", "elseif", "end", "false", "for",
            "function", "goto", "if", "in", "local", "nil", "not", "or", "repeat", "return", "then", "true", "until",
            "while");
    private static final Set<String> BUILTINS = Set.of("getBlock", "setBlock", "getBlockState", "isSolid",
            "withBlockProperty", "getBlockProperty", "isBlockTagged", "getHighestBlockYAt", "getSimplexNoise",
            "getVoroniEdgeNoise", "getBlockRGB", "findClosestBlockToRGB", "blocks", "math", "string", "table",
            "ipairs", "pairs", "print", "tostring", "tonumber", "x", "y", "z");

    private LuaTokens() {
    }

    public static List<Token> tokenize(String line) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        int n = line.length();
        while (i < n) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c)) {
                int j = i;
                while (j < n && Character.isWhitespace(line.charAt(j))) j++;
                out.add(new Token(line.substring(i, j), Type.SPACE));
                i = j;
            } else if (c == '-' && i + 1 < n && line.charAt(i + 1) == '-') {
                out.add(new Token(line.substring(i), Type.COMMENT));
                i = n;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && line.charAt(j) != c) {
                    if (line.charAt(j) == '\\' && j + 1 < n) j++;
                    j++;
                }
                j = Math.min(n, j + 1);
                out.add(new Token(line.substring(i, j), Type.STRING));
                i = j;
            } else if (c == '$') {
                int end = line.indexOf('$', i + 1);
                int j = end < 0 ? n : end + 1;
                out.add(new Token(line.substring(i, j), Type.TEMPLATE));
                i = j;
            } else if (Character.isDigit(c)) {
                int j = i;
                while (j < n && (Character.isDigit(line.charAt(j)) || line.charAt(j) == '.')) j++;
                out.add(new Token(line.substring(i, j), Type.NUMBER));
                i = j;
            } else if (Character.isLetter(c) || c == '_') {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(line.charAt(j)) || line.charAt(j) == '_')) j++;
                String word = line.substring(i, j);
                Type type = KEYWORDS.contains(word) ? Type.KEYWORD : BUILTINS.contains(word) ? Type.BUILTIN : Type.PLAIN;
                out.add(new Token(word, type));
                i = j;
            } else {
                int j = i + 1;
                while (j < n && isPunctuation(line.charAt(j)) && !(line.charAt(j) == '-' && j + 1 < n && line.charAt(j + 1) == '-')) j++;
                out.add(new Token(line.substring(i, j), Type.PLAIN));
                i = j;
            }
        }
        return out;
    }

    private static boolean isPunctuation(char c) {
        return !Character.isLetterOrDigit(c) && !Character.isWhitespace(c) && c != '_' && c != '"' && c != '\'' && c != '$';
    }
}
