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

/**
 * Clean-room VP8 (WebP lossy) keyframe decoder implemented from the published
 * bitstream specification, RFC 6386 (boolean entropy decode, macroblock intra
 * prediction, DCT/WHT inversion, loop filter, YUV→RGB). Interframes and motion
 * compensation are out of scope — WebP still images are always a single keyframe.
 *
 * <p>This follows the algorithms and constant tables of the specification (see
 * {@link Vp8Tables} / {@link Vp8ProbData}); it is a straightforward, readable
 * implementation rather than a performance-tuned one.</p>
 */
final class Vp8Decoder {

    private static final int DC_PRED = 0, V_PRED = 1, H_PRED = 2, TM_PRED = 3, B_PRED = 4;
    private static final int B_DC = 0, B_TM = 1, B_VE = 2, B_HE = 3, B_LD = 4,
                             B_RD = 5, B_VR = 6, B_VL = 7, B_HD = 8, B_HU = 9;

    private final byte[] data;
    private final int frameStart;
    private final int frameEnd;

    private int width, height;
    private int mbCols, mbRows;

    private int[] yPlane, uPlane, vPlane;
    private int yStride, uvStride;

    private final int[][][][] coeffProbs = deepCopy(Vp8Tables.DEFAULT_COEFF_PROBS);
    private int[] mbYMode, mbUVMode, mbSegment;
    private boolean[] mbSkip, mbHasCoeffs;
    private int[][] mbSubModes;

    private boolean segmentationEnabled, updateMbSegMap, segAbsDelta;
    private final int[] segQuant = new int[4];
    private final int[] segLfLevel = new int[4];
    private final int[] segTreeProbs = { 255, 255, 255 };
    private int baseQ, y1dc, y2dc, y2ac, uvdc, uvac;

    private boolean filterSimple;
    private int filterLevel, sharpness;

    private boolean skipEnabled;
    private int probSkipFalse;

    private Vp8BoolDecoder[] tokenParts;

    Vp8Decoder(byte[] data, int off, int end) {
        this.data = data;
        this.frameStart = off;
        this.frameEnd = end;
    }

    int width()  { return width; }
    int height() { return height; }

    /** Decodes the keyframe and returns packed ARGB pixels (alpha 0xFF). */
    int[] decodeRgb() {
        parseAndDecode();
        return toRgb();
    }

    /** ALPH chunk support is a follow-up; lossy alpha is not applied here. */
    void applyAlphaChunk(int[] argb, byte[] chunk, int off, int end) {
        // Intentionally a no-op for the first lossy release.
    }

    // ---- Top-level parse + decode -----------------------------------------

    private void parseAndDecode() {
        int p = frameStart;
        int tag = (data[p] & 0xFF) | ((data[p + 1] & 0xFF) << 8) | ((data[p + 2] & 0xFF) << 16);
        boolean keyFrame = (tag & 1) == 0;
        int firstPartSize = (tag >> 5) & 0x7FFFF;
        if (!keyFrame) throw new IllegalStateException("VP8: only keyframes are supported");
        p += 3;
        if ((data[p] & 0xFF) != 0x9d || (data[p + 1] & 0xFF) != 0x01 || (data[p + 2] & 0xFF) != 0x2a) {
            throw new IllegalStateException("VP8: bad start code");
        }
        int w = ((data[p + 3] & 0xFF) | ((data[p + 4] & 0xFF) << 8)) & 0x3FFF;
        int h = ((data[p + 5] & 0xFF) | ((data[p + 6] & 0xFF) << 8)) & 0x3FFF;
        p += 7;
        this.width = w;
        this.height = h;
        this.mbCols = (w + 15) / 16;
        this.mbRows = (h + 15) / 16;

        int firstPartStart = p;
        Vp8BoolDecoder bd = new Vp8BoolDecoder(data, firstPartStart, firstPartStart + firstPartSize);

        bd.readBit();                 // color space
        bd.readBit();                 // clamping type
        parseSegmentation(bd);
        parseLoopFilter(bd);
        parseTokenPartitions(bd, firstPartStart + firstPartSize);
        parseQuant(bd);
        bd.readBit();                 // refresh_entropy_probs (keyframe)
        parseCoeffProbUpdates(bd);
        skipEnabled = bd.readBit() != 0;
        probSkipFalse = skipEnabled ? bd.readLiteral(8) : 0;

        allocatePlanes();
        allocateMbState();

        decodeModeRows(bd);
        decodeAndReconstruct();
        if (filterLevel > 0) loopFilterFrame();
    }

    private void parseSegmentation(Vp8BoolDecoder bd) {
        segmentationEnabled = bd.readBit() != 0;
        if (!segmentationEnabled) { updateMbSegMap = false; return; }
        updateMbSegMap = bd.readBit() != 0;
        boolean updateData = bd.readBit() != 0;
        if (updateData) {
            segAbsDelta = bd.readBit() != 0;
            for (int i = 0; i < 4; i++) segQuant[i] = bd.readBool(128) != 0 ? bd.readSignedLiteral(7) : 0;
            for (int i = 0; i < 4; i++) segLfLevel[i] = bd.readBool(128) != 0 ? bd.readSignedLiteral(6) : 0;
        }
        if (updateMbSegMap) {
            for (int i = 0; i < 3; i++) segTreeProbs[i] = bd.readBit() != 0 ? bd.readLiteral(8) : 255;
        }
    }

