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
 * Code 128 linear barcode encoder (ISO/IEC 15417). Clean-room implementation of
 * the published symbology: 106 symbol characters (each 11 modules of three bars
 * and three spaces, plus a 13-module stop), a modulo-103 symbol check character,
 * and code sets A/B/C. This encoder auto-selects code set C for even-length runs
 * of digits and code set B otherwise, which yields compact, spec-valid symbols
 * for the common alphanumeric case.
 */
public final class Code128 {

    private Code128() {}

    /** Width patterns for symbol values 0..106 (element widths, bar/space alternating). */
    private static final String[] PATTERNS = {
        "212222","222122","222221","121223","121322","131222","122213","122312","132212","221213",
        "221312","231212","112232","122132","122231","113222","123122","123221","223211","221132",
        "221231","213212","223112","312131","311222","321122","321221","312212","322112","322211",
        "212123","212321","232121","111323","131123","131321","112313","132113","132311","211313",
        "231113","231311","112133","112331","132131","113123","113321","133121","313121","211331",
        "231131","213113","213311","213131","311123","311321","331121","312113","312311","332111",
        "314111","221411","431111","111224","111422","121124","121421","141122","141221","112214",
        "112412","122114","122411","142112","142211","241211","221114","413111","241112","134111",
        "111242","121142","121241","114212","124112","124211","411212","421112","421211","212141",
        "214121","412121","111143","111341","131141","114113","114311","411113","411311","113141",
        "114131","311141","411131","211412","211214","211232","2331112"
    };

    private static final int START_B = 104, START_C = 105, STOP = 106;

    /** Encodes ASCII text (0x20..0x7E) into a Code 128 module row (true = dark bar). */
    public static BarcodeMatrix encode(String text) {
        if (text == null || text.isEmpty()) throw new IllegalArgumentException("Code128: empty text");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 32 || c > 126) throw new IllegalArgumentException("Code128: unsupported char U+" + Integer.toHexString(c));
        }

        java.util.List<Integer> codes = new java.util.ArrayList<>();
        boolean codeC = startsWithDigitPair(text, 0);
        codes.add(codeC ? START_C : START_B);

        int i = 0;
        while (i < text.length()) {
            if (codeC) {
                if (i + 1 < text.length() && isDigit(text.charAt(i)) && isDigit(text.charAt(i + 1))) {
                    codes.add((text.charAt(i) - '0') * 10 + (text.charAt(i + 1) - '0'));
                    i += 2;
                    if (!startsWithDigitPair(text, i) && i < text.length()) { codes.add(100); codeC = false; } // Code B
                } else {
                    codes.add(100); codeC = false;                                                            // switch to Code B
                }
            } else {
                if (startsWithDigitPair(text, i) && digitRun(text, i) >= 4) {
                    codes.add(99); codeC = true;                                                              // switch to Code C
                } else {
                    codes.add(text.charAt(i) - 32);
                    i++;
                }
            }
        }

        long sum = codes.get(0);
        for (int k = 1; k < codes.size(); k++) sum += (long) k * codes.get(k);
        codes.add((int) (sum % 103));
        codes.add(STOP);

        StringBuilder mods = new StringBuilder();
        for (int code : codes) {
            String p = PATTERNS[code];
            boolean bar = true;                          // each pattern starts with a bar
            for (int e = 0; e < p.length(); e++) {
                int w = p.charAt(e) - '0';
                for (int m = 0; m < w; m++) mods.append(bar ? '1' : '0');
                bar = !bar;
            }
        }

        boolean[] row = new boolean[mods.length()];
        for (int c = 0; c < row.length; c++) row[c] = mods.charAt(c) == '1';
        return BarcodeMatrix.ofRow(row, 10);             // 10-module quiet zone each side
    }

    private static boolean isDigit(char c) { return c >= '0' && c <= '9'; }

    private static boolean startsWithDigitPair(String s, int i) {
        return i + 1 < s.length() && isDigit(s.charAt(i)) && isDigit(s.charAt(i + 1));
    }

    private static int digitRun(String s, int i) {
        int n = 0;
        while (i + n < s.length() && isDigit(s.charAt(i + n))) n++;
        return n;
    }
}
