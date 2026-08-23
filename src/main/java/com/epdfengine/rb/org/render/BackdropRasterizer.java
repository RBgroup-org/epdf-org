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

import com.epdfengine.rb.org.css.ComputedStyle;
import com.epdfengine.rb.org.css.Gradient;
import com.epdfengine.rb.org.io.image.DecodedImage;
import com.epdfengine.rb.org.layout.Drawable;
import com.epdfengine.rb.org.layout.PaintedImage;
import com.epdfengine.rb.org.layout.PaintedRect;
import com.epdfengine.rb.org.layout.PaintedSvg;
import com.epdfengine.rb.org.layout.PositionedText;
import com.epdfengine.rb.org.svg.SvgRasterizer;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Produces a genuine {@code backdrop-filter: blur()} by rendering the drawables that lie
 * <em>behind</em> a glass panel into an off-screen raster, Gaussian-blurring it, and
 * returning it as an RGB image the renderer draws (clipped to the panel) under the panel's
 * translucent fill. Unlike a flat frost veil, this reflects the actual colours, gradients
 * and images behind the glass.
 *
 * <p>Backdrop layers rendered: solid and gradient (linear/radial/conic) rectangle fills and
 * raster images — the content real glass designs sit over. Text/SVG behind glass is not
 * rasterised (rare); when nothing paintable is behind the panel the caller keeps the frost
 * veil fallback.</p>
 */
final class BackdropRasterizer {

    static final class Result {
        byte[] rgb;      // width*height*3
        int width;
        int height;
        double padPt;    // transparent margin (pt) added around the panel for blur bleed
    }

    private BackdropRasterizer() {}

    /**
     * Renders {@code backdrops} (already filtered to earlier, same-page drawables that
     * intersect {@code panel}) into a blurred RGB raster covering the panel plus a blur
     * margin. Returns {@code null} when nothing paintable is behind the panel.
     */
    static Result rasterize(PaintedRect panel, List<Drawable> backdrops, double blurPt, double scale) {
        double pad = Math.max(6, blurPt * 1.5);
        double ox = panel.x() - pad, oy = panel.y() - pad;
        double bw = panel.width() + 2 * pad, bh = panel.height() + 2 * pad;
        if (bw <= 0 || bh <= 0) return null;
        int w = Math.max(1, (int) Math.round(bw * scale));
        int h = Math.max(1, (int) Math.round(bh * scale));

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);   // base so uncovered margin doesn't blur to black

        boolean painted = false;
        for (Drawable d : backdrops) {
            if (d instanceof PaintedRect r && (r.fillRgb() != null || r.gradient() != null)) {
                painted |= drawRect(img, g, r, ox, oy, scale);
            } else if (d instanceof PaintedImage pi) {
                painted |= drawImage(g, pi, ox, oy, scale);
            } else if (d instanceof PaintedSvg sv) {
                painted |= drawSvg(g, sv, ox, oy, scale);
            } else if (d instanceof PositionedText tx) {
                painted |= drawText(g, tx, ox, oy, scale);
            }
        }
        g.dispose();
        if (!painted) return null;

        int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
        int rad = Math.max(1, (int) Math.round(blurPt * scale / 2.0));
        blurRgb(argb, w, h, rad);

