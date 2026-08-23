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

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Shrinks embedded raster images: re-encodes 8-bit RGB as baseline JPEG (a large size win for
 * photographic content vs. FlateDecode), and downsamples oversized images to a target pixel
 * budget (a screenshot at 4000 px wide printed at 200 px wastes bytes). Uses only the JDK's
 * {@code javax.imageio} — no third-party image library.
 */
public final class ImageOptimizer {

    private ImageOptimizer() {}

    /** Re-encodes interleaved 8-bit RGB to baseline JPEG at {@code quality} (0..1). */
    public static byte[] recompressToJpeg(byte[] rgb, int width, int height, float quality) throws IOException {
        BufferedImage img = toBufferedImage(rgb, width, height);
        Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpeg");
        if (!it.hasNext()) throw new IOException("no JPEG writer available");
        ImageWriter writer = it.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(Math.max(0f, Math.min(1f, quality)));
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(1024, rgb.length / 8));
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /** Bilinearly resamples interleaved 8-bit RGB to {@code targetW × targetH}, returning RGB. */
    public static byte[] downsampleRgb(byte[] rgb, int width, int height, int targetW, int targetH) {
        BufferedImage src = toBufferedImage(rgb, width, height);
        BufferedImage dst = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, targetW, targetH, null);
        g.dispose();
        byte[] out = new byte[targetW * targetH * 3];
        int i = 0;
        for (int y = 0; y < targetH; y++) {
            for (int x = 0; x < targetW; x++) {
                int p = dst.getRGB(x, y);
                out[i++] = (byte) (p >> 16);
                out[i++] = (byte) (p >> 8);
                out[i++] = (byte) p;
            }
        }
        return out;
    }

    /** The largest dimension divisor needed to fit within {@code maxDim} (1 = no downscale). */
    public static int downscaleFactorFor(int width, int height, int maxDim) {
        int longest = Math.max(width, height);
        if (maxDim <= 0 || longest <= maxDim) return 1;
        return (int) Math.ceil((double) longest / maxDim);
    }

    private static BufferedImage toBufferedImage(byte[] rgb, int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int i = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = rgb[i++] & 0xFF, g = rgb[i++] & 0xFF, b = rgb[i++] & 0xFF;
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }
}
