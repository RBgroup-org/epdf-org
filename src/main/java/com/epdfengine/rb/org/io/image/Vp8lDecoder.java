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
package com.epdfengine.rb.org.io.image;

import java.util.ArrayList;
import java.util.List;

/**
 * Clean-room VP8L (WebP lossless) decoder implemented from the official WebP
 * Lossless Bitstream Specification: LSB-first bit reader, canonical prefix
 * (Huffman) codes, spatially variant meta prefix codes, LZ77 backward references
 * with the 120-entry distance map, a hash-addressed colour cache, and the four
 * inverse transforms (predictor, colour, subtract-green, colour-indexing).
 *
 * <p>Output is an ARGB {@code int[]} in scan-line order (A in bits 31..24, R in
 * 23..16, G in 15..8, B in 7..0).</p>
 */
final class Vp8lDecoder {

    private static final int ARGB_BLACK = 0xff000000;
    private static final int MAX_ALLOWED_CODE_LENGTH = 15;

    // Transform types.
    private static final int PREDICTOR_TRANSFORM = 0;
    private static final int COLOR_TRANSFORM = 1;
    private static final int SUBTRACT_GREEN = 2;
    private static final int COLOR_INDEXING = 3;

    // Code-length code reading (Normal Code Length Code).
    private static final int[] CODE_LENGTH_ORDER = {
            17, 18, 0, 1, 2, 3, 4, 5, 16, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
    };
    private static final int[] CODE_LENGTH_EXTRA_BITS = { 2, 3, 7 };     // codes 16,17,18
    private static final int[] CODE_LENGTH_REPEAT_OFFSETS = { 3, 3, 11 };

    // LZ77 distance map: (xi, yi) pairs for distance codes 1..120 (Section 5.2.2).
    private static final int[] DISTANCE_MAP = {
            0, 1, 1, 0, 1, 1, -1, 1, 0, 2, 2, 0, 1, 2,
            -1, 2, 2, 1, -2, 1, 2, 2, -2, 2, 0, 3, 3, 0,
            1, 3, -1, 3, 3, 1, -3, 1, 2, 3, -2, 3, 3, 2,
            -3, 2, 0, 4, 4, 0, 1, 4, -1, 4, 4, 1, -4, 1,
            3, 3, -3, 3, 2, 4, -2, 4, 4, 2, -4, 2, 0, 5,
            3, 4, -3, 4, 4, 3, -4, 3, 5, 0, 1, 5, -1, 5,
            5, 1, -5, 1, 2, 5, -2, 5, 5, 2, -5, 2, 4, 4,
            -4, 4, 3, 5, -3, 5, 5, 3, -5, 3, 0, 6, 6, 0,
            1, 6, -1, 6, 6, 1, -6, 1, 2, 6, -2, 6, 6, 2,
            -6, 2, 4, 5, -4, 5, 5, 4, -5, 4, 3, 6, -3, 6,
            6, 3, -6, 3, 0, 7, 7, 0, 1, 7, -1, 7, 5, 5,
            -5, 5, 7, 1, -7, 1, 4, 6, -4, 6, 6, 4, -6, 4,
            2, 7, -2, 7, 7, 2, -7, 2, 3, 7, -3, 7, 7, 3,
            -7, 3, 5, 6, -5, 6, 6, 5, -6, 5, 8, 0, 4, 7,
            -4, 7, 7, 4, -7, 4, 8, 1, 8, 2, 6, 6, -6, 6,
            8, 3, 5, 7, -5, 7, 7, 5, -7, 5, 8, 4, 6, 7,
            -6, 7, 7, 6, -7, 6, 8, 5, 7, 7, -7, 7, 8, 6,
            8, 7
    };

    private final BitReader br;
    private int width;
    private int height;

    Vp8lDecoder(byte[] data, int off, int end) {
        this.br = new BitReader(data, off, end);
    }

    int width()  { return width; }
    int height() { return height; }

