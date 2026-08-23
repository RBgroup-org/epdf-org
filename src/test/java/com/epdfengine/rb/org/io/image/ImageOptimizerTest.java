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

import com.epdfengine.rb.org.testkit.Assert;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

/** Verifies JPEG recompression and RGB downsampling. */
public final class ImageOptimizerTest {

    public static void main(String[] args) throws Exception {
        int w = 400, h = 400;
        byte[] rgb = new byte[w * h * 3];
        int i = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {   // smooth gradient → compresses well
                rgb[i++] = (byte) (x * 255 / w);
                rgb[i++] = (byte) (y * 255 / h);
                rgb[i++] = (byte) 128;
            }
        }

        byte[] jpeg = ImageOptimizer.recompressToJpeg(rgb, w, h, 0.7f);
        Assert.isTrue(jpeg.length < rgb.length, "JPEG smaller than raw RGB (" + jpeg.length + " < " + rgb.length + ")");
        BufferedImage dec = ImageIO.read(new ByteArrayInputStream(jpeg));
        Assert.isTrue(dec != null && dec.getWidth() == w && dec.getHeight() == h, "JPEG decodes to same dimensions");

        byte[] small = ImageOptimizer.downsampleRgb(rgb, w, h, 100, 100);
        Assert.equals(100L * 100 * 3, small.length, "downsampled RGB byte count");

        Assert.equals(4L, ImageOptimizer.downscaleFactorFor(4000, 3000, 1000), "downscale factor for oversized image");
        Assert.equals(1L, ImageOptimizer.downscaleFactorFor(500, 400, 1000), "no downscale when within budget");
        System.out.println("PASS — image optimizer (JPEG recompress + downsample)");
    }
}
