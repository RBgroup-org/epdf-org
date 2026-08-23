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
package com.epdfengine.rb.org.barcode;

/**
 * Code 39 linear barcode encoder (ISO/IEC 16388). Clean-room implementation of
 * the published symbology: 43 data characters plus the {@code *} start/stop, each
 * encoded as nine elements (five bars, four spaces) of which exactly three are
 * wide. A single narrow space separates characters. This encoder uses a 3:1
 * wide-to-narrow ratio and does not add the optional modulo-43 check character.
 */
public final class Code39 {

    private Code39() {}

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%";

    /** For each character: nine elements (bar,space,... bar), 1 = wide, 0 = narrow. */
    private static final String[] PATTERNS = {
        "000110100","100100001","001100001","101100000","000110001", // 0-4
        "100110000","001110000","000100101","100100100","001100100", // 5-9
        "100001001","001001001","101001000","000011001","100011000", // A-E
        "001011000","000001101","100001100","001001100","000011100", // F-J
        "100000011","001000011","101000010","000010011","100010010", // K-O
        "001010010","000000111","100000110","001000110","000010110", // P-T
        "110000001","011000001","111000000","010010001","110010000", // U-Y
        "011010000","010000101","110000100","011000100","010101000", // Z - . space $
        "010100010","010001010","000101010"                          // / + %
    };
    private static final String START_STOP = "010010100";   // '*'

    private static final int WIDE = 3, NARROW = 1, GAP = 1;

    /** Encodes uppercase alphanumeric Code 39 text into a module row (true = dark bar). */
    public static BarcodeMatrix encode(String text) {
        if (text == null || text.isEmpty()) throw new IllegalArgumentException("Code39: empty text");
        StringBuilder mods = new StringBuilder();
        appendChar(mods, START_STOP);
        for (int i = 0; i < text.length(); i++) {
            appendGap(mods);
            int idx = ALPHABET.indexOf(Character.toUpperCase(text.charAt(i)));
            if (idx < 0) throw new IllegalArgumentException("Code39: unsupported char '" + text.charAt(i) + "'");
            appendChar(mods, PATTERNS[idx]);
        }
        appendGap(mods);
        appendChar(mods, START_STOP);

        boolean[] row = new boolean[mods.length()];
        for (int c = 0; c < row.length; c++) row[c] = mods.charAt(c) == '1';
        return BarcodeMatrix.ofRow(row, 10);
    }

    private static void appendChar(StringBuilder mods, String pattern) {
        boolean bar = true;                              // elements alternate bar, space, ...
        for (int e = 0; e < pattern.length(); e++) {
            int w = pattern.charAt(e) == '1' ? WIDE : NARROW;
            for (int m = 0; m < w; m++) mods.append(bar ? '1' : '0');
            bar = !bar;
        }
    }

    private static void appendGap(StringBuilder mods) {
        for (int m = 0; m < GAP; m++) mods.append('0');  // narrow inter-character space
    }
}