    int[] decode() {
        int signature = br.read(8);
        if (signature != 0x2f) throw new IllegalArgumentException("Bad VP8L signature");
        width = br.read(14) + 1;
        height = br.read(14) + 1;
        br.read(1);                 // alpha_is_used hint (ignored)
        int version = br.read(3);
        if (version != 0) throw new IllegalArgumentException("Unsupported VP8L version " + version);

        // Read transforms (applied later, in reverse order). Colour-indexing may
        // subsample the working width; track it for subsequent transforms/image.
        List<Transform> transforms = new ArrayList<>();
        int xsize = width;
        while (br.read(1) == 1) {
            Transform t = readTransform(xsize);
            transforms.add(t);
            if (t.type == COLOR_INDEXING) xsize = t.reducedWidth;
        }

        int[] argb = decodeImageData(xsize, height, true);

        // Inverse transforms, last read first. Colour-indexing expands width back.
        int curWidth = xsize;
        for (int i = transforms.size() - 1; i >= 0; i--) {
            Transform t = transforms.get(i);
            switch (t.type) {
                case PREDICTOR_TRANSFORM -> inversePredictor(argb, curWidth, height, t);
                case COLOR_TRANSFORM -> inverseColor(argb, curWidth, height, t);
                case SUBTRACT_GREEN -> inverseSubtractGreen(argb, curWidth * height);
                case COLOR_INDEXING -> {
                    argb = inverseColorIndexing(argb, curWidth, height, t);
                    curWidth = t.origWidth;
                }
                default -> { }
            }
        }
        return argb;
    }

    // --- transforms ---

    private static final class Transform {
        int type;
        int sizeBits;          // predictor / colour block size
        int blockWidth;        // transform_width (block columns)
        int[] data;            // sub-resolution image (ARGB per block)
        int origWidth;         // colour-indexing: width before subsampling
        int reducedWidth;      // colour-indexing: width after subsampling
        int widthBits;         // colour-indexing: bundling bits
        int[] colorTable;      // colour-indexing: palette (delta-decoded)
        int colorTableSize;
    }

    private Transform readTransform(int xsize) {
        Transform t = new Transform();
        t.type = br.read(2);
        switch (t.type) {
            case PREDICTOR_TRANSFORM, COLOR_TRANSFORM -> {
                t.sizeBits = br.read(3) + 2;
                int bw = divRoundUp(xsize, 1 << t.sizeBits);
                int bh = divRoundUp(height, 1 << t.sizeBits);
                t.blockWidth = bw;
                t.data = decodeImageData(bw, bh, false);
            }
            case SUBTRACT_GREEN -> { /* no data */ }
            case COLOR_INDEXING -> {
                int size = br.read(8) + 1;
                t.colorTableSize = size;
                int[] table = decodeImageData(size, 1, false);
                // Palette is subtraction-coded: add previous entry per channel.
                for (int i = 1; i < size; i++) table[i] = addPixels(table[i], table[i - 1]);
                t.colorTable = table;
                int widthBits = (size <= 2) ? 3 : (size <= 4) ? 2 : (size <= 16) ? 1 : 0;
                t.widthBits = widthBits;
                t.origWidth = xsize;
                t.reducedWidth = divRoundUp(xsize, 1 << widthBits);
            }
            default -> throw new IllegalArgumentException("Bad VP8L transform type");
        }
        return t;
    }

