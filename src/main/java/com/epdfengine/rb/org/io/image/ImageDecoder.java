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

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Decodes image bytes into a {@link DecodedImage}. JPEG is passed through
 * (embedded directly via DCTDecode — dimensions read from the SOF marker, no
 * pixel decode). Other formats (PNG, GIF, BMP, …) are decoded to RGB(A) samples
 * with the JDK's {@code javax.imageio} (no third-party libraries).
 *
 * <p>Note: non-JPEG decoding uses {@code java.desktop} (ImageIO). JPEG needs no
 * decoding and no AWT. Hand-written PNG decoding can replace ImageIO later for
 * fully headless/native builds.</p>
 */
public final class ImageDecoder {

    private ImageDecoder() {}

    public static DecodedImage decode(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            throw new IllegalArgumentException("Empty or too-small image");
        }
        if (isJpeg(bytes)) {
            int[] info = jpegInfo(bytes);            // {width, height, components}
            return DecodedImage.jpeg(bytes, info[0], info[1], info[2]);
        }
        if (WebpDecoder.isWebp(bytes)) {
            return WebpDecoder.decode(bytes);
        }
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to decode image", e);
        }
        if (img == null) throw new IllegalArgumentException("Unsupported image format");

        int w = img.getWidth();
        int h = img.getHeight();
        boolean hasAlpha = img.getColorModel().hasAlpha();
        byte[] rgb = new byte[w * h * 3];
        byte[] alpha = hasAlpha ? new byte[w * h] : null;
        int ri = 0, ai = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                rgb[ri++] = (byte) (argb >> 16);
                rgb[ri++] = (byte) (argb >> 8);
                rgb[ri++] = (byte) argb;
                if (alpha != null) alpha[ai++] = (byte) (argb >>> 24);
            }
        }
        return alpha != null ? DecodedImage.rgba(rgb, alpha, w, h) : DecodedImage.rgb(rgb, w, h);
    }

    private static boolean isJpeg(byte[] b) {
        return (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
    }

    /** Reads width/height/components from the JPEG Start-Of-Frame marker. */
    private static int[] jpegInfo(byte[] b) {
        int i = 2;
        while (i + 9 < b.length) {
            if ((b[i] & 0xFF) != 0xFF) { i++; continue; }
            int marker = b[i + 1] & 0xFF;
            // Standalone markers (no length): SOI/EOI and RSTn.
            if (marker == 0xD8 || marker == 0xD9 || (marker >= 0xD0 && marker <= 0xD7)) { i += 2; continue; }
            int len = ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
            boolean sof = marker >= 0xC0 && marker <= 0xCF
                    && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
            if (sof) {
                int height = ((b[i + 5] & 0xFF) << 8) | (b[i + 6] & 0xFF);
                int width = ((b[i + 7] & 0xFF) << 8) | (b[i + 8] & 0xFF);
                int comps = b[i + 9] & 0xFF;
                return new int[]{width, height, comps};
            }
            i += 2 + len;
        }
        throw new IllegalArgumentException("Not a valid JPEG (no SOF marker)");
    }
}
