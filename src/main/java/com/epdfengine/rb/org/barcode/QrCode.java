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

/**
 * QR Code (Model 2) encoder, clean-room from ISO/IEC 18004. Supports versions
 * 1–40, all four error-correction levels (L/M/Q/H), numeric / alphanumeric /
 * byte(UTF-8) modes with automatic single-segment mode selection, Reed–Solomon
 * error correction over GF(256), the eight data masks with penalty scoring, and
 * BCH-coded format/version information. Produces a {@link BarcodeMatrix}.
 */
public final class QrCode {

    public enum Ecc { L, M, Q, H }

    private QrCode() {}

    // ---- Public API --------------------------------------------------------

    public static BarcodeMatrix encode(String text) { return encode(text, Ecc.M); }

    public static BarcodeMatrix encode(String text, Ecc ecc) {
        if (text == null) throw new IllegalArgumentException("QrCode: null text");
        int mode = chooseMode(text);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        int dataLen = (mode == BYTE) ? bytes.length : text.length();

        int version = chooseVersion(mode, dataLen, ecc);
        BitBuffer bits = new BitBuffer();
        bits.append(MODE_IND[mode], 4);
        bits.append(dataLen, charCountBits(mode, version));
        encodeData(text, bytes, mode, bits);

        int totalDataCw = totalDataCodewords(version, ecc);
        int capacityBits = totalDataCw * 8;
        int terminator = Math.min(4, capacityBits - bits.length());
        bits.append(0, terminator);
        bits.append(0, (8 - bits.length() % 8) % 8);                 // pad to byte
        for (int pad = 0xEC; bits.length() < capacityBits; pad ^= 0xEC ^ 0x11) bits.append(pad, 8);

        byte[] dataCw = bits.toBytes();
        byte[] finalCw = interleaveWithEcc(dataCw, version, ecc);

        return buildMatrix(finalCw, version, ecc);
    }

    // ---- Mode selection + data encoding ------------------------------------

    private static final int NUMERIC = 0, ALPHANUMERIC = 1, BYTE = 2;
    private static final int[] MODE_IND = { 0b0001, 0b0010, 0b0100 };
    private static final String ALNUM = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";

