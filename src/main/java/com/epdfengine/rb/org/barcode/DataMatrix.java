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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Matrix (ECC 200) encoder, clean-room from ISO/IEC 16022. Supports every
 * standard <em>square</em> symbol from 10×10 up to 132×132 modules, ASCII
 * encodation (digit-pair compaction, single ASCII, and extended-ASCII via the
 * Upper-Shift codeword), 253-state pad randomisation, Reed–Solomon error
 * correction over GF(256) with the Data Matrix primitive polynomial
 * {@code x^8 + x^5 + x^3 + x^2 + 1} (0x12D), multi-block ECC interleaving, the
 * ECC 200 module placement algorithm, and the L-shaped finder plus clock-track
 * timing pattern. Produces a {@link BarcodeMatrix}.
 */
public final class DataMatrix {

    private DataMatrix() {}

    // ---- Symbol specifications (square symbols) ---------------------------
    // {regionsPerSide, regionDataSize, dataCodewords, eccCodewords, rsBlocks}
    private static final int[][] SPECS = {
            { 1,  8,    3,   5, 1 },
            { 1, 10,    5,   7, 1 },
            { 1, 12,    8,  10, 1 },
            { 1, 14,   12,  12, 1 },
            { 1, 16,   18,  14, 1 },
            { 1, 18,   22,  18, 1 },
            { 1, 20,   30,  20, 1 },
            { 1, 22,   36,  24, 1 },
            { 1, 24,   44,  28, 1 },
            { 2, 14,   62,  36, 1 },
            { 2, 16,   86,  42, 1 },
            { 2, 18,  114,  48, 1 },
            { 2, 20,  144,  56, 1 },
            { 2, 22,  174,  68, 1 },
            { 2, 24,  204,  84, 2 },
            { 4, 14,  280, 112, 2 },
            { 4, 16,  368, 144, 4 },
            { 4, 18,  456, 192, 4 },
            { 4, 20,  576, 224, 4 },
            { 4, 22,  688, 272, 4 },
            { 4, 24,  776, 336, 6 },
            { 6, 18, 1050, 408, 6 },
            { 6, 20, 1304, 496, 8 },
    };

    /** Encodes {@code text} (UTF-8 bytes) as a square Data Matrix (ECC 200). */
    public static BarcodeMatrix encode(String text) {
        if (text == null) throw new IllegalArgumentException("DataMatrix: null text");
        int[] data = encodeAscii(text);

        int[] spec = pickSpec(data.length);
        int regions = spec[0], regionData = spec[1], dataCw = spec[2], eccCw = spec[3], blocks = spec[4];

        int[] padded = pad(data, dataCw);
        int[] all = addErrorCorrection(padded, dataCw, eccCw, blocks);

        int nr = regionData * regions;                 // mapping-matrix rows
        int nc = regionData * regions;                 // mapping-matrix cols
        int[][] cwOf = new int[nr][nc];                // 1-based codeword index per cell (0 = unset)
        int[][] bitOf = new int[nr][nc];               // bit index (0..7) per cell
        boolean[][] fixed = new boolean[nr][nc];       // fixed dark corner cells
        boolean[][] fixedDark = new boolean[nr][nc];
        placeBits(cwOf, bitOf, fixed, fixedDark, nr, nc);

        boolean[][] mapping = new boolean[nr][nc];
        for (int r = 0; r < nr; r++) {
            for (int c = 0; c < nc; c++) {
                if (fixed[r][c]) { mapping[r][c] = fixedDark[r][c]; continue; }
                int p = cwOf[r][c];
                if (p == 0) continue;                  // leaves light
                mapping[r][c] = ((all[p - 1] >> bitOf[r][c]) & 1) == 1;
            }
        }

        return assemble(mapping, regions, regionData);
    }

    // ---- ASCII encodation --------------------------------------------------