    private void inversePredictor(int[] argb, int w, int h, Transform t) {
        int bits = t.sizeBits;
        int tw = t.blockWidth;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int pred;
                if (x == 0 && y == 0) {
                    pred = ARGB_BLACK;
                } else if (y == 0) {
                    pred = argb[i - 1];                     // top row: use left
                } else if (x == 0) {
                    pred = argb[i - w];                     // left column: use top
                } else {
                    int mode = (t.data[(y >> bits) * tw + (x >> bits)] >> 8) & 0xff;
                    int L = argb[i - 1];
                    int T = argb[i - w];
                    int TL = argb[i - w - 1];
                    int TR = (x == w - 1) ? argb[y * w] : argb[i - w + 1];
                    pred = predict(mode, L, T, TL, TR);
                }
                argb[i] = addPixels(argb[i], pred);
            }
        }
    }

    private void inverseColor(int[] argb, int w, int h, Transform t) {
        int bits = t.sizeBits;
        int tw = t.blockWidth;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int cte = t.data[(y >> bits) * tw + (x >> bits)];
                int greenToRed = (cte) & 0xff;            // blue channel
                int greenToBlue = (cte >> 8) & 0xff;      // green channel
                int redToBlue = (cte >> 16) & 0xff;       // red channel
                int argbVal = argb[i];
                int green = (argbVal >> 8) & 0xff;
                int red = (argbVal >> 16) & 0xff;
                int blue = argbVal & 0xff;
                red = (red + colorTransformDelta(greenToRed, green)) & 0xff;
                blue = (blue + colorTransformDelta(greenToBlue, green)) & 0xff;
                blue = (blue + colorTransformDelta(redToBlue, red)) & 0xff;
                argb[i] = (argbVal & 0xff00ff00) | (red << 16) | blue;
            }
        }
    }

    private static void inverseSubtractGreen(int[] argb, int n) {
        for (int i = 0; i < n; i++) {
            int px = argb[i];
            int green = (px >> 8) & 0xff;
            int red = ((px >> 16) & 0xff) + green;
            int blue = (px & 0xff) + green;
            argb[i] = (px & 0xff00ff00) | ((red & 0xff) << 16) | (blue & 0xff);
        }
    }

    private int[] inverseColorIndexing(int[] argb, int w, int h, Transform t) {
        int origW = t.origWidth;
        int[] out = new int[origW * h];
        int wb = t.widthBits;
        int size = t.colorTableSize;
        int[] table = t.colorTable;
        if (wb == 0) {
            for (int i = 0; i < origW * h; i++) {
                int idx = (argb[i] >> 8) & 0xff;
                out[i] = (idx < size) ? table[idx] : 0;
            }
            return out;
        }
        int bitsPerIndex = 8 >> wb;                 // 4, 2 or 1
        int mask = (1 << bitsPerIndex) - 1;
        int perPixel = 1 << wb;                     // 2, 4 or 8 packed indices
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < origW; x++) {
                int srcX = x >> wb;
                int packed = (argb[y * w + srcX] >> 8) & 0xff;
                int shift = (x & (perPixel - 1)) * bitsPerIndex;
                int idx = (packed >> shift) & mask;
                out[y * origW + x] = (idx < size) ? table[idx] : 0;
            }
        }
        return out;
    }

    // --- predictors (Section 4.1) ---

    private static int predict(int mode, int L, int T, int TL, int TR) {
        return switch (mode) {
            case 0 -> ARGB_BLACK;
            case 1 -> L;
            case 2 -> T;
            case 3 -> TR;
            case 4 -> TL;
            case 5 -> average2(average2(L, TR), T);
            case 6 -> average2(L, TL);
            case 7 -> average2(L, T);
            case 8 -> average2(TL, T);
            case 9 -> average2(T, TR);
            case 10 -> average2(average2(L, TL), average2(T, TR));
            case 11 -> select(L, T, TL);
            case 12 -> clampAddSubtractFull(L, T, TL);
            case 13 -> clampAddSubtractHalf(average2(L, T), TL);
            default -> ARGB_BLACK;
        };
    }

    private static int average2(int a, int b) {
        return ((((a >>> 24) & 0xff) + ((b >>> 24) & 0xff)) >> 1) << 24
                | ((((a >>> 16) & 0xff) + ((b >>> 16) & 0xff)) >> 1) << 16
                | ((((a >>> 8) & 0xff) + ((b >>> 8) & 0xff)) >> 1) << 8
                | (((a & 0xff) + (b & 0xff)) >> 1);
    }

    private static int select(int L, int T, int TL) {
        int pa = ch(L, 24) + ch(T, 24) - ch(TL, 24);
        int pr = ch(L, 16) + ch(T, 16) - ch(TL, 16);
        int pg = ch(L, 8) + ch(T, 8) - ch(TL, 8);
        int pb = ch(L, 0) + ch(T, 0) - ch(TL, 0);
        int pL = Math.abs(pa - ch(L, 24)) + Math.abs(pr - ch(L, 16))
                + Math.abs(pg - ch(L, 8)) + Math.abs(pb - ch(L, 0));
        int pT = Math.abs(pa - ch(T, 24)) + Math.abs(pr - ch(T, 16))
                + Math.abs(pg - ch(T, 8)) + Math.abs(pb - ch(T, 0));
        return (pL < pT) ? L : T;
    }

    private static int clampAddSubtractFull(int a, int b, int c) {
        int al = clamp(ch(a, 24) + ch(b, 24) - ch(c, 24));
        int r = clamp(ch(a, 16) + ch(b, 16) - ch(c, 16));
        int g = clamp(ch(a, 8) + ch(b, 8) - ch(c, 8));
        int bl = clamp(ch(a, 0) + ch(b, 0) - ch(c, 0));
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }

    private static int clampAddSubtractHalf(int a, int b) {
        int al = clamp(ch(a, 24) + (ch(a, 24) - ch(b, 24)) / 2);
        int r = clamp(ch(a, 16) + (ch(a, 16) - ch(b, 16)) / 2);
        int g = clamp(ch(a, 8) + (ch(a, 8) - ch(b, 8)) / 2);
        int bl = clamp(ch(a, 0) + (ch(a, 0) - ch(b, 0)) / 2);
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }

    private static int ch(int argb, int shift) { return (argb >>> shift) & 0xff; }
    private static int clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    private static int colorTransformDelta(int t, int c) {
        return ((byte) t * (byte) c) >> 5;      // 3.5 fixed-point; only low 8 bits used by caller
    }

    private static int addPixels(int a, int b) {
        int aa = ((a >>> 24) + (b >>> 24)) & 0xff;
        int rr = (((a >> 16) & 0xff) + ((b >> 16) & 0xff)) & 0xff;
        int gg = (((a >> 8) & 0xff) + ((b >> 8) & 0xff)) & 0xff;
        int bb = ((a & 0xff) + (b & 0xff)) & 0xff;
        return (aa << 24) | (rr << 16) | (gg << 8) | bb;
    }

    private static int divRoundUp(int num, int den) { return (num + den - 1) / den; }

    // --- entropy-coded image data (Sections 5 & 6) ---

    private int[] decodeImageData(int xsize, int ysize, boolean topLevel) {
        int colorCacheBits = 0;
        if (br.read(1) == 1) {
            colorCacheBits = br.read(4);
            if (colorCacheBits < 1 || colorCacheBits > 11) {
                throw new IllegalArgumentException("Bad colour-cache bits " + colorCacheBits);
            }
        }
        int colorCacheSize = (colorCacheBits > 0) ? (1 << colorCacheBits) : 0;

        int[] entropyImage = null;
        int prefixBits = 0, entropyWidth = 0, numGroups = 1;
        if (topLevel && br.read(1) == 1) {
            prefixBits = br.read(3) + 2;
            entropyWidth = divRoundUp(xsize, 1 << prefixBits);
            int entropyHeight = divRoundUp(ysize, 1 << prefixBits);
            entropyImage = decodeImageData(entropyWidth, entropyHeight, false);
            int maxCode = 0;
            for (int v : entropyImage) {
                int meta = (v >> 8) & 0xffff;
                if (meta > maxCode) maxCode = meta;
            }
            numGroups = maxCode + 1;
        }

        // Prefix code groups: five prefix codes each (G+len+cache, R, B, A, distance).
        int greenAlphabet = 256 + 24 + colorCacheSize;
        HuffTree[][] groups = new HuffTree[numGroups][5];
        for (int g = 0; g < numGroups; g++) {
            groups[g][0] = readHuffTree(greenAlphabet);
            groups[g][1] = readHuffTree(256);
            groups[g][2] = readHuffTree(256);
            groups[g][3] = readHuffTree(256);
            groups[g][4] = readHuffTree(40);
        }

        int[] cache = (colorCacheSize > 0) ? new int[colorCacheSize] : null;
        int[] argb = new int[xsize * ysize];
        int total = xsize * ysize;
        int pos = 0, x = 0, y = 0;
        while (pos < total) {
            HuffTree[] group = groups[0];
            if (entropyImage != null) {
                int meta = (entropyImage[(y >> prefixBits) * entropyWidth + (x >> prefixBits)] >> 8) & 0xffff;
                group = groups[meta];
            }
            int s = readSymbol(group[0]);
            if (s < 256) {
                int green = s;
                int red = readSymbol(group[1]);
                int blue = readSymbol(group[2]);
                int alpha = readSymbol(group[3]);
                int px = (alpha << 24) | (red << 16) | (green << 8) | blue;
                argb[pos++] = px;
                if (cache != null) cache[cacheHash(px, colorCacheBits)] = px;
                if (++x == xsize) { x = 0; y++; }
            } else if (s < 256 + 24) {
                int length = copyValue(s - 256);
                int distCode = copyValue(readSymbol(group[4]));
                int dist = mapDistance(distCode, xsize);
                if (dist > pos) dist = pos;
                for (int k = 0; k < length && pos < total; k++) {
                    int px = argb[pos - dist];
                    argb[pos++] = px;
                    if (cache != null) cache[cacheHash(px, colorCacheBits)] = px;
                    if (++x == xsize) { x = 0; y++; }
                }
            } else {
                int px = cache[s - (256 + 24)];
                argb[pos++] = px;
                if (cache != null) cache[cacheHash(px, colorCacheBits)] = px;
                if (++x == xsize) { x = 0; y++; }
            }
        }
        return argb;
    }

    private static int cacheHash(int argb, int bits) {
        return (0x1e35a7bd * argb) >>> (32 - bits);
    }

    /** Length/distance prefix decode (Section 5.2.2). */
    private int copyValue(int prefixCode) {
        if (prefixCode < 4) return prefixCode + 1;
        int extraBits = (prefixCode - 2) >> 1;
        int offset = (2 + (prefixCode & 1)) << extraBits;
        return offset + br.read(extraBits) + 1;
    }

    private static int mapDistance(int distCode, int xsize) {
        if (distCode > 120) return distCode - 120;
        int i = (distCode - 1) * 2;
        int xi = DISTANCE_MAP[i];
        int yi = DISTANCE_MAP[i + 1];
        int dist = xi + yi * xsize;
        return dist < 1 ? 1 : dist;
    }

    // --- canonical prefix (Huffman) codes (Section 6) ---

    private HuffTree readHuffTree(int alphabetSize) {
        int[] codeLengths = new int[alphabetSize];
        if (br.read(1) == 1) {
            // Simple code length code: 1 or 2 symbols of length 1.
            int numSymbols = br.read(1) + 1;
            int isFirst8 = br.read(1);
            int symbol0 = br.read(1 + 7 * isFirst8);
            if (symbol0 < alphabetSize) codeLengths[symbol0] = 1;
            if (numSymbols == 2) {
                int symbol1 = br.read(8);
                if (symbol1 < alphabetSize) codeLengths[symbol1] = 1;
            }
        } else {
            // Normal code length code.
            int[] clcl = new int[19];
            int numCodeLengths = 4 + br.read(4);
            for (int i = 0; i < numCodeLengths; i++) {
                clcl[CODE_LENGTH_ORDER[i]] = br.read(3);
            }
            HuffTree clTree = new HuffTree(clcl);
            int maxSymbol;
            if (br.read(1) != 0) {
                int lengthNbits = 2 + 2 * br.read(3);
                maxSymbol = 2 + br.read(lengthNbits);
            } else {
                maxSymbol = alphabetSize;
            }
            int symbol = 0, prevLen = 8;
            while (symbol < alphabetSize && maxSymbol-- > 0) {
                int codeLen = readSymbol(clTree);
                if (codeLen < 16) {
                    codeLengths[symbol++] = codeLen;
                    if (codeLen != 0) prevLen = codeLen;
                } else {
                    int slot = codeLen - 16;               // 0,1,2 → codes 16,17,18
                    int repeat = br.read(CODE_LENGTH_EXTRA_BITS[slot]) + CODE_LENGTH_REPEAT_OFFSETS[slot];
                    int len = (codeLen == 16) ? prevLen : 0;
                    if (symbol + repeat > alphabetSize) throw new IllegalArgumentException("Bad code-length run");
                    while (repeat-- > 0) codeLengths[symbol++] = len;
                }
            }
        }
        return new HuffTree(codeLengths);
    }

    private int readSymbol(HuffTree tree) {
        if (tree.singleSymbol >= 0) return tree.singleSymbol;
        int code = 0, first = 0, index = 0;
        for (int len = 1; len <= MAX_ALLOWED_CODE_LENGTH; len++) {
            code |= br.read(1);
            int count = tree.count[len];
            if (code - first < count) return tree.symbols[index + (code - first)];
            index += count;
            first += count;
            first <<= 1;
            code <<= 1;
        }
        throw new IllegalArgumentException("Corrupt prefix code");
    }

    /** Canonical Huffman table built from per-symbol code lengths (zlib/puff style). */
    private static final class HuffTree {
        final int[] count = new int[MAX_ALLOWED_CODE_LENGTH + 1];
        final int[] symbols;
        int singleSymbol = -1;

        HuffTree(int[] codeLengths) {
            int used = 0, only = -1;
            for (int sym = 0; sym < codeLengths.length; sym++) {
                int l = codeLengths[sym];
                if (l > 0) { count[l]++; used++; only = sym; }
            }
            symbols = new int[Math.max(1, used)];
            if (used <= 1) { singleSymbol = (used == 1) ? only : 0; return; }
            int[] offsets = new int[MAX_ALLOWED_CODE_LENGTH + 2];
            for (int len = 1; len <= MAX_ALLOWED_CODE_LENGTH; len++) {
                offsets[len + 1] = offsets[len] + count[len];
            }
            for (int sym = 0; sym < codeLengths.length; sym++) {
                int l = codeLengths[sym];
                if (l > 0) symbols[offsets[l]++] = sym;
            }
        }
    }

    /** LSB-first bit reader over a byte range (WebP lossless bit order). */
    private static final class BitReader {
        private final byte[] d;
        private int bytePos;
        private final int end;
        private long acc;
        private int accBits;

        BitReader(byte[] d, int off, int end) {
            this.d = d;
            this.bytePos = off;
            this.end = end;
        }

        int read(int n) {
            if (n == 0) return 0;
            while (accBits < n) {
                long b = (bytePos < end) ? (d[bytePos] & 0xffL) : 0;
                bytePos++;
                acc |= b << accBits;
                accBits += 8;
            }
            int v = (int) (acc & ((1L << n) - 1));
            acc >>>= n;
            accBits -= n;
            return v;
        }
    }
}