        Result res = new Result();
        res.width = w; res.height = h; res.padPt = pad;
        res.rgb = new byte[w * h * 3];
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            int j = i * 3;
            res.rgb[j]     = (byte) ((p >> 16) & 0xFF);
            res.rgb[j + 1] = (byte) ((p >> 8) & 0xFF);
            res.rgb[j + 2] = (byte) (p & 0xFF);
        }
        return res;
    }

    private static boolean drawRect(BufferedImage img, Graphics2D g, PaintedRect r,
                                    double ox, double oy, double scale) {
        double rx = (r.x() - ox) * scale, ry = (r.y() - oy) * scale;
        double rw = r.width() * scale, rh = r.height() * scale;
        if (rw <= 0 || rh <= 0) return false;
        double radius = Math.min(r.cornerRadiusPt() * scale, Math.min(rw, rh) / 2.0);

        if (r.gradient() != null && !r.gradient().stops().isEmpty()) {
            fillGradient(img, r, rx, ry, rw, rh, radius);
            return true;
        }
        int rgb = r.fillRgb();
        double alpha = Math.max(0, Math.min(1, r.fillAlpha()));
        g.setColor(new Color((int) ((alpha < 1 ? Math.round(alpha * 255) : 255) << 24)
                | (rgb & 0xFFFFFF), true));
        if (radius > 0) g.fill(new RoundRectangle2D.Double(rx, ry, rw, rh, 2 * radius, 2 * radius));
        else g.fillRect((int) Math.floor(rx), (int) Math.floor(ry),
                (int) Math.ceil(rw), (int) Math.ceil(rh));
        return true;
    }

    /** Per-pixel gradient fill (approximate; the result is blurred anyway) clipped to the rect. */
    private static void fillGradient(BufferedImage img, PaintedRect r,
                                     double rx, double ry, double rw, double rh, double radius) {
        Gradient grad = r.gradient();
        List<Gradient.Stop> stops = grad.stops();
        int W = img.getWidth(), H = img.getHeight();
        int x0 = Math.max(0, (int) Math.floor(rx)), x1 = Math.min(W, (int) Math.ceil(rx + rw));
        int y0 = Math.max(0, (int) Math.floor(ry)), y1 = Math.min(H, (int) Math.ceil(ry + rh));
        double cx = grad.centerXFrac(), cy = grad.centerYFrac();
        double angle = Math.toRadians(grad.angleDeg());
        double dirX = Math.sin(angle), dirY = -Math.cos(angle);   // CSS: 0deg = to top
        double norm = Math.abs(dirX) + Math.abs(dirY);
        double alpha = Math.max(0, Math.min(1, r.fillAlpha()));
        java.awt.geom.RoundRectangle2D clip = radius > 0
                ? new RoundRectangle2D.Double(rx, ry, rw, rh, 2 * radius, 2 * radius) : null;
        Area area = clip != null ? new Area(clip) : null;
        for (int y = y0; y < y1; y++) {
            double fy = (y + 0.5 - ry) / rh;
            for (int x = x0; x < x1; x++) {
                if (area != null && !area.contains(x + 0.5, y + 0.5)) continue;
                double fx = (x + 0.5 - rx) / rw;
                double t;
                switch (grad.type()) {
                    case RADIAL -> {
                        double dx = fx - cx, dy = fy - cy;
                        double maxR = Math.hypot(Math.max(cx, 1 - cx), Math.max(cy, 1 - cy));
                        t = maxR <= 0 ? 0 : Math.hypot(dx, dy) / maxR;
                    }
                    case CONIC -> {
                        double a = Math.toDegrees(Math.atan2(fx - cx, -(fy - cy)));
                        t = Renderer.wrap01((a - grad.angleDeg()) / 360.0);
                    }
                    default -> t = 0.5 + ((fx - 0.5) * dirX + (fy - 0.5) * dirY) / (norm <= 0 ? 1 : norm);
                }
                int rgb = Renderer.sampleStops(stops, Math.max(0, Math.min(1, t)));
                if (alpha < 1) {
                    int bg = img.getRGB(x, y);
                    rgb = blend(bg, rgb, alpha);
                }
                img.setRGB(x, y, 0xFF000000 | (rgb & 0xFFFFFF));
            }
        }
    }

    private static boolean drawImage(Graphics2D g, PaintedImage pi, double ox, double oy, double scale) {
        BufferedImage bi = toBufferedImage(pi.image());
        if (bi == null) return false;
        int dx = (int) Math.round((pi.x() - ox) * scale);
        int dy = (int) Math.round((pi.y() - oy) * scale);
        int dw = (int) Math.round(pi.width() * scale);
        int dh = (int) Math.round(pi.height() * scale);
        if (dw <= 0 || dh <= 0) return false;
        g.drawImage(bi, dx, dy, dw, dh, null);
        return true;
    }

    private static boolean drawSvg(Graphics2D g, PaintedSvg sv, double ox, double oy, double scale) {
        try {
            BufferedImage bi = toBufferedImage(SvgRasterizer.rasterize(sv.svg(), sv.width(), sv.height()));
            if (bi == null) return false;
            int dx = (int) Math.round((sv.x() - ox) * scale);
            int dy = (int) Math.round((sv.y() - oy) * scale);
            int dw = (int) Math.round(sv.width() * scale);
            int dh = (int) Math.round(sv.height() * scale);
            if (dw <= 0 || dh <= 0) return false;
            g.drawImage(bi, dx, dy, dw, dh, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Draws a text run with a matching AWT face; exact glyph shapes are irrelevant once blurred. */
    private static boolean drawText(Graphics2D g, PositionedText t, double ox, double oy, double scale) {
        String s = t.text();
        if (s == null || s.isBlank()) return false;
        ComputedStyle st = t.style();
        float size = (float) (st.fontSizePt() * scale);
        if (size < 1) return false;
        int style = (st.bold() ? Font.BOLD : 0) | (st.italic() ? Font.ITALIC : 0);
        g.setFont(new Font(Font.SANS_SERIF, style, 12).deriveFont(size));
        g.setColor(new Color(st.colorRgb() & 0xFFFFFF));
        double bx = (t.xPt() - ox) * scale, by = (t.baselineYFromTop() - oy) * scale;
        g.drawString(s, (float) bx, (float) by);
        return true;
    }

    private static BufferedImage toBufferedImage(DecodedImage di) {
        try {
            switch (di.kind()) {
                case JPEG -> {
                    return javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(di.data()));
                }
                case RGB -> {
                    BufferedImage bi = new BufferedImage(di.width(), di.height(), BufferedImage.TYPE_INT_RGB);
                    byte[] d = di.data();
                    for (int i = 0, p = 0; i < di.width() * di.height(); i++, p += 3) {
                        int rgb = ((d[p] & 0xFF) << 16) | ((d[p + 1] & 0xFF) << 8) | (d[p + 2] & 0xFF);
                        bi.setRGB(i % di.width(), i / di.width(), rgb);
                    }
                    return bi;
                }
                case RGBA -> {
                    BufferedImage bi = new BufferedImage(di.width(), di.height(), BufferedImage.TYPE_INT_ARGB);
                    byte[] d = di.data(); byte[] a = di.alpha();
                    for (int i = 0, p = 0; i < di.width() * di.height(); i++, p += 3) {
                        int argb = ((a[i] & 0xFF) << 24) | ((d[p] & 0xFF) << 16)
                                | ((d[p + 1] & 0xFF) << 8) | (d[p + 2] & 0xFF);
                        bi.setRGB(i % di.width(), i / di.width(), argb);
                    }
                    return bi;
                }
                default -> { return null; }
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static int blend(int bg, int fg, double a) {
        int br = (bg >> 16) & 0xFF, bgc = (bg >> 8) & 0xFF, bb = bg & 0xFF;
        int fr = (fg >> 16) & 0xFF, fgc = (fg >> 8) & 0xFF, fb = fg & 0xFF;
        int r = (int) Math.round(br + (fr - br) * a);
        int gg = (int) Math.round(bgc + (fgc - bgc) * a);
        int b = (int) Math.round(bb + (fb - bb) * a);
        return (r << 16) | (gg << 8) | b;
    }

    /** Three box-blur passes per channel (converges to Gaussian), on packed 0xRRGGBB. */
    private static void blurRgb(int[] px, int w, int h, int r) {
        int[] ch = new int[px.length];
        for (int shift = 16; shift >= 0; shift -= 8) {
            for (int i = 0; i < px.length; i++) ch[i] = (px[i] >> shift) & 0xFF;
            for (int p = 0; p < 3; p++) boxBlur(ch, w, h, r);
            for (int i = 0; i < px.length; i++) px[i] = (px[i] & ~(0xFF << shift)) | (ch[i] << shift);
        }
    }

    private static void boxBlur(int[] a, int w, int h, int r) {
        int win = 2 * r + 1;
        int[] tmp = new int[a.length];
        for (int y = 0; y < h; y++) {
            int base = y * w, sum = 0;
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

    private static int clamp(int v, int n) { return v < 0 ? 0 : (v >= n ? n - 1 : v); }
}