    private static int[] encodeAscii(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.ISO_8859_1);
        List<Integer> out = new ArrayList<>();
        int i = 0, n = bytes.length;
        while (i < n) {
            int v = bytes[i] & 0xFF;
            if (isDigit(v) && i + 1 < n && isDigit(bytes[i + 1] & 0xFF)) {
                int pair = (v - '0') * 10 + ((bytes[i + 1] & 0xFF) - '0');
                out.add(130 + pair);                   // digit pair 00..99 -> 130..229
                i += 2;
            } else if (v < 128) {
                out.add(v + 1);                        // ASCII value+1
                i++;
            } else {
                out.add(235);                          // Upper Shift
                out.add((v - 128) + 1);
                i++;
            }
        }
        int[] r = new int[out.size()];
        for (int k = 0; k < r.length; k++) r[k] = out.get(k);
        return r;
    }

    private static boolean isDigit(int v) { return v >= '0' && v <= '9'; }

    private static int[] pickSpec(int dataLen) {
        for (int[] s : SPECS) if (s[2] >= dataLen) return s;
        throw new IllegalArgumentException("DataMatrix: data too large (" + dataLen
                + " codewords) for the supported square symbols (max " + SPECS[SPECS.length - 1][2] + ")");
    }

    /** Pads to {@code dataCw} with the 129 EOD/pad codeword and 253-state randomisation. */
    private static int[] pad(int[] data, int dataCw) {
        int[] out = new int[dataCw];
        System.arraycopy(data, 0, out, 0, Math.min(data.length, dataCw));
        if (data.length < dataCw) {
            out[data.length] = 129;                    // first pad
            for (int pos = data.length + 1; pos < dataCw; pos++) {
                int rnd = ((149 * (pos + 1)) % 253) + 1;
                int v = 129 + rnd;
                out[pos] = v <= 254 ? v : v - 254;
            }
        }
        return out;
    }

    // ---- Reed–Solomon (GF(256), primitive 0x12D) --------------------------

    private static final int[] EXP = new int[256];
    private static final int[] LOG = new int[256];
    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            EXP[i] = x;
            LOG[x] = i;
            x <<= 1;
            if (x >= 256) x ^= 0x12D;
        }
    }

    private static int mul(int a, int b) {
        return (a == 0 || b == 0) ? 0 : EXP[(LOG[a] + LOG[b]) % 255];
    }

    /** Generator polynomial g(x) = ∏_{i=1}^{nc} (x − α^i); returns nc+1 coefficients, g[0]=1. */
    private static int[] rsGenerator(int nc) {
        int[] g = { 1 };
        for (int i = 1; i <= nc; i++) {
            int[] ng = new int[g.length + 1];
            for (int j = 0; j < g.length; j++) {
                ng[j] ^= g[j];                         // × x
                ng[j + 1] ^= mul(g[j], EXP[i]);        // × α^i
            }
            g = ng;
        }
        return g;
    }

    private static int[] eccBlock(int[] data, int nc) {
        int[] g = rsGenerator(nc);
        int[] rem = new int[nc];
        for (int d : data) {
            int factor = d ^ rem[0];
            for (int j = 0; j < nc - 1; j++) rem[j] = rem[j + 1] ^ mul(g[j + 1], factor);
            rem[nc - 1] = mul(g[nc], factor);
        }
        return rem;
    }

    /** Appends ECC to the data codewords, interleaving ECC across RS blocks. */
    private static int[] addErrorCorrection(int[] data, int dataCw, int eccCw, int blocks) {
        int[] all = new int[dataCw + eccCw];
        System.arraycopy(data, 0, all, 0, dataCw);
        int eccPerBlock = eccCw / blocks;
        int[][] blockEcc = new int[blocks][];
        for (int b = 0; b < blocks; b++) {
            int count = 0;
            for (int i = b; i < dataCw; i += blocks) count++;
            int[] blockData = new int[count];
            int k = 0;
            for (int i = b; i < dataCw; i += blocks) blockData[k++] = data[i];
            blockEcc[b] = eccBlock(blockData, eccPerBlock);
        }
        int idx = dataCw;
        for (int i = 0; i < eccPerBlock; i++)
            for (int b = 0; b < blocks; b++) all[idx++] = blockEcc[b][i];
        return all;
    }

    // ---- ECC 200 module placement (ISO/IEC 16022 Annex F) -----------------

    private static void placeBits(int[][] cwOf, int[][] bitOf, boolean[][] fixed,
                                  boolean[][] fixedDark, int nr, int nc) {
        int p = 1, r = 4, c = 0;
        do {
            if (r == nr && c == 0)            corner1(cwOf, bitOf, nr, nc, p++);
            if (r == nr - 2 && c == 0 && nc % 4 != 0)        corner2(cwOf, bitOf, nr, nc, p++);
            if (r == nr - 2 && c == 0 && nc % 8 == 4)        corner3(cwOf, bitOf, nr, nc, p++);
            if (r == nr + 4 && c == 2 && nc % 8 == 0)        corner4(cwOf, bitOf, nr, nc, p++);
            do {
                if (r < nr && c >= 0 && cwOf[r][c] == 0) block(cwOf, bitOf, nr, nc, r, c, p++);
                r -= 2; c += 2;
            } while (r >= 0 && c < nc);
            r += 1; c += 3;
            do {
                if (r >= 0 && c < nc && cwOf[r][c] == 0) block(cwOf, bitOf, nr, nc, r, c, p++);
                r += 2; c -= 2;
            } while (r < nr && c >= 0);
            r += 3; c += 1;
        } while (r < nr || c < nc);

        // Fixed bottom-right corner pattern when the final module is unset.
        if (cwOf[nr - 1][nc - 1] == 0) {
            fixed[nr - 1][nc - 1] = true; fixedDark[nr - 1][nc - 1] = true;
            fixed[nr - 2][nc - 2] = true; fixedDark[nr - 2][nc - 2] = true;
        }
    }

    private static void setBit(int[][] cwOf, int[][] bitOf, int nr, int nc, int r, int c, int p, int b) {
        if (r < 0) { r += nr; c += 4 - ((nr + 4) % 8); }
        if (c < 0) { c += nc; r += 4 - ((nc + 4) % 8); }
        cwOf[r][c] = p;
        bitOf[r][c] = b;
    }

    private static void block(int[][] cwOf, int[][] bitOf, int nr, int nc, int r, int c, int p) {
        setBit(cwOf, bitOf, nr, nc, r - 2, c - 2, p, 7);
        setBit(cwOf, bitOf, nr, nc, r - 2, c - 1, p, 6);
        setBit(cwOf, bitOf, nr, nc, r - 1, c - 2, p, 5);
        setBit(cwOf, bitOf, nr, nc, r - 1, c - 1, p, 4);
        setBit(cwOf, bitOf, nr, nc, r - 1, c,     p, 3);
        setBit(cwOf, bitOf, nr, nc, r,     c - 2, p, 2);
        setBit(cwOf, bitOf, nr, nc, r,     c - 1, p, 1);
        setBit(cwOf, bitOf, nr, nc, r,     c,     p, 0);
    }

    private static void corner1(int[][] cwOf, int[][] bitOf, int nr, int nc, int p) {
        setBit(cwOf, bitOf, nr, nc, nr - 1, 0,      p, 7);
        setBit(cwOf, bitOf, nr, nc, nr - 1, 1,      p, 6);
        setBit(cwOf, bitOf, nr, nc, nr - 1, 2,      p, 5);
        setBit(cwOf, bitOf, nr, nc, 0,      nc - 2, p, 4);
        setBit(cwOf, bitOf, nr, nc, 0,      nc - 1, p, 3);
        setBit(cwOf, bitOf, nr, nc, 1,      nc - 1, p, 2);
        setBit(cwOf, bitOf, nr, nc, 2,      nc - 1, p, 1);
        setBit(cwOf, bitOf, nr, nc, 3,      nc - 1, p, 0);
    }

    private static void corner2(int[][] cwOf, int[][] bitOf, int nr, int nc, int p) {
        setBit(cwOf, bitOf, nr, nc, nr - 3, 0,      p, 7);
        setBit(cwOf, bitOf, nr, nc, nr - 2, 0,      p, 6);
        setBit(cwOf, bitOf, nr, nc, nr - 1, 0,      p, 5);
        setBit(cwOf, bitOf, nr, nc, 0,      nc - 4, p, 4);
        setBit(cwOf, bitOf, nr, nc, 0,      nc - 3, p, 3);
        setBit(cwOf, bitOf, nr, nc, 0,      nc - 2, p, 2);
        setBit(cwOf, bitOf, nr, nc, 0,      nc - 1, p, 1);
        setBit(cwOf, bitOf, nr, nc, 1,      nc - 1, p, 0);
    }

    private static void corner3(int[][] cwOf, int[][] bitOf, int nr, int nc, int p) {
        setBit(cwOf, bitOf, nr, nc, nr - 3, 0,      p, 7);
        setBit(cwOf, bitOf, nr, nc, nr - 2, 0,      p, 6);
        setBit(cwOf, bitOf, nr, nc, nr - 1, 0,      p, 5);
        setBit(cwOf, bitOf, nr, nc, 0,      nc - 2, p, 4);
        setBit(cwOf, bitOf, nr, nc, 0,      nc - 1, p, 3);
        setBit(cwOf, bitOf, nr, nc, 1,      nc - 1, p, 2);
        setBit(cwOf, bitOf, nr, nc, 2,      nc - 1, p, 1);
        setBit(cwOf, bitOf, nr, nc, 3,      nc - 1, p, 0);
    }

    private static void corner4(int[][] cwOf, int[][] bitOf, int nr, int nc, int p) {
        setBit(cwOf, bitOf, nr, nc, nr - 1, 0,      p, 7);
        setBit(cwOf, bitOf, nr, nc, nr - 1, nc - 1, p, 6);
        setBit(cwOf, bitOf, nr, nc, 0,      nc - 3, p, 5);
        setBit(cwOf, bitOf, nr, nc, 0,      nc - 2, p, 4);
        setBit(cwOf, bitOf, nr, nc, 0,      nc - 1, p, 3);
        setBit(cwOf, bitOf, nr, nc, 1,      nc - 3, p, 2);
        setBit(cwOf, bitOf, nr, nc, 1,      nc - 2, p, 1);
        setBit(cwOf, bitOf, nr, nc, 1,      nc - 1, p, 0);
    }

    // ---- Assemble physical symbol (finder + timing around each region) ----

    private static BarcodeMatrix assemble(boolean[][] mapping, int regions, int regionData) {
        int physRegion = regionData + 2;               // finder adds 1 module on each side
        int size = regions * physRegion;
        boolean[][] m = new boolean[size][size];
        for (int rr = 0; rr < regions; rr++) {
            for (int cc = 0; cc < regions; cc++) {
                int baseR = rr * physRegion;
                int baseC = cc * physRegion;
                // solid left column + solid bottom row (the "L" finder)
                for (int i = 0; i < physRegion; i++) {
                    m[baseR + i][baseC] = true;                          // left solid
                    m[baseR + physRegion - 1][baseC + i] = true;         // bottom solid
                }
                // top + right clock track (top dark on even cols, right dark on odd rows
                // so the shared top-right corner stays light)
                for (int i = 0; i < physRegion; i++) {
                    if (i % 2 == 0) m[baseR][baseC + i] = true;                  // top timing
                    if (i % 2 == 1) m[baseR + i][baseC + physRegion - 1] = true; // right timing
                }
                // data cells
                for (int dr = 0; dr < regionData; dr++) {
                    for (int dc = 0; dc < regionData; dc++) {
                        m[baseR + 1 + dr][baseC + 1 + dc] = mapping[rr * regionData + dr][cc * regionData + dc];
                    }
                }
            }
        }
        return new BarcodeMatrix(m, 1);
    }
}
