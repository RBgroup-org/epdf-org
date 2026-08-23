/*
 * ePDF Engine (epdf-org) — an open-source PDF engine and toolkit.
 * Part of the ePDF Engine project.  Copyright (c) 2026 Rupesh Borse.
 *
 * Licensed under the ePDF Engine License, a permissive open-source license:
 * you may freely use, copy, modify, and distribute this software (including in
 * commercial or closed-source products) provided this notice and the LICENSE
 * file are retained. See LICENSE at the repository root for the full terms.
 *
 * Original clean-room work: no third-party or copyleft (AGPL/GPL) code.
 * Cryptography is intentionally out of scope for epdf-org.
 *
 * Author: Rupesh Borse
 */
package com.epdfengine.rb.org.text.hyphen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Frank Liang's competing-patterns hyphenation algorithm (the method used by TeX
 * and by iText's {@code hyph} module). A word is padded with boundary markers,
 * every substring is looked up in a pattern set, the odd/even inter-letter values
 * compete by taking the maximum, and an odd value marks a legal break — subject to
 * {@code leftMin}/{@code rightMin} minimum fragment lengths. Pattern data is
 * language-specific and supplied separately (see {@link HyphenationPatterns}).
 *
 * <p>This is a clean-room implementation of the published algorithm; no third-party
 * code is used. Layout code can call {@link #hyphenate(String)} to find soft-break
 * points when a long word overflows a line.</p>
 */
public final class Hyphenator {

    private final Map<String, int[]> patterns = new HashMap<>();
    private final Map<String, int[]> exceptions = new HashMap<>();
    private final int leftMin;
    private final int rightMin;
    private int maxPatternLength = 1;

    public Hyphenator(String patternData, String exceptionData, int leftMin, int rightMin) {
        this.leftMin = Math.max(1, leftMin);
        this.rightMin = Math.max(1, rightMin);
        if (patternData != null) {
            for (String p : patternData.trim().split("\\s+")) {
                if (p.isEmpty()) continue;
                String[] parsed = split(p);
                patterns.put(parsed[0], toValues(parsed[1], parsed[0].length()));
                maxPatternLength = Math.max(maxPatternLength, parsed[0].length());
            }
        }
        if (exceptionData != null) {
            for (String e : exceptionData.trim().split("\\s+")) {
                if (e.isEmpty()) continue;
                // Exceptions are written with hyphens, e.g. "as-so-ciate".
                String word = e.replace("-", "").toLowerCase();
                int[] pts = new int[word.length() + 1];
                int idx = 0;
                for (int i = 0; i < e.length(); i++) {
                    if (e.charAt(i) == '-') pts[idx] = 1;
                    else idx++;
                }
                exceptions.put(word, pts);
            }
        }
    }

    /** English hyphenator with the bundled patterns and the classic 2/3 minimums. */
    public static Hyphenator english() {
        return new Hyphenator(HyphenationPatterns.EN_PATTERNS, HyphenationPatterns.EN_EXCEPTIONS, 2, 3);
    }

    /**
     * Returns the legal break positions in {@code word} as a sorted array of split
     * indices ({@code p} means a hyphen may be inserted after {@code word.charAt(p-1)}).
     * Returns an empty array when the word must not be broken.
     */
    public int[] hyphenate(String word) {
        if (word == null || word.length() < leftMin + rightMin) return new int[0];
        String lower = word.toLowerCase();
        int n = lower.length();

        int[] values;
        int[] ex = exceptions.get(lower);
        if (ex != null) {
            values = new int[n + 3];
            // exception points index between letters; map to padded value array
            for (int p = 1; p < n; p++) if (ex[p] == 1) values[p + 1] = 1;
        } else {
            String s = "." + lower + ".";
            values = new int[s.length() + 1];
            for (int i = 0; i < s.length(); i++) {
                int max = Math.min(s.length(), i + maxPatternLength);
                for (int j = i + 1; j <= max; j++) {
                    int[] pv = patterns.get(s.substring(i, j));
                    if (pv == null) continue;
                    for (int k = 0; k < pv.length; k++) {
                        if (pv[k] > values[i + k]) values[i + k] = pv[k];
                    }
                }
            }
        }

        List<Integer> breaks = new ArrayList<>();
        for (int p = leftMin; p <= n - rightMin; p++) {
            if ((values[p + 1] & 1) == 1) breaks.add(p);
        }
        int[] out = new int[breaks.size()];
        for (int i = 0; i < out.length; i++) out[i] = breaks.get(i);
        return out;
    }

    /** Splits {@code word} into syllables using the discovered break points. */
    public List<String> syllables(String word) {
        int[] pts = hyphenate(word);
        List<String> parts = new ArrayList<>();
        int prev = 0;
        for (int p : pts) { parts.add(word.substring(prev, p)); prev = p; }
        parts.add(word.substring(prev));
        return parts;
    }

    // Separates a raw pattern into its letters and its digit string form.
    private static String[] split(String pattern) {
        StringBuilder letters = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (!Character.isDigit(c)) letters.append(c);
        }
        return new String[]{letters.toString(), pattern};
    }

    // Builds the per-position value array (length letters+1) from a raw pattern.
    private static int[] toValues(String pattern, int letterCount) {
        int[] d = new int[letterCount + 1];
        int pos = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (Character.isDigit(c)) d[pos] = c - '0';
            else pos++;
        }
        return d;
    }
}
