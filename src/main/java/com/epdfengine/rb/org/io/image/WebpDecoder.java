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
 * Clean-room WebP decoder producing {@link DecodedImage} RGB(A) samples. The
 * lossless codec (VP8L) is fully implemented from the official WebP Lossless
 * Bitstream Specification (RIFF container, prefix/Huffman coding, LZ77 backward
 * references, colour cache, and the four inverse transforms). The lossy codec
 * (VP8) is decoded by {@link Vp8Decoder}; extended containers ({@code VP8X}) and
 * a separate alpha chunk ({@code ALPH}) are handled here.
 *
 * <p>No third-party or copyleft code is used: the algorithm follows the public
 * specification (the same one libwebp/Chromium implement) implemented from
 * scratch.</p>
 */
public final class WebpDecoder {

    private WebpDecoder() {}

    /** True when the bytes begin with a RIFF/WEBP container. */
    public static boolean isWebp(byte[] b) {
        return b != null && b.length >= 16
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    public static DecodedImage decode(byte[] bytes) {
        if (!isWebp(bytes)) throw new IllegalArgumentException("Not a WebP (RIFF/WEBP) stream");
        // Walk RIFF chunks starting at offset 12.
        int p = 12;
        int vp8lOff = -1, vp8lLen = 0;
        int vp8Off = -1, vp8Len = 0;
        int alphOff = -1, alphLen = 0;
        while (p + 8 <= bytes.length) {
            String fourCc = new String(bytes, p, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int size = le32(bytes, p + 4);
            int payload = p + 8;
            if (size < 0 || payload + size > bytes.length) size = bytes.length - payload;
            switch (fourCc) {
                case "VP8L" -> { vp8lOff = payload; vp8lLen = size; }
                case "VP8 " -> { vp8Off = payload; vp8Len = size; }
                case "ALPH" -> { alphOff = payload; alphLen = size; }
                case "VP8X" -> { /* extended header: canvas flags; sub-chunks follow */ }
                default -> { }
            }
            p = payload + size + (size & 1);   // chunks are padded to even size
        }
        if (vp8lOff >= 0) {
            Vp8lDecoder dec = new Vp8lDecoder(bytes, vp8lOff, vp8lOff + vp8lLen);
            int[] argb = dec.decode();
            return toDecoded(argb, dec.width(), dec.height());
        }
        if (vp8Off >= 0) {
            Vp8Decoder vp8 = new Vp8Decoder(bytes, vp8Off, vp8Off + vp8Len);
            int[] argb = vp8.decodeRgb();
            if (alphOff >= 0) vp8.applyAlphaChunk(argb, bytes, alphOff, alphOff + alphLen);
            return toDecoded(argb, vp8.width(), vp8.height());
        }
        throw new IllegalArgumentException("WebP has no VP8/VP8L image chunk");
    }

    private static DecodedImage toDecoded(int[] argb, int w, int h) {
        byte[] rgb = new byte[w * h * 3];
        byte[] alpha = new byte[w * h];
        boolean opaque = true;
        for (int i = 0, ri = 0; i < w * h; i++) {
            int px = argb[i];
            int a = (px >>> 24) & 0xff;
            rgb[ri++] = (byte) ((px >>> 16) & 0xff);
            rgb[ri++] = (byte) ((px >>> 8) & 0xff);
            rgb[ri++] = (byte) (px & 0xff);
            alpha[i] = (byte) a;
            if (a != 0xff) opaque = false;
        }
        return opaque ? DecodedImage.rgb(rgb, w, h) : DecodedImage.rgba(rgb, alpha, w, h);
    }

    static int le32(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8)
                | ((b[off + 2] & 0xff) << 16) | ((b[off + 3] & 0xff) << 24);
    }
}
