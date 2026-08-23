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
 * EAN-13 linear barcode encoder (GS1 General Specifications). Clean-room
 * implementation: 95 modules — start guard, six left digits encoded with the
 * L/G parity pattern selected by the first digit, a centre guard, six R-encoded
 * right digits, and an end guard. Accepts 12 digits (the modulo-10 check digit
 * is computed) or 13 (the check digit is recomputed to guarantee validity).
 * UPC-A is EAN-13 with a leading zero, so a 12-digit UPC-A can be encoded by
 * prefixing {@code "0"}.
 */
public final class EAN13 {

    private EAN13() {}

    private static final String[] L = {
        "0001101","0011001","0010011","0111101","0100011",
        "0110001","0101111","0111011","0110111","0001011"
    };
    private static final String[] G = {
        "0100111","0110011","0011011","0100001","0011101",
        "0111001","0000101","0010001","0001001","0010111"
    };
    private static final String[] R = {
        "1110010","1100110","1101100","1000010","1011100",
        "1001110","1010000","1000100","1001000","1110100"
    };
    /** Left-half L/G parity selection per first digit (true = G). */
    private static final String[] PARITY = {
        "LLLLLL","LLGLGG","LLGGLG","LLGGGL","LGLLGG",
        "LGGLLG","LGGGLL","LGLGLG","LGLGGL","LGGLGL"
    };

    public static BarcodeMatrix encode(String digits) {
        if (digits == null) throw new IllegalArgumentException("EAN13: null");
        String d = digits.trim();
        for (int i = 0; i < d.length(); i++)
            if (d.charAt(i) < '0' || d.charAt(i) > '9')
                throw new IllegalArgumentException("EAN13: non-digit '" + d.charAt(i) + "'");
        if (d.length() == 13) d = d.substring(0, 12);
        if (d.length() != 12) throw new IllegalArgumentException("EAN13: need 12 or 13 digits, got " + d.length());
        d = d + checkDigit(d);

        int[] n = new int[13];
        for (int i = 0; i < 13; i++) n[i] = d.charAt(i) - '0';

        StringBuilder m = new StringBuilder();
        m.append("101");                                   // start guard
        String parity = PARITY[n[0]];
        for (int i = 0; i < 6; i++)
            m.append(parity.charAt(i) == 'L' ? L[n[1 + i]] : G[n[1 + i]]);
        m.append("01010");                                 // centre guard
        for (int i = 0; i < 6; i++) m.append(R[n[7 + i]]);
        m.append("101");                                   // end guard

        boolean[] row = new boolean[m.length()];
        for (int c = 0; c < row.length; c++) row[c] = m.charAt(c) == '1';
        return BarcodeMatrix.ofRow(row, 11);               // EAN mandates wide quiet zones
    }

    private static char checkDigit(String d12) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int v = d12.charAt(i) - '0';
            sum += (i % 2 == 0) ? v : v * 3;               // odd positions x1, even x3 (1-indexed)
        }
        return (char) ('0' + (10 - sum % 10) % 10);
    }
}