    private void parseLoopFilter(Vp8BoolDecoder bd) {
        filterSimple = bd.readBit() != 0;
        filterLevel = bd.readLiteral(6);
        sharpness = bd.readLiteral(3);
        boolean lfDeltaEnabled = bd.readBit() != 0;
        if (lfDeltaEnabled && bd.readBit() != 0) {
            for (int i = 0; i < 4; i++) if (bd.readBool(128) != 0) bd.readSignedLiteral(6);
            for (int i = 0; i < 4; i++) if (bd.readBool(128) != 0) bd.readSignedLiteral(6);
        }
    }

    private void parseTokenPartitions(Vp8BoolDecoder bd, int afterFirstPart) {
        int count = 1 << bd.readLiteral(2);
        tokenParts = new Vp8BoolDecoder[count];
        int sizesStart = afterFirstPart;
        int dataStart = sizesStart + 3 * (count - 1);
        int cursor = dataStart;
        for (int i = 0; i < count; i++) {
            int size;
            if (i < count - 1) {
                int s = sizesStart + 3 * i;
                size = (data[s] & 0xFF) | ((data[s + 1] & 0xFF) << 8) | ((data[s + 2] & 0xFF) << 16);
            } else {
                size = frameEnd - cursor;
            }
            tokenParts[i] = new Vp8BoolDecoder(data, cursor, cursor + size);
            cursor += size;
        }
    }

    private void parseQuant(Vp8BoolDecoder bd) {
        baseQ = bd.readLiteral(7);
        y1dc = optDelta(bd);
        y2dc = optDelta(bd);
        y2ac = optDelta(bd);
        uvdc = optDelta(bd);
        uvac = optDelta(bd);
    }

    private static int optDelta(Vp8BoolDecoder bd) {
        return bd.readBool(128) != 0 ? bd.readSignedLiteral(4) : 0;
    }