    private static int chooseMode(String s) {
        boolean numeric = !s.isEmpty(), alnum = !s.isEmpty();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') numeric = false;
            if (ALNUM.indexOf(c) < 0) alnum = false;
        }
        return numeric ? NUMERIC : alnum ? ALPHANUMERIC : BYTE;
    }

    private static int charCountBits(int mode, int version) {
        int grp = version <= 9 ? 0 : version <= 26 ? 1 : 2;
        switch (mode) {
            case NUMERIC:      return new int[]{10, 12, 14}[grp];
            case ALPHANUMERIC: return new int[]{9, 11, 13}[grp];
            default:           return new int[]{8, 16, 16}[grp];
        }
    }

    private static void encodeData(String text, byte[] bytes, int mode, BitBuffer bits) {
        switch (mode) {
            case NUMERIC -> {
                for (int i = 0; i < text.length(); ) {
                    int n = Math.min(3, text.length() - i);
                    int val = Integer.parseInt(text.substring(i, i + n));
                    bits.append(val, n == 3 ? 10 : n == 2 ? 7 : 4);
                    i += n;
                }
            }
            case ALPHANUMERIC -> {
                for (int i = 0; i < text.length(); ) {
                    if (i + 1 < text.length()) {
                        int v = ALNUM.indexOf(text.charAt(i)) * 45 + ALNUM.indexOf(text.charAt(i + 1));
                        bits.append(v, 11);
                        i += 2;
                    } else {
                        bits.append(ALNUM.indexOf(text.charAt(i)), 6);
                        i++;
                    }
                }
            }
            default -> { for (byte b : bytes) bits.append(b & 0xFF, 8); }
        }
    }

    private static int chooseVersion(int mode, int dataLen, Ecc ecc) {
        for (int v = 1; v <= 40; v++) {
            int cap = totalDataCodewords(v, ecc) * 8;
            int used = 4 + charCountBits(mode, v) + dataBitLength(mode, dataLen);
            if (used <= cap) return v;
        }
        throw new IllegalArgumentException("QrCode: data too large for version 40");
    }

    private static int dataBitLength(int mode, int len) {
        switch (mode) {
            case NUMERIC:      return (len / 3) * 10 + (len % 3 == 2 ? 7 : len % 3 == 1 ? 4 : 0);
            case ALPHANUMERIC: return (len / 2) * 11 + (len % 2) * 6;
            default:           return len * 8;
        }
    }

    // ---- Error correction (Reed–Solomon over GF(256)) ----------------------

    private static byte[] interleaveWithEcc(byte[] data, int version, Ecc ecc) {
        int[] e = eccEntry(version, ecc);
        int ecPerBlock = e[0], g1 = e[1], dc1 = e[2], g2 = e[3], dc2 = e[4];
        int numBlocks = g1 + g2;

        byte[][] dataBlocks = new byte[numBlocks][];
        byte[][] eccBlocks = new byte[numBlocks][];
        byte[] gen = rsGenerator(ecPerBlock);
        int p = 0;
        for (int b = 0; b < numBlocks; b++) {
            int dc = b < g1 ? dc1 : dc2;
            byte[] blk = new byte[dc];
            System.arraycopy(data, p, blk, 0, dc);
            p += dc;
            dataBlocks[b] = blk;
            eccBlocks[b] = rsEncode(blk, gen);
        }

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int maxDc = Math.max(dc1, dc2);
        for (int i = 0; i < maxDc; i++)
            for (int b = 0; b < numBlocks; b++)
                if (i < dataBlocks[b].length) out.write(dataBlocks[b][i]);
        for (int i = 0; i < ecPerBlock; i++)
            for (int b = 0; b < numBlocks; b++) out.write(eccBlocks[b][i]);
        return out.toByteArray();
    }

    private static final int[] GF_EXP = new int[512];
    private static final int[] GF_LOG = new int[256];
    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            GF_EXP[i] = x;
            GF_LOG[x] = i;
            x <<= 1;
            if ((x & 0x100) != 0) x ^= 0x11D;                        // primitive polynomial
        }
        for (int i = 255; i < 512; i++) GF_EXP[i] = GF_EXP[i - 255];
    }

    private static int gfMul(int a, int b) {
        return (a == 0 || b == 0) ? 0 : GF_EXP[GF_LOG[a] + GF_LOG[b]];
    }

    private static byte[] rsGenerator(int degree) {
        int[] g = { 1 };
        for (int i = 0; i < degree; i++) {
            int[] ng = new int[g.length + 1];
            for (int j = 0; j < g.length; j++) {
                ng[j] ^= g[j];
                ng[j + 1] ^= gfMul(g[j], GF_EXP[i]);
            }
            g = ng;
        }
        byte[] out = new byte[g.length];
        for (int i = 0; i < g.length; i++) out[i] = (byte) g[i];
        return out;
    }

    private static byte[] rsEncode(byte[] data, byte[] gen) {
        int ec = gen.length - 1;
        byte[] res = new byte[ec];
        for (byte datum : data) {
            int factor = (datum ^ res[0]) & 0xFF;
            System.arraycopy(res, 1, res, 0, ec - 1);
            res[ec - 1] = 0;
            for (int j = 0; j < ec; j++) res[j] ^= (byte) gfMul(gen[j + 1] & 0xFF, factor);
        }
        return res;
    }

    // ---- Matrix construction -----------------------------------------------

    private static BarcodeMatrix buildMatrix(byte[] codewords, int version, Ecc ecc) {
        int size = version * 4 + 17;
        int[][] mod = new int[size][size];        // -1 = unset, 0/1 = light/dark
        for (int[] r : mod) java.util.Arrays.fill(r, -1);

        placeFinder(mod, 0, 0);
        placeFinder(mod, size - 7, 0);
        placeFinder(mod, 0, size - 7);
        placeSeparators(mod, size);
        placeAlignment(mod, version, size);
        placeTiming(mod, size);
        mod[size - 8][8] = 1;                     // dark module
        reserveFormat(mod, size);
        if (version >= 7) reserveVersion(mod, size);

        placeData(mod, codewords, size);

        int bestMask = 0, bestPenalty = Integer.MAX_VALUE;
        int[][] best = null;
        int forced = Integer.getInteger("qr.mask", -1);
        for (int mask = 0; mask < 8; mask++) {
            if (forced >= 0 && mask != forced) continue;
            int[][] cand = applyMask(mod, size, mask);
            placeFormat(cand, size, ecc, mask);
            if (version >= 7) placeVersion(cand, size, version);
            int p = penalty(cand, size);
            if (p < bestPenalty) { bestPenalty = p; bestMask = mask; best = cand; }
        }

        boolean[][] out = new boolean[size][size];
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) out[y][x] = best[y][x] == 1;
        return new BarcodeMatrix(out, 4);         // 4-module quiet zone (spec minimum)
    }

    private static void placeFinder(int[][] m, int col, int row) {
        for (int r = -1; r <= 7; r++)
            for (int c = -1; c <= 7; c++) {
                int rr = row + r, cc = col + c;
                if (rr < 0 || cc < 0 || rr >= m.length || cc >= m.length) continue;
                boolean dark = (r >= 0 && r <= 6 && (c == 0 || c == 6))
                        || (c >= 0 && c <= 6 && (r == 0 || r == 6))
                        || (r >= 2 && r <= 4 && c >= 2 && c <= 4);
                m[rr][cc] = dark ? 1 : 0;
            }
    }

    private static void placeSeparators(int[][] m, int size) {
        for (int i = 0; i < 8; i++) {
            setIfUnset(m, i, 7); setIfUnset(m, 7, i);
            setIfUnset(m, size - 1 - i, 7); setIfUnset(m, size - 8, i);
            setIfUnset(m, 7, size - 1 - i); setIfUnset(m, i, size - 8);
        }
    }

    private static void setIfUnset(int[][] m, int r, int c) { if (m[r][c] == -1) m[r][c] = 0; }

    private static void placeTiming(int[][] m, int size) {
        for (int i = 8; i < size - 8; i++) {
            if (m[6][i] == -1) m[6][i] = (i % 2 == 0) ? 1 : 0;
            if (m[i][6] == -1) m[i][6] = (i % 2 == 0) ? 1 : 0;
        }
    }

    private static void placeAlignment(int[][] m, int version, int size) {
        int[] pos = ALIGN_POS[version - 1];
        for (int r : pos)
            for (int c : pos) {
                if ((r == 6 && c == 6) || (r == 6 && c == size - 7) || (r == size - 7 && c == 6)) continue;
                for (int dr = -2; dr <= 2; dr++)
                    for (int dc = -2; dc <= 2; dc++) {
                        boolean dark = Math.max(Math.abs(dr), Math.abs(dc)) != 1;
                        m[r + dr][c + dc] = dark ? 1 : 0;
                    }
            }
    }

    private static void reserveFormat(int[][] m, int size) {
        for (int i = 0; i < 9; i++) { if (m[8][i] == -1) m[8][i] = 0; if (m[i][8] == -1) m[i][8] = 0; }
        for (int i = 0; i < 8; i++) { if (m[8][size - 1 - i] == -1) m[8][size - 1 - i] = 0; if (m[size - 1 - i][8] == -1) m[size - 1 - i][8] = 0; }
    }

    private static void reserveVersion(int[][] m, int size) {
        for (int i = 0; i < 6; i++)
            for (int j = 0; j < 3; j++) { m[i][size - 11 + j] = 0; m[size - 11 + j][i] = 0; }
    }

    private static void placeData(int[][] m, byte[] cw, int size) {
        int bit = 0, dirUp = -1;
        for (int col = size - 1; col > 0; col -= 2) {
            if (col == 6) col--;                  // skip vertical timing column
            for (int i = 0; i < size; i++) {
                int row = dirUp < 0 ? size - 1 - i : i;
                for (int c = 0; c < 2; c++) {
                    int cc = col - c;
                    if (m[row][cc] != -1) continue;
                    int v = 0;
                    if (bit < cw.length * 8) v = (cw[bit >> 3] >> (7 - (bit & 7))) & 1;
                    m[row][cc] = v;
                    bit++;
                }
            }
            dirUp = -dirUp;
        }
    }

    private static int[][] applyMask(int[][] m, int size, int mask) {
        int[][] out = new int[size][size];
        boolean[][] fn = functionModules(m, size);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                int v = m[y][x];
                if (!fn[y][x] && maskBit(mask, x, y)) v ^= 1;
                out[y][x] = v;
            }
        return out;
    }

    private static boolean maskBit(int mask, int x, int y) {
        switch (mask) {
            case 0: return (x + y) % 2 == 0;
            case 1: return y % 2 == 0;
            case 2: return x % 3 == 0;
            case 3: return (x + y) % 3 == 0;
            case 4: return (y / 2 + x / 3) % 2 == 0;
            case 5: return (x * y) % 2 + (x * y) % 3 == 0;
            case 6: return ((x * y) % 2 + (x * y) % 3) % 2 == 0;
            default: return ((x + y) % 2 + (x * y) % 3) % 2 == 0;
        }
    }

    /** Marks function-pattern / reserved modules so masking and format bits skip them. */
    private static boolean[][] functionModules(int[][] m, int size) {
        boolean[][] fn = new boolean[size][size];
        for (int i = 0; i < size; i++) { fn[6][i] = true; fn[i][6] = true; }   // timing
        markSquare(fn, 0, 0, 9); markSquare(fn, size - 8, 0, 8, 9); markSquare(fn, 0, size - 8, 9, 8);
        int[] pos = ALIGN_POS[(size - 17) / 4 - 1];
        for (int r : pos) for (int c : pos) {
            if ((r == 6 && c == 6) || (r == 6 && c == size - 7) || (r == size - 7 && c == 6)) continue;
            for (int dr = -2; dr <= 2; dr++) for (int dc = -2; dc <= 2; dc++) fn[r + dr][c + dc] = true;
        }
        if (size >= 45) for (int i = 0; i < 6; i++) for (int j = 0; j < 3; j++) {
            fn[i][size - 11 + j] = true; fn[size - 11 + j][i] = true;
        }
        return fn;
    }

    private static void markSquare(boolean[][] fn, int r0, int c0, int h) { markSquare(fn, r0, c0, h, h); }
    private static void markSquare(boolean[][] fn, int r0, int c0, int h, int w) {
        for (int r = 0; r < h; r++) for (int c = 0; c < w; c++) {
            int rr = r0 + r, cc = c0 + c;
            if (rr < fn.length && cc < fn.length) fn[rr][cc] = true;
        }
    }

    private static void placeFormat(int[][] m, int size, Ecc ecc, int mask) {
        int eccBits = new int[]{1, 0, 3, 2}[ecc.ordinal()];          // L=01,M=00,Q=11,H=10
        int data = (eccBits << 3) | mask;                           // 5-bit data
        int bch = data << 10;                                       // BCH(15,5), generator 0x537
        for (int i = 14; i >= 10; i--) if (((bch >> i) & 1) != 0) bch ^= 0x537 << (i - 10);
        int bits = ((data << 10) | (bch & 0x3FF)) ^ 0x5412;         // mask pattern 101010000010010
        for (int i = 0; i <= 5; i++) m[i][8] = (bits >> i) & 1;
        m[7][8] = (bits >> 6) & 1; m[8][8] = (bits >> 7) & 1; m[8][7] = (bits >> 8) & 1;
        for (int i = 9; i < 15; i++) m[8][14 - i] = (bits >> i) & 1;
        for (int i = 0; i < 8; i++) m[8][size - 1 - i] = (bits >> i) & 1;
        for (int i = 8; i < 15; i++) m[size - 15 + i][8] = (bits >> i) & 1;
        m[size - 8][8] = 1;                                          // dark module
    }

    private static void placeVersion(int[][] m, int size, int version) {
        int bch = version << 12;                                    // BCH(18,6), generator 0x1F25
        for (int i = 17; i >= 12; i--) if (((bch >> i) & 1) != 0) bch ^= 0x1F25 << (i - 12);
        int bits = (version << 12) | (bch & 0xFFF);
        for (int i = 0; i < 18; i++) {
            int b = (bits >> i) & 1, r = i / 3, c = i % 3;
            m[r][size - 11 + c] = b; m[size - 11 + c][r] = b;
        }
    }

    private static int penalty(int[][] m, int size) {
        int p = 0;
        // Rule 1: runs of 5+ same colour.
        for (int y = 0; y < size; y++) { p += runPenalty(m, size, y, true); p += runPenalty(m, size, y, false); }
        // Rule 2: 2x2 blocks.
        for (int y = 0; y < size - 1; y++)
            for (int x = 0; x < size - 1; x++)
                if (m[y][x] == m[y][x + 1] && m[y][x] == m[y + 1][x] && m[y][x] == m[y + 1][x + 1]) p += 3;
        // Rule 3: finder-like 1:1:3:1:1 with 4 light either side.
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                if (x <= size - 11 && finderLike(m, y, x, true)) p += 40;
                if (y <= size - 11 && finderLike(m, y, x, false)) p += 40;
            }
        // Rule 4: dark-module proportion.
        int dark = 0;
        for (int[] row : m) for (int v : row) dark += v;
        int percent = dark * 100 / (size * size);
        int k = Math.abs(percent - 50);
        p += (k / 5) * 10;
        return p;
    }

    private static int runPenalty(int[][] m, int size, int line, boolean row) {
        int p = 0, run = 1, prev = -1;
        for (int i = 0; i < size; i++) {
            int v = row ? m[line][i] : m[i][line];
            if (v == prev) { run++; if (run == 5) p += 3; else if (run > 5) p += 1; }
            else { run = 1; prev = v; }
        }
        return p;
    }

    private static final int[] FINDER = {1, 0, 1, 1, 1, 0, 1};
    private static boolean finderLike(int[][] m, int y, int x, boolean horiz) {
        for (int i = 0; i < 7; i++) {
            int v = horiz ? m[y][x + i] : m[y + i][x];
            if (v != FINDER[i]) return false;
        }
        // Require 4 light modules on one side (spec: 0000 1011101 or 1011101 0000).
        boolean before = true, after = true;
        for (int i = 1; i <= 4; i++) {
            int b = horiz ? (x - i >= 0 ? m[y][x - i] : -1) : (y - i >= 0 ? m[y - i][x] : -1);
            int a = horiz ? (x + 6 + i < m.length ? m[y][x + 6 + i] : -1) : (y + 6 + i < m.length ? m[y + 6 + i][x] : -1);
            if (b != 0) before = false;
            if (a != 0) after = false;
        }
        return before || after;
    }

    // ---- ECC / capacity tables (ISO/IEC 18004) -----------------------------

    /** {ecPerBlock, g1Blocks, g1DataCw, g2Blocks, g2DataCw} per version, level order L,M,Q,H. */
    private static int[] eccEntry(int version, Ecc ecc) { return ECC[(version - 1) * 4 + ecc.ordinal()]; }

    private static int totalDataCodewords(int version, Ecc ecc) {
        int[] e = eccEntry(version, ecc);
        return e[1] * e[2] + e[3] * e[4];
    }

    private static final int[][] ECC = buildEcc();

    private static int[][] buildEcc() {
        // Flat rows of {ecPerBlock,g1,dc1,g2,dc2} for versions 1..40, each level L,M,Q,H.
        int[][] t = {
            {7,1,19,0,0},{10,1,16,0,0},{13,1,13,0,0},{17,1,9,0,0},
            {10,1,34,0,0},{16,1,28,0,0},{22,1,22,0,0},{28,1,16,0,0},
            {15,1,55,0,0},{26,1,44,0,0},{18,2,17,0,0},{22,2,13,0,0},
            {20,1,80,0,0},{18,2,32,0,0},{26,2,24,0,0},{16,4,9,0,0},
            {26,1,108,0,0},{24,2,43,0,0},{18,2,15,2,16},{22,2,11,2,12},
            {18,2,68,0,0},{16,4,27,0,0},{24,4,19,0,0},{28,4,15,0,0},
            {20,2,78,0,0},{18,4,31,0,0},{18,2,14,4,15},{26,4,13,1,14},
            {24,2,97,0,0},{22,2,38,2,39},{22,4,18,2,19},{26,4,14,2,15},
            {30,2,116,0,0},{22,3,36,2,37},{20,4,16,4,17},{24,4,12,4,13},
            {18,2,68,2,69},{26,4,43,1,44},{24,6,19,2,20},{28,6,15,2,16},
            {20,4,81,0,0},{30,1,50,4,51},{28,4,22,4,23},{24,3,12,8,13},
            {24,2,92,2,93},{22,6,36,2,37},{26,4,20,6,21},{28,7,14,4,15},
            {26,4,107,0,0},{22,8,37,1,38},{24,8,20,4,21},{22,12,11,4,12},
            {30,3,115,1,116},{24,4,40,5,41},{20,11,16,5,17},{24,11,12,5,13},
            {22,5,87,1,88},{24,5,41,5,42},{30,5,24,7,25},{24,11,12,7,13},
            {24,5,98,1,99},{28,7,45,3,46},{24,15,19,2,20},{30,3,15,13,16},
            {28,1,107,5,108},{28,10,46,1,47},{28,1,22,15,23},{28,2,14,17,15},
            {30,5,120,1,121},{26,9,43,4,44},{28,17,22,1,23},{28,2,14,19,15},
            {28,3,113,4,114},{26,3,44,11,45},{26,17,21,4,22},{26,9,13,16,14},
            {28,3,107,5,108},{26,3,41,13,42},{30,15,24,5,25},{28,15,15,10,16},
            {28,4,116,4,117},{26,17,42,0,0},{28,17,22,6,23},{30,19,16,6,17},
            {28,2,111,7,112},{28,17,46,0,0},{30,7,24,16,25},{24,34,13,0,0},
            {30,4,121,5,122},{28,4,47,14,48},{30,11,24,14,25},{30,16,15,14,16},
            {30,6,117,4,118},{28,6,45,14,46},{30,11,24,16,25},{30,30,16,2,17},
            {26,8,106,4,107},{28,8,47,13,48},{30,7,24,22,25},{30,22,15,13,16},
            {28,10,114,2,115},{28,19,46,4,47},{28,28,22,6,23},{30,33,16,4,17},
            {30,8,122,4,123},{28,22,45,3,46},{30,8,23,26,24},{30,12,15,28,16},
            {30,3,117,10,118},{28,3,45,23,46},{30,4,24,31,25},{30,11,15,31,16},
            {30,7,116,7,117},{28,21,45,7,46},{30,1,23,37,24},{30,19,15,26,16},
            {30,5,115,10,116},{28,19,47,10,48},{30,15,24,25,25},{30,23,15,25,16},
            {30,13,115,3,116},{28,2,46,29,47},{30,42,24,1,25},{30,23,15,28,16},
            {30,17,115,0,0},{28,10,46,23,47},{30,10,24,35,25},{30,19,15,35,16},
            {30,17,115,1,116},{28,14,46,21,47},{30,29,24,19,25},{30,11,15,46,16},
            {30,13,115,6,116},{28,14,46,23,47},{30,44,24,7,25},{30,59,16,1,17},
            {30,12,121,7,122},{28,12,47,26,48},{30,39,24,14,25},{30,22,15,41,16},
            {30,6,121,14,122},{28,6,47,34,48},{30,46,24,10,25},{30,2,15,64,16},
            {30,17,122,4,123},{28,29,46,14,47},{30,49,24,10,25},{30,24,15,46,16},
            {30,4,122,18,123},{28,13,46,32,47},{30,48,24,14,25},{30,42,15,32,16},
            {30,20,117,4,118},{28,40,47,7,48},{30,43,24,22,25},{30,10,15,67,16},
            {30,19,118,6,119},{28,18,47,31,48},{30,34,24,34,25},{30,20,15,61,16}
        };
        return t;
    }

    private static final int[][] ALIGN_POS = {
        {}, {6,18}, {6,22}, {6,26}, {6,30}, {6,34}, {6,22,38}, {6,24,42}, {6,26,46}, {6,28,50},
        {6,30,54}, {6,32,58}, {6,34,62}, {6,26,46,66}, {6,26,48,70}, {6,26,50,74}, {6,30,54,78},
        {6,30,56,82}, {6,30,58,86}, {6,34,62,90}, {6,28,50,72,94}, {6,26,50,74,98}, {6,30,54,78,102},
        {6,28,54,80,106}, {6,32,58,84,110}, {6,30,58,86,114}, {6,34,62,90,118}, {6,26,50,74,98,122},
        {6,30,54,78,102,126}, {6,26,52,78,104,130}, {6,30,56,82,108,134}, {6,34,60,86,112,138},
        {6,30,58,86,114,142}, {6,34,62,90,118,146}, {6,30,54,78,102,126,150}, {6,24,50,76,102,128,154},
        {6,28,54,80,106,132,158}, {6,32,58,84,110,136,162}, {6,26,54,82,110,138,166}, {6,30,58,86,114,142,170}
    };

    // ---- Bit buffer --------------------------------------------------------

    private static final class BitBuffer {
        private byte[] buf = new byte[64];
        private int len;

        void append(int value, int bits) {
            for (int i = bits - 1; i >= 0; i--) {
                if (len >> 3 >= buf.length) buf = java.util.Arrays.copyOf(buf, buf.length * 2);
                buf[len >> 3] |= ((value >> i) & 1) << (7 - (len & 7));
                len++;
            }
        }
        int length() { return len; }
        byte[] toBytes() { return java.util.Arrays.copyOf(buf, (len + 7) / 8); }
    }
}
