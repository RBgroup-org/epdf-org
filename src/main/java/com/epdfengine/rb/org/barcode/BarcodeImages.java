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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Rasterizes a {@link BarcodeMatrix} to a PNG (black modules on white, including
 * the mandatory quiet zone). 2D symbologies produce a square image; 1D
 * symbologies are drawn at the requested bar height. Uses only {@code javax.imageio}
 * (java.desktop) — the same JDK imaging already used by the image pipeline.
 */
public final class BarcodeImages {

    private BarcodeImages() {}

    /** 2D convenience: square PNG at {@code scale} pixels per module. */
    public static byte[] toPng(BarcodeMatrix m, int scale) {
        return toPng(m, scale, m.width() * scale);
    }

    /** Rasterizes to PNG; {@code oneDBarHeightPx} sets the bar height for 1D symbologies. */
    public static byte[] toPng(BarcodeMatrix m, int scale, int oneDBarHeightPx) {
        if (scale < 1) scale = 1;
        int q = m.quietZone();
        int cols = m.width() + 2 * q;
        int wPx = cols * scale;
        int hPx = m.isOneDimensional() ? Math.max(scale, oneDBarHeightPx) : (m.height() + 2 * q) * scale;

        BufferedImage img = new BufferedImage(wPx, hPx, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < hPx; y++) {
            for (int x = 0; x < wPx; x++) {
                int col = x / scale - q;
                int row = m.isOneDimensional() ? 0 : y / scale - q;
                img.setRGB(x, y, m.dark(col, row) ? 0x000000 : 0xFFFFFF);
            }
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("barcode PNG encode failed", e);
        }
    }
}