    private void parseCoeffProbUpdates(Vp8BoolDecoder bd) {
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 8; j++)
                for (int k = 0; k < 3; k++)
                    for (int t = 0; t < 11; t++)
                        if (bd.readBool(Vp8Tables.COEFF_UPDATE_PROBS[i][j][k][t]) != 0)
                            coeffProbs[i][j][k][t] = bd.readLiteral(8);
    }

    // ---- Mode (prediction) decode -----------------------------------------

    private void decodeModeRows(Vp8BoolDecoder bd) {
        int[] aboveMode = new int[mbCols * 4];
        for (int mbRow = 0; mbRow < mbRows; mbRow++) {
            int[] leftMode = new int[4];
            for (int mbCol = 0; mbCol < mbCols; mbCol++) {
                int mb = mbRow * mbCols + mbCol;
                if (updateMbSegMap) mbSegment[mb] = readSegmentId(bd);
                mbSkip[mb] = skipEnabled && bd.readBool(probSkipFalse) != 0;

                int yMode = bd.readTree(Vp8Tables.KF_YMODE_TREE, Vp8Tables.KF_YMODE_PROB);
                mbYMode[mb] = yMode;
                if (yMode == B_PRED) {
                    int[] modes = mbSubModes[mb];
                    for (int i = 0; i < 16; i++) {
                        int r = i >> 2, c = i & 3;
                        int a = (r == 0) ? aboveMode[mbCol * 4 + c] : modes[i - 4];
                        int l = (c == 0) ? leftMode[r] : modes[i - 1];
                        modes[i] = bd.readTree(Vp8Tables.B_MODE_TREE, Vp8Tables.KF_BMODE_PROB[a][l]);
                    }
                    for (int c = 0; c < 4; c++) aboveMode[mbCol * 4 + c] = modes[12 + c];
                    for (int r = 0; r < 4; r++) leftMode[r] = modes[r * 4 + 3];
                } else {
                    int sub = ymodeToBmode(yMode);
                    java.util.Arrays.fill(mbSubModes[mb], sub);
                    for (int c = 0; c < 4; c++) aboveMode[mbCol * 4 + c] = sub;
                    for (int r = 0; r < 4; r++) leftMode[r] = sub;
                }
                mbUVMode[mb] = bd.readTree(Vp8Tables.UV_MODE_TREE, Vp8Tables.KF_UV_MODE_PROB);
            }
        }
    }

    private int readSegmentId(Vp8BoolDecoder bd) {
        return bd.readBool(segTreeProbs[0]) != 0
                ? 2 + bd.readBool(segTreeProbs[2])
                : bd.readBool(segTreeProbs[1]);
    }

    private static int ymodeToBmode(int yMode) {
        return switch (yMode) {
            case V_PRED -> B_VE;
            case H_PRED -> B_HE;
            case TM_PRED -> B_TM;
            default -> B_DC;
        };
    }

    // ---- Residual token decode + reconstruction ---------------------------

    private void decodeAndReconstruct() {
        int[][] aboveNz = new int[mbCols][9];
        short[] coeffs = new short[25 * 16];
        for (int mbRow = 0; mbRow < mbRows; mbRow++) {
            int[] leftNz = new int[9];
            Vp8BoolDecoder tok = tokenParts[mbRow % tokenParts.length];
            for (int mbCol = 0; mbCol < mbCols; mbCol++) {
                int mb = mbRow * mbCols + mbCol;
                java.util.Arrays.fill(coeffs, (short) 0);
                boolean hasCoeffs;
                if (mbSkip[mb]) {
                    hasCoeffs = false;
                    resetMbContext(leftNz, aboveNz[mbCol], mbYMode[mb]);
                } else {
                    hasCoeffs = decodeMbTokens(tok, mb, coeffs, leftNz, aboveNz[mbCol]);
                }
                mbHasCoeffs[mb] = hasCoeffs;
                reconstructMb(mbRow, mbCol, mb, coeffs);
            }
        }
    }

    private void resetMbContext(int[] left, int[] above, int yMode) {
        for (int i = 0; i < 8; i++) { left[i] = 0; above[i] = 0; }
        if (yMode != B_PRED) { left[8] = 0; above[8] = 0; }
    }

    private int[] dequantFactors(int mb) {
        int q = baseQ;
        if (segmentationEnabled) {
            q = segAbsDelta ? segQuant[mbSegment[mb]] : q + segQuant[mbSegment[mb]];
        }
        int y1DC = Vp8Tables.DC_QUANT[clampQ(q + y1dc)];
        int y1AC = Vp8Tables.AC_QUANT[clampQ(q)];
        int uvDC = Math.min(Vp8Tables.DC_QUANT[clampQ(q + uvdc)], 132);
        int uvAC = Vp8Tables.AC_QUANT[clampQ(q + uvac)];
        int y2DC = Vp8Tables.DC_QUANT[clampQ(q + y2dc)] * 2;
        int y2AC = Math.max(8, Vp8Tables.AC_QUANT[clampQ(q + y2ac)] * 155 / 100);
        return new int[] { y1DC, y1AC, uvDC, uvAC, y2DC, y2AC };
    }

    private static int clampQ(int q) { return q < 0 ? 0 : Math.min(q, 127); }

    private boolean decodeMbTokens(Vp8BoolDecoder tok, int mb, short[] coeffs, int[] left, int[] above) {
        int[] dqf = dequantFactors(mb);
        boolean has = false;
        boolean hasY2 = mbYMode[mb] != B_PRED;

        if (hasY2) {
            int nz = decodeBlock(tok, coeffs, 24 * 16, 1, 0, left[8] + above[8], dqf[4], dqf[5]);
            left[8] = above[8] = (nz > 0) ? 1 : 0;
            if (nz > 0) has = true;
        }

        int firstCoeff = hasY2 ? 1 : 0;
        int plane = hasY2 ? 0 : 3;
        for (int b = 0; b < 16; b++) {
            int lc = b & 3, ac = b >> 2;
            int ctx = left[ac] + above[lc];
            int nz = decodeBlock(tok, coeffs, b * 16, plane, firstCoeff, ctx, dqf[0], dqf[1]);
            left[ac] = above[lc] = (nz > firstCoeff) ? 1 : 0;
            if (nz > firstCoeff) has = true;
        }
        for (int b = 0; b < 4; b++) {
            int lc = 4 + (b & 1), ac = 4 + (b >> 1);
            int ctx = left[ac] + above[lc];
            int nz = decodeBlock(tok, coeffs, (16 + b) * 16, 2, 0, ctx, dqf[2], dqf[3]);
            left[ac] = above[lc] = (nz > 0) ? 1 : 0;
            if (nz > 0) has = true;
        }
        for (int b = 0; b < 4; b++) {
            int lc = 6 + (b & 1), ac = 6 + (b >> 1);
            int ctx = left[ac] + above[lc];
            int nz = decodeBlock(tok, coeffs, (20 + b) * 16, 2, 0, ctx, dqf[2], dqf[3]);
            left[ac] = above[lc] = (nz > 0) ? 1 : 0;
            if (nz > 0) has = true;
        }
        return has;
    }

    private int decodeBlock(Vp8BoolDecoder tok, short[] coeffs, int base, int plane,
                            int firstCoeff, int ctx, int dcq, int acq) {
        int c = firstCoeff;
        boolean prevZero = false;
        while (c < 16) {
            int band = Vp8Tables.COEFF_BANDS[c];
            int[] probs = coeffProbs[plane][band][ctx];
            int token;
            if (prevZero) {
                token = tok.readTree(Vp8Tables.COEFF_TREE, probs, 2);
            } else {
                token = tok.readTree(Vp8Tables.COEFF_TREE, probs);
                if (token == 11) break;
            }
            int value;
            if (token == 0) {
                prevZero = true;
                ctx = 0;
                c++;
                continue;
            } else if (token < 5) {
                value = token;
            } else {
                int cat = token - 5;
                int extra = 0;
                for (int b : Vp8Tables.CAT_PROBS[cat]) extra = (extra << 1) | tok.readBool(b);
                value = Vp8Tables.CAT_BASE[cat] + extra;
            }
            prevZero = false;
            ctx = (value == 1) ? 1 : 2;
            if (tok.readBool(128) != 0) value = -value;
            int q = (c == 0) ? dcq : acq;
            coeffs[base + Vp8Tables.ZIGZAG[c]] = (short) (value * q);
            c++;
        }
        return c;
    }

    // ---- Inverse transforms + prediction + summation ----------------------

    private void reconstructMb(int mbRow, int mbCol, int mb, short[] coeffs) {
        int yOrigin = originY(mbRow, mbCol);
        int uOrigin = originUV(mbRow, mbCol);

        if (mbYMode[mb] != B_PRED) {
            short[] y2 = new short[16];
            inverseWht(coeffs, 24 * 16, y2);
            for (int i = 0; i < 16; i++) coeffs[i * 16] = y2[i];
            predictLuma16(yOrigin, mbRow, mbCol, mbYMode[mb]);
            for (int b = 0; b < 16; b++) {
                int sub = (b >> 2) * 4 * yStride + (b & 3) * 4;
                idctAdd(yPlane, yOrigin + sub, yStride, coeffs, b * 16);
            }
        } else {
            // VP8 "above-right" quirk: the four above-right samples for the rightmost
            // subblock column (and every subblock row below the top) come from the row
            // above the macroblock, not from not-yet-decoded pixels to the right.
            int[] topRight = new int[4];
            if (mbRow == 0) {
                for (int k = 0; k < 4; k++) topRight[k] = 127;
            } else if (mbCol < mbCols - 1) {
                for (int k = 0; k < 4; k++) topRight[k] = yPlane[yOrigin - yStride + 16 + k];
            } else {
                int rep = yPlane[yOrigin - yStride + 15];
                for (int k = 0; k < 4; k++) topRight[k] = rep;
            }
            for (int b = 0; b < 16; b++) {
                int r = b >> 2, c = b & 3;
                int off = yOrigin + r * 4 * yStride + c * 4;
                predictSubblock4(off, mbRow, mbCol, r, c, mbSubModes[mb][b], topRight);
                idctAdd(yPlane, off, yStride, coeffs, b * 16);
            }
        }

        predictChroma8(uPlane, uOrigin, mbRow, mbCol, mbUVMode[mb]);
        predictChroma8(vPlane, uOrigin, mbRow, mbCol, mbUVMode[mb]);
        for (int b = 0; b < 4; b++) {
            int sub = (b >> 1) * 4 * uvStride + (b & 1) * 4;
            idctAdd(uPlane, uOrigin + sub, uvStride, coeffs, (16 + b) * 16);
            idctAdd(vPlane, uOrigin + sub, uvStride, coeffs, (20 + b) * 16);
        }
    }

    private static void inverseWht(short[] in, int base, short[] out) {
        int[] tmp = new int[16];
        for (int i = 0; i < 4; i++) {
            int a1 = in[base + i] + in[base + 12 + i];
            int b1 = in[base + 4 + i] + in[base + 8 + i];
            int c1 = in[base + 4 + i] - in[base + 8 + i];
            int d1 = in[base + i] - in[base + 12 + i];
            tmp[i] = a1 + b1;
            tmp[4 + i] = c1 + d1;
            tmp[8 + i] = a1 - b1;
            tmp[12 + i] = d1 - c1;
        }
        for (int i = 0; i < 4; i++) {
            int off = i * 4;
            int a1 = tmp[off] + tmp[off + 3];
            int b1 = tmp[off + 1] + tmp[off + 2];
            int c1 = tmp[off + 1] - tmp[off + 2];
            int d1 = tmp[off] - tmp[off + 3];
            out[off] = (short) ((a1 + b1 + 3) >> 3);
            out[off + 1] = (short) ((c1 + d1 + 3) >> 3);
            out[off + 2] = (short) ((a1 - b1 + 3) >> 3);
            out[off + 3] = (short) ((d1 - c1 + 3) >> 3);
        }
    }

    private static final int COS = 20091, SIN = 35468;

    private static void idctAdd(int[] buf, int origin, int stride, short[] coeffs, int base) {
        int[] tmp = new int[16];
        for (int i = 0; i < 4; i++) {
            int i0 = coeffs[base + i], i4 = coeffs[base + 4 + i],
                i8 = coeffs[base + 8 + i], i12 = coeffs[base + 12 + i];
            int a1 = i0 + i8;
            int b1 = i0 - i8;
            int t1 = (i4 * SIN) >> 16;
            int t2 = i12 + ((i12 * COS) >> 16);
            int c1 = t1 - t2;
            t1 = i4 + ((i4 * COS) >> 16);
            t2 = (i12 * SIN) >> 16;
            int d1 = t1 + t2;
            tmp[i] = a1 + d1;
            tmp[12 + i] = a1 - d1;
            tmp[4 + i] = b1 + c1;
            tmp[8 + i] = b1 - c1;
        }
        for (int i = 0; i < 4; i++) {
            int off = i * 4;
            int a1 = tmp[off] + tmp[off + 2];
            int b1 = tmp[off] - tmp[off + 2];
            int t1 = (tmp[off + 1] * SIN) >> 16;
            int t2 = tmp[off + 3] + ((tmp[off + 3] * COS) >> 16);
            int c1 = t1 - t2;
            t1 = tmp[off + 1] + ((tmp[off + 1] * COS) >> 16);
            t2 = (tmp[off + 3] * SIN) >> 16;
            int d1 = t1 + t2;
            int row = origin + i * stride;
            buf[row]     = clamp255(buf[row]     + ((a1 + d1 + 4) >> 3));
            buf[row + 3] = clamp255(buf[row + 3] + ((a1 - d1 + 4) >> 3));
            buf[row + 1] = clamp255(buf[row + 1] + ((b1 + c1 + 4) >> 3));
            buf[row + 2] = clamp255(buf[row + 2] + ((b1 - c1 + 4) >> 3));
        }
    }

    // ---- Intra prediction --------------------------------------------------

    private void predictLuma16(int origin, int mbRow, int mbCol, int mode) {
        predictNxN(yPlane, origin, yStride, 16, mode, mbRow != 0, mbCol != 0);
    }

    private void predictChroma8(int[] plane, int origin, int mbRow, int mbCol, int mode) {
        predictNxN(plane, origin, uvStride, 8, mode, mbRow != 0, mbCol != 0);
    }

    private static void predictNxN(int[] buf, int origin, int stride, int n, int mode,
                                   boolean hasAbove, boolean hasLeft) {
        switch (mode) {
            case V_PRED -> {
                for (int r = 0; r < n; r++)
                    for (int c = 0; c < n; c++)
                        buf[origin + r * stride + c] = hasAbove ? buf[origin - stride + c] : 127;
            }
            case H_PRED -> {
                for (int r = 0; r < n; r++) {
                    int left = hasLeft ? buf[origin + r * stride - 1] : 129;
                    for (int c = 0; c < n; c++) buf[origin + r * stride + c] = left;
                }
            }
            case TM_PRED -> {
                int p = !hasAbove ? 127 : (hasLeft ? buf[origin - stride - 1] : 129);
                for (int r = 0; r < n; r++) {
                    int left = hasLeft ? buf[origin + r * stride - 1] : 129;
                    for (int c = 0; c < n; c++) {
                        int above = hasAbove ? buf[origin - stride + c] : 127;
                        buf[origin + r * stride + c] = clamp255(left + above - p);
                    }
                }
            }
            default -> {
                int sum = 0, count = 0;
                if (hasAbove) { for (int c = 0; c < n; c++) sum += buf[origin - stride + c]; count += n; }
                if (hasLeft)  { for (int r = 0; r < n; r++) sum += buf[origin + r * stride - 1]; count += n; }
                int dc = (count == 0) ? 128 : (sum + (count >> 1)) / count;
                for (int r = 0; r < n; r++)
                    for (int c = 0; c < n; c++)
                        buf[origin + r * stride + c] = dc;
            }
        }
    }

    private void predictSubblock4(int origin, int mbRow, int mbCol, int subR, int subC, int mode,
                                 int[] topRight) {
        int s = yStride;
        boolean hasAbove = mbRow != 0 || subR != 0;
        boolean hasLeft = mbCol != 0 || subC != 0;
        int[] A = new int[9];
        for (int i = 0; i < 8; i++) A[i + 1] = hasAbove ? yPlane[origin - s + i] : 127;
        // Rightmost subblock column: above-right comes from the MB's saved top-right row.
        if (subC == 3) {
            A[5] = topRight[0]; A[6] = topRight[1]; A[7] = topRight[2]; A[8] = topRight[3];
        }
        A[0] = !hasAbove ? 127 : (hasLeft ? yPlane[origin - s - 1] : 129);
        int[] L = new int[4];
        for (int i = 0; i < 4; i++) L[i] = hasLeft ? yPlane[origin + i * s - 1] : 129;

        int[] E = { L[3], L[2], L[1], L[0], A[0], A[1], A[2], A[3], A[4] };
        int[][] B = new int[4][4];
        switch (mode) {
            case B_DC -> {
                int v = 4;
                for (int i = 0; i < 4; i++) v += A[i + 1] + L[i];
                v >>= 3;
                fill(B, v);
            }
            case B_TM -> {
                for (int r = 0; r < 4; r++)
                    for (int c = 0; c < 4; c++)
                        B[r][c] = clamp255(L[r] + A[c + 1] - A[0]);
            }
            case B_VE -> {
                for (int c = 0; c < 4; c++) {
                    int v = avg3(A[c], A[c + 1], A[c + 2]);
                    for (int r = 0; r < 4; r++) B[r][c] = v;
                }
            }
            case B_HE -> {
                int r0 = avg3(A[0], L[0], L[1]);
                int r1 = avg3(L[0], L[1], L[2]);
                int r2 = avg3(L[1], L[2], L[3]);
                int r3 = avg3(L[2], L[3], L[3]);
                for (int c = 0; c < 4; c++) { B[0][c] = r0; B[1][c] = r1; B[2][c] = r2; B[3][c] = r3; }
            }
            case B_LD -> {
                B[0][0] = avg3(A[1], A[2], A[3]);
                B[0][1] = B[1][0] = avg3(A[2], A[3], A[4]);
                B[0][2] = B[1][1] = B[2][0] = avg3(A[3], A[4], A[5]);
                B[0][3] = B[1][2] = B[2][1] = B[3][0] = avg3(A[4], A[5], A[6]);
                B[1][3] = B[2][2] = B[3][1] = avg3(A[5], A[6], A[7]);
                B[2][3] = B[3][2] = avg3(A[6], A[7], A[8]);
                B[3][3] = avg3(A[7], A[8], A[8]);
            }
            case B_RD -> {
                B[3][0] = avg3(E[0], E[1], E[2]);
                B[3][1] = B[2][0] = avg3(E[1], E[2], E[3]);
                B[3][2] = B[2][1] = B[1][0] = avg3(E[2], E[3], E[4]);
                B[3][3] = B[2][2] = B[1][1] = B[0][0] = avg3(E[3], E[4], E[5]);
                B[2][3] = B[1][2] = B[0][1] = avg3(E[4], E[5], E[6]);
                B[1][3] = B[0][2] = avg3(E[5], E[6], E[7]);
                B[0][3] = avg3(E[6], E[7], E[8]);
            }
            case B_VR -> {
                B[3][0] = avg3(E[1], E[2], E[3]);
                B[2][0] = avg3(E[2], E[3], E[4]);
                B[3][1] = B[1][0] = avg3(E[3], E[4], E[5]);
                B[2][1] = B[0][0] = avg2(E[4], E[5]);
                B[3][2] = B[1][1] = avg3(E[4], E[5], E[6]);
                B[2][2] = B[0][1] = avg2(E[5], E[6]);
                B[3][3] = B[1][2] = avg3(E[5], E[6], E[7]);
                B[2][3] = B[0][2] = avg2(E[6], E[7]);
                B[1][3] = avg3(E[6], E[7], E[8]);
                B[0][3] = avg2(E[7], E[8]);
            }
            case B_VL -> {
                B[0][0] = avg2(A[1], A[2]);
                B[1][0] = avg3(A[1], A[2], A[3]);
                B[2][0] = B[0][1] = avg2(A[2], A[3]);
                B[1][1] = B[3][0] = avg3(A[2], A[3], A[4]);
                B[2][1] = B[0][2] = avg2(A[3], A[4]);
                B[3][1] = B[1][2] = avg3(A[3], A[4], A[5]);
                B[2][2] = B[0][3] = avg2(A[4], A[5]);
                B[3][2] = B[1][3] = avg3(A[4], A[5], A[6]);
                B[2][3] = avg3(A[5], A[6], A[7]);
                B[3][3] = avg3(A[6], A[7], A[8]);
            }
            case B_HD -> {
                B[3][0] = avg2(E[0], E[1]);
                B[3][1] = avg3(E[0], E[1], E[2]);
                B[2][0] = B[3][2] = avg2(E[1], E[2]);
                B[2][1] = B[3][3] = avg3(E[1], E[2], E[3]);
                B[2][2] = B[1][0] = avg2(E[2], E[3]);
                B[2][3] = B[1][1] = avg3(E[2], E[3], E[4]);
                B[1][2] = B[0][0] = avg2(E[3], E[4]);
                B[1][3] = B[0][1] = avg3(E[3], E[4], E[5]);
                B[0][2] = avg3(E[4], E[5], E[6]);
                B[0][3] = avg3(E[5], E[6], E[7]);
            }
            case B_HU -> {
                B[0][0] = avg2(L[0], L[1]);
                B[0][1] = avg3(L[0], L[1], L[2]);
                B[0][2] = B[1][0] = avg2(L[1], L[2]);
                B[0][3] = B[1][1] = avg3(L[1], L[2], L[3]);
                B[1][2] = B[2][0] = avg2(L[2], L[3]);
                B[1][3] = B[2][1] = avg3(L[2], L[3], L[3]);
                B[2][2] = B[2][3] = B[3][0] = B[3][1] = B[3][2] = B[3][3] = L[3];
            }
            default -> fill(B, 128);
        }
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                yPlane[origin + r * s + c] = B[r][c];
    }

    private static void fill(int[][] b, int v) {
        for (int[] row : b) java.util.Arrays.fill(row, v);
    }

    private static int avg2(int a, int b) { return (a + b + 1) >> 1; }
    private static int avg3(int a, int b, int c) { return (a + 2 * b + c + 2) >> 2; }

    // ---- Loop filter (RFC 6386 §15) ---------------------------------------

    private void loopFilterFrame() {
        for (int mbRow = 0; mbRow < mbRows; mbRow++) {
            for (int mbCol = 0; mbCol < mbCols; mbCol++) {
                int mb = mbRow * mbCols + mbCol;
                int level = filterLevelForMb(mb);
                if (level == 0) continue;
                int interior = interiorLimit(level);
                int hevT = hevThreshold(level);
                int mbEdge = ((level + 2) * 2) + interior;
                int subEdge = (level * 2) + interior;
                boolean filterSub = mbHasCoeffs[mb] || mbYMode[mb] == B_PRED;

                int yO = originY(mbRow, mbCol), uO = originUV(mbRow, mbCol);
                if (filterSimple) {
                    if (mbCol != 0) simpleV(yPlane, yO, yStride, 16, mbEdge);
                    if (filterSub) for (int c = 4; c < 16; c += 4) simpleV(yPlane, yO + c, yStride, 16, subEdge);
                    if (mbRow != 0) simpleH(yPlane, yO, yStride, 16, mbEdge);
                    if (filterSub) for (int r = 4; r < 16; r += 4) simpleH(yPlane, yO + r * yStride, yStride, 16, subEdge);
                } else {
                    if (mbCol != 0) { mbV(yPlane, yO, yStride, 16, mbEdge, interior, hevT);
                                      mbV(uPlane, uO, uvStride, 8, mbEdge, interior, hevT);
                                      mbV(vPlane, uO, uvStride, 8, mbEdge, interior, hevT); }
                    if (filterSub) {
                        for (int c = 4; c < 16; c += 4) subV(yPlane, yO + c, yStride, 16, subEdge, interior, hevT);
                        subV(uPlane, uO + 4, uvStride, 8, subEdge, interior, hevT);
                        subV(vPlane, uO + 4, uvStride, 8, subEdge, interior, hevT);
                    }
                    if (mbRow != 0) { mbH(yPlane, yO, yStride, 16, mbEdge, interior, hevT);
                                      mbH(uPlane, uO, uvStride, 8, mbEdge, interior, hevT);
                                      mbH(vPlane, uO, uvStride, 8, mbEdge, interior, hevT); }
                    if (filterSub) {
                        for (int r = 4; r < 16; r += 4) subH(yPlane, yO + r * yStride, yStride, 16, subEdge, interior, hevT);
                        subH(uPlane, uO + 4 * uvStride, uvStride, 8, subEdge, interior, hevT);
                        subH(vPlane, uO + 4 * uvStride, uvStride, 8, subEdge, interior, hevT);
                    }
                }
            }
        }
    }

    private int filterLevelForMb(int mb) {
        int level = filterLevel;
        if (segmentationEnabled) {
            level = segAbsDelta ? segLfLevel[mbSegment[mb]] : level + segLfLevel[mbSegment[mb]];
        }
        return Math.max(0, Math.min(63, level));
    }

    private int interiorLimit(int level) {
        int limit = level;
        if (sharpness > 0) {
            limit >>= (sharpness > 4) ? 2 : 1;
            if (limit > 9 - sharpness) limit = 9 - sharpness;
        }
        return Math.max(1, limit);
    }

    private static int hevThreshold(int level) {
        if (level >= 40) return 2;
        if (level >= 15) return 1;
        return 0;
    }

    private static int u2s(int v) { return v - 128; }
    private static int s8(int v) { return v < -128 ? -128 : Math.min(v, 127); }
    private static int s2u(int v) { return s8(v) + 128; }

    private static int commonAdjust(int[] buf, int i, int step, boolean outer) {
        int p1 = u2s(buf[i - 2 * step]), p0 = u2s(buf[i - step]);
        int q0 = u2s(buf[i]), q1 = u2s(buf[i + step]);
        int a = s8((outer ? s8(p1 - q1) : 0) + 3 * (q0 - p0));
        int b = s8(a + 3) >> 3;
        a = s8(a + 4) >> 3;
        buf[i] = s2u(q0 - a);
        buf[i - step] = s2u(p0 + b);
        return a;
    }

    private static boolean simpleThresh(int[] buf, int i, int step, int limit) {
        return Math.abs(buf[i - step] - buf[i]) * 2 + (Math.abs(buf[i - 2 * step] - buf[i + step]) >> 1) <= limit;
    }

    private static boolean normalThresh(int[] buf, int i, int step, int edge, int interior) {
        int p3 = buf[i - 4 * step], p2 = buf[i - 3 * step], p1 = buf[i - 2 * step], p0 = buf[i - step];
        int q0 = buf[i], q1 = buf[i + step], q2 = buf[i + 2 * step], q3 = buf[i + 3 * step];
        return simpleThresh(buf, i, step, 2 * edge + interior)
                && Math.abs(p3 - p2) <= interior && Math.abs(p2 - p1) <= interior && Math.abs(p1 - p0) <= interior
                && Math.abs(q3 - q2) <= interior && Math.abs(q2 - q1) <= interior && Math.abs(q1 - q0) <= interior;
    }

    private static boolean hev(int[] buf, int i, int step, int thresh) {
        return Math.abs(buf[i - 2 * step] - buf[i - step]) > thresh
                || Math.abs(buf[i + step] - buf[i]) > thresh;
    }

    private static void simpleV(int[] buf, int origin, int stride, int len, int limit) {
        for (int r = 0; r < len; r++) { int i = origin + r * stride; if (simpleThresh(buf, i, 1, limit)) commonAdjust(buf, i, 1, true); }
    }
    private static void simpleH(int[] buf, int origin, int stride, int len, int limit) {
        for (int c = 0; c < len; c++) { int i = origin + c; if (simpleThresh(buf, i, stride, limit)) commonAdjust(buf, i, stride, true); }
    }
    private static void subV(int[] buf, int origin, int stride, int len, int edge, int interior, int hevT) {
        for (int r = 0; r < len; r++) subblockFilter(buf, origin + r * stride, 1, edge, interior, hevT);
    }
    private static void subH(int[] buf, int origin, int stride, int len, int edge, int interior, int hevT) {
        for (int c = 0; c < len; c++) subblockFilter(buf, origin + c, stride, edge, interior, hevT);
    }
    private static void mbV(int[] buf, int origin, int stride, int len, int edge, int interior, int hevT) {
        for (int r = 0; r < len; r++) mbFilter(buf, origin + r * stride, 1, edge, interior, hevT);
    }
    private static void mbH(int[] buf, int origin, int stride, int len, int edge, int interior, int hevT) {
        for (int c = 0; c < len; c++) mbFilter(buf, origin + c, stride, edge, interior, hevT);
    }

    private static void subblockFilter(int[] buf, int i, int step, int edge, int interior, int hevT) {
        if (!normalThresh(buf, i, step, edge, interior)) return;
        boolean highVar = hev(buf, i, step, hevT);
        int a = (commonAdjust(buf, i, step, highVar) + 1) >> 1;
        if (!highVar) {
            buf[i + step] = s2u(u2s(buf[i + step]) - a);
            buf[i - 2 * step] = s2u(u2s(buf[i - 2 * step]) + a);
        }
    }

    private static void mbFilter(int[] buf, int i, int step, int edge, int interior, int hevT) {
        if (!normalThresh(buf, i, step, edge, interior)) return;
        if (hev(buf, i, step, hevT)) { commonAdjust(buf, i, step, true); return; }
        int p2 = u2s(buf[i - 3 * step]), p1 = u2s(buf[i - 2 * step]), p0 = u2s(buf[i - step]);
        int q0 = u2s(buf[i]), q1 = u2s(buf[i + step]), q2 = u2s(buf[i + 2 * step]);
        int w = s8(s8(p1 - q1) + 3 * (q0 - p0));
        int a = (27 * w + 63) >> 7;
        buf[i] = s2u(q0 - a); buf[i - step] = s2u(p0 + a);
        a = (18 * w + 63) >> 7;
        buf[i + step] = s2u(q1 - a); buf[i - 2 * step] = s2u(p1 + a);
        a = (9 * w + 63) >> 7;
        buf[i + 2 * step] = s2u(q2 - a); buf[i - 3 * step] = s2u(p2 + a);
    }

    // ---- Buffers, geometry, colour conversion -----------------------------

    private void allocatePlanes() {
        yStride = mbCols * 16 + 1 + 8;
        uvStride = mbCols * 8 + 1 + 8;
        yPlane = new int[yStride * (mbRows * 16 + 1)];
        uPlane = new int[uvStride * (mbRows * 8 + 1)];
        vPlane = new int[uvStride * (mbRows * 8 + 1)];
    }

    private void allocateMbState() {
        int n = mbCols * mbRows;
        mbYMode = new int[n];
        mbUVMode = new int[n];
        mbSegment = new int[n];
        mbSkip = new boolean[n];
        mbHasCoeffs = new boolean[n];
        mbSubModes = new int[n][16];
    }

    private int originY(int mbRow, int mbCol)  { return (mbRow * 16 + 1) * yStride + (mbCol * 16 + 1); }
    private int originUV(int mbRow, int mbCol) { return (mbRow * 8 + 1) * uvStride + (mbCol * 8 + 1); }

    private int[] toRgb() {
        int[] out = new int[width * height];
        for (int y = 0; y < height; y++) {
            int yr = (y + 1) * yStride + 1;
            int cr = (y / 2 + 1) * uvStride + 1;
            for (int x = 0; x < width; x++) {
                int yy = yPlane[yr + x];
                int uu = uPlane[cr + x / 2] - 128;
                int vv = vPlane[cr + x / 2] - 128;
                // Studio-range BT.601 (matches libwebp / browsers), fixed point at 2^16.
                int yc = 76284 * (yy - 16);
                int r = clamp255((yc + 104595 * vv + 32768) >> 16);
                int g = clamp255((yc - 25625 * uu - 53281 * vv + 32768) >> 16);
                int b = clamp255((yc + 132251 * uu + 32768) >> 16);
                out[y * width + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return out;
    }

    private static int clamp255(int v) { return v < 0 ? 0 : Math.min(v, 255); }

    private static int[][][][] deepCopy(int[][][][] src) {
        int[][][][] d = new int[src.length][][][];
        for (int i = 0; i < src.length; i++) {
            d[i] = new int[src[i].length][][];
            for (int j = 0; j < src[i].length; j++) {
                d[i][j] = new int[src[i][j].length][];
                for (int k = 0; k < src[i][j].length; k++) d[i][j][k] = src[i][j][k].clone();
            }
        }
        return d;
    }
}
