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
package com.epdfengine.rb.org.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Rasterizes a CSS box-shadow into a blurred alpha coverage bitmap — the same
 * technique browsers/Skia use: draw the (rounded) box shape opaque, apply a
 * Gaussian blur (approximated by three box-blur passes, which converges to a true
 * Gaussian), and emit RGB (the solid shadow colour) + an alpha soft mask.
 */
final class ShadowRasterizer {

    static final class Result {
        byte[] rgb;      // width*height*3, solid shadow colour
        byte[] alpha;    // width*height, blurred coverage (soft mask)
        int width;
        int height;
        double padPt;    // transparent margin (pt) added around the box for blur spread
    }

    private ShadowRasterizer() {}

    static Result rasterize(double boxWpt, double boxHpt, double radiusPt, double blurPt,
                            int colorRgb, double cssAlpha, double scale) {
        double padPt = Math.max(0, blurPt * 1.5);
        double extentWpt = boxWpt + 2 * padPt;
        double extentHpt = boxHpt + 2 * padPt;
        int w = Math.max(1, (int) Math.round(extentWpt * scale));
        int h = Math.max(1, (int) Math.round(extentHpt * scale));

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int a255 = (int) Math.round(Math.max(0, Math.min(1, cssAlpha)) * 255);
        g.setColor(new Color((a255 << 24) | (colorRgb & 0xFFFFFF), true));
        double bx = padPt * scale, by = padPt * scale, bw = boxWpt * scale, bh = boxHpt * scale;
        double r = Math.min(radiusPt * scale, Math.min(bw, bh) / 2.0);
        if (r > 0) g.fill(new RoundRectangle2D.Double(bx, by, bw, bh, 2 * r, 2 * r));
        else g.fill(new Rectangle2D.Double(bx, by, bw, bh));
        g.dispose();

        int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
        int[] al = new int[w * h];
        for (int i = 0; i < al.length; i++) al[i] = (argb[i] >>> 24) & 0xFF;

        if (blurPt > 0) {
            int rad = Math.max(1, (int) Math.round(blurPt * scale / 2.0));
            for (int p = 0; p < 3; p++) boxBlur(al, w, h, rad);
        }

        Result res = new Result();
        res.width = w;
        res.height = h;
        res.padPt = padPt;
        res.alpha = new byte[w * h];
        res.rgb = new byte[w * h * 3];
        byte cr = (byte) ((colorRgb >> 16) & 0xFF);
        byte cg = (byte) ((colorRgb >> 8) & 0xFF);
        byte cb = (byte) (colorRgb & 0xFF);
        for (int i = 0; i < al.length; i++) {
            res.alpha[i] = (byte) al[i];
            int j = i * 3;
            res.rgb[j] = cr; res.rgb[j + 1] = cg; res.rgb[j + 2] = cb;
        }
        return res;
    }

    /** Separable sliding-window box blur (clamped edges) applied in place. */
    private static void boxBlur(int[] a, int w, int h, int r) {
        int win = 2 * r + 1;
        int[] tmp = new int[a.length];
        for (int y = 0; y < h; y++) {
            int base = y * w;
            int sum = 0;
            for (int k = -r; k <= r; k++) sum += a[base + clamp(k, w)];
            for (int x = 0; x < w; x++) {
                tmp[base + x] = sum / win;
                sum += a[base + clamp(x + r + 1, w)] - a[base + clamp(x - r, w)];
            }
        }
        for (int x = 0; x < w; x++) {
            int sum = 0;
            for (int k = -r; k <= r; k++) sum += tmp[clamp(k, h) * w + x];
            for (int y = 0; y < h; y++) {
                a[y * w + x] = sum / win;
                sum += tmp[clamp(y + r + 1, h) * w + x] - tmp[clamp(y - r, h) * w + x];
            }
        }
    }

    private static int clamp(int v, int n) {
        return v < 0 ? 0 : (v >= n ? n - 1 : v);
    }
}
