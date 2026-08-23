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
package com.epdfengine.rb.org.svg;

import com.epdfengine.rb.org.io.image.DecodedImage;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/**
 * Rasterizes an {@link SvgImage} to an RGBA bitmap (via the JDK's Java2D) for the
 * raster fallback path. The result flows through the normal image-embedding
 * pipeline. Uses {@code java.desktop}; the default SVG path is vector and needs no
 * AWT.
 */
public final class SvgRasterizer {

    private SvgRasterizer() {}

    /** Renders the SVG at ~2× the target point size (supersampled) into an ARGB image. */
    public static DecodedImage rasterize(SvgImage svg, double widthPt, double heightPt) {
        int scale = 2;
        int w = Math.max(1, (int) Math.round(widthPt / 0.75 * scale)); // pt -> px, supersampled
        int h = Math.max(1, (int) Math.round(heightPt / 0.75 * scale));
        double vbW = svg.viewBoxWidth() <= 0 ? w : svg.viewBoxWidth();
        double vbH = svg.viewBoxHeight() <= 0 ? h : svg.viewBoxHeight();
        double sx = w / vbW, sy = h / vbH, avg = (sx + sy) / 2.0;
        double minX = svg.viewBoxMinX(), minY = svg.viewBoxMinY();

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (SvgPath path : svg.paths()) {
                if (!path.hasFill() && !path.hasStroke()) continue;
                Path2D.Double gp = new Path2D.Double();
                for (SvgPath.Seg s : path.segments()) {
                    double[] q = s.pts();
                    switch (s.kind()) {
                        case SvgPath.MOVE  -> gp.moveTo((q[0] - minX) * sx, (q[1] - minY) * sy);
                        case SvgPath.LINE  -> gp.lineTo((q[0] - minX) * sx, (q[1] - minY) * sy);
                        case SvgPath.CUBIC -> gp.curveTo((q[0] - minX) * sx, (q[1] - minY) * sy,
                                (q[2] - minX) * sx, (q[3] - minY) * sy, (q[4] - minX) * sx, (q[5] - minY) * sy);
                        case SvgPath.CLOSE -> gp.closePath();
                        default -> { }
                    }
                }
                if (path.hasFill()) { g.setColor(new Color(path.fillRgb())); g.fill(gp); }
                if (path.hasStroke()) {
                    g.setStroke(new BasicStroke((float) Math.max(0.1, path.strokeWidth() * avg)));
                    g.setColor(new Color(path.strokeRgb()));
                    g.draw(gp);
                }
            }
        } finally {
            g.dispose();
        }

        int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
        byte[] rgb = new byte[w * h * 3];
        byte[] alpha = new byte[w * h];
        for (int i = 0; i < w * h; i++) {
            int a = argb[i];
            rgb[i * 3] = (byte) (a >> 16);
            rgb[i * 3 + 1] = (byte) (a >> 8);
            rgb[i * 3 + 2] = (byte) a;
            alpha[i] = (byte) (a >>> 24);
        }
        return DecodedImage.rgba(rgb, alpha, w, h);
    }
}
