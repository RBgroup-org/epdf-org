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
package com.epdfengine.rb.org.render.content;

import java.nio.charset.StandardCharsets;

/**
 * Builds a page's PDF content-stream bytes from high-level drawing calls. Inputs
 * use the layout coordinate system (top-left origin, y downward); this class flips
 * to PDF's bottom-left origin. Colours are packed 0xRRGGBB.
 */
public final class ContentStreamBuilder {

    private final StringBuilder sb = new StringBuilder(1024);
    private final double pageHeightPt;

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    // Bezier control-point ratio that best approximates a 90-degree circular arc.
    private static final double KAPPA = 0.5522847498307936;

    public ContentStreamBuilder(double pageHeightPt) {
        this.pageHeightPt = pageHeightPt;
    }

    /** Opens a tagged marked-content sequence: {@code /Role << /MCID n >> BDC} (PDF/UA). */
    public ContentStreamBuilder beginMarkedContent(String role, int mcid) {
        sb.append('/').append(role).append(" <</MCID ").append(mcid).append(">> BDC\n");
        return this;
    }

    /** Opens an artifact marked-content sequence: {@code /Artifact BMC} (decorative content). */
    public ContentStreamBuilder beginArtifact() {
        sb.append("/Artifact BMC\n");
        return this;
    }

    /** Opens an optional-content (layer) marked sequence: {@code /OC /name BDC}. */
    public ContentStreamBuilder beginOptionalContent(String propertyName) {
        sb.append("/OC /").append(propertyName).append(" BDC\n");
        return this;
    }

    /** Closes the most recent marked-content sequence: {@code EMC}. */
    public ContentStreamBuilder endMarkedContent() {
        sb.append("EMC\n");
        return this;
    }

    /** Fills a rectangle given its top-left corner (top-down y). */
    public ContentStreamBuilder fillRect(double x, double yTop, double w, double h, int rgb) {
        double yPdf = pageHeightPt - yTop - h;
        setColor(rgb, false);
        sb.append(fmt(x)).append(' ').append(fmt(yPdf)).append(' ')
          .append(fmt(w)).append(' ').append(fmt(h)).append(" re f\n");
        return this;
    }

    /** Strokes a rectangle outline with a uniform line width. */
    public ContentStreamBuilder strokeRect(double x, double yTop, double w, double h,
                                           double lineWidthPt, int rgb) {
        double yPdf = pageHeightPt - yTop - h;
        setColor(rgb, true);
        sb.append(fmt(lineWidthPt)).append(" w ")
          .append(fmt(x)).append(' ').append(fmt(yPdf)).append(' ')
          .append(fmt(w)).append(' ').append(fmt(h)).append(" re S\n");
        return this;
    }

    /** Shows a single-line text run placed at a baseline measured from the page top. */
    public ContentStreamBuilder text(String fontResource, double fontSizePt,
                                     double x, double baselineFromTop, String str, int rgb) {
        return text(fontResource, fontSizePt, x, baselineFromTop, str, rgb, 0);
    }

    /** Text run with a char-spacing (letter-spacing) applied via the {@code Tc} operator. */
    public ContentStreamBuilder text(String fontResource, double fontSizePt,
                                     double x, double baselineFromTop, String str, int rgb, double charSpacing) {
        double yPdf = pageHeightPt - baselineFromTop;
        setColor(rgb, false);
        sb.append("BT /").append(fontResource).append(' ').append(fmt(fontSizePt)).append(" Tf ");
        if (charSpacing != 0) sb.append(fmt(charSpacing)).append(" Tc ");
        sb.append(fmt(x)).append(' ').append(fmt(yPdf)).append(" Td (")
          .append(escapeLiteral(str)).append(") Tj");
        if (charSpacing != 0) sb.append(" 0 Tc");
        sb.append(" ET\n");
        return this;
    }

    /**
     * Shows text for an Identity-H (Type0/CID) font by glyph id: emits a hex string
     * of 2-byte big-endian glyph ids. Baseline is measured from the page top.
     */
    public ContentStreamBuilder glyphs(String fontResource, double fontSizePt,
                                       double x, double baselineFromTop, int[] gids, int rgb) {
        return glyphs(fontResource, fontSizePt, x, baselineFromTop, gids, rgb, 0);
    }

    /** Identity-H glyph run with a char-spacing (letter-spacing) via the {@code Tc} operator. */
    public ContentStreamBuilder glyphs(String fontResource, double fontSizePt,
                                       double x, double baselineFromTop, int[] gids, int rgb, double charSpacing) {
        if (gids == null || gids.length == 0) return this;
        double yPdf = pageHeightPt - baselineFromTop;
        setColor(rgb, false);
        sb.append("BT /").append(fontResource).append(' ').append(fmt(fontSizePt)).append(" Tf ");
        if (charSpacing != 0) sb.append(fmt(charSpacing)).append(" Tc ");
        sb.append(fmt(x)).append(' ').append(fmt(yPdf)).append(" Td <");
        for (int g : gids) {
            sb.append(HEX[(g >> 12) & 0xF]).append(HEX[(g >> 8) & 0xF])
              .append(HEX[(g >> 4) & 0xF]).append(HEX[g & 0xF]);
        }
        sb.append("> Tj");
        if (charSpacing != 0) sb.append(" 0 Tc");
        sb.append(" ET\n");
        return this;
    }

    /** Draws an image XObject in the box (top-left x/y, size w×h in points). */
    public ContentStreamBuilder drawImage(String resource, double x, double yTop, double w, double h) {
        double yPdf = pageHeightPt - (yTop + h);
        sb.append("q ").append(fmt(w)).append(" 0 0 ").append(fmt(h)).append(' ')
          .append(fmt(x)).append(' ').append(fmt(yPdf)).append(" cm /")
          .append(resource).append(" Do Q\n");
        return this;
    }

    // --- vector path primitives (used by SVG and shape drawing) ---

    public ContentStreamBuilder saveState()    { sb.append("q\n"); return this; }
    public ContentStreamBuilder restoreState() { sb.append("Q\n"); return this; }

    /** Concatenates a raw transformation matrix: {@code a b c d e f cm} (PDF user space, y-up). */
    public ContentStreamBuilder transform(double a, double b, double c, double d, double e, double f) {
        sb.append(fmt(a)).append(' ').append(fmt(b)).append(' ').append(fmt(c)).append(' ')
          .append(fmt(d)).append(' ').append(fmt(e)).append(' ').append(fmt(f)).append(" cm\n");
        return this;
    }

    public ContentStreamBuilder setFillRgb(int rgb)    { setColor(rgb, false); return this; }
    public ContentStreamBuilder setStrokeRgb(int rgb)  { setColor(rgb, true);  return this; }
    public ContentStreamBuilder setLineWidth(double w) { sb.append(fmt(w)).append(" w\n"); return this; }

    /** Path move-to; coordinates are top-left (y flipped to PDF space here). */
    public ContentStreamBuilder moveTo(double x, double yTop) {
        sb.append(fmt(x)).append(' ').append(fmt(pageHeightPt - yTop)).append(" m\n");
        return this;
    }

    public ContentStreamBuilder lineTo(double x, double yTop) {
        sb.append(fmt(x)).append(' ').append(fmt(pageHeightPt - yTop)).append(" l\n");
        return this;
    }

    /** Cubic Bézier; all coordinates top-left (y flipped here). */
    public ContentStreamBuilder curveTo(double x1, double y1t, double x2, double y2t, double x, double yTop) {
        sb.append(fmt(x1)).append(' ').append(fmt(pageHeightPt - y1t)).append(' ')
          .append(fmt(x2)).append(' ').append(fmt(pageHeightPt - y2t)).append(' ')
          .append(fmt(x)).append(' ').append(fmt(pageHeightPt - yTop)).append(" c\n");
        return this;
    }

    public ContentStreamBuilder closePath()     { sb.append("h\n"); return this; }
    public ContentStreamBuilder fill()          { sb.append("f\n"); return this; }
    public ContentStreamBuilder stroke()        { sb.append("S\n"); return this; }
    public ContentStreamBuilder fillAndStroke() { sb.append("B\n"); return this; }

    // --- rounded rectangles, clipping and shading (CSS border-radius / gradients) ---

    /** Appends a rounded-rectangle subpath (top-left origin); r is clamped to half the shorter side. */
    private void appendRoundedRectPath(double x, double yTop, double w, double h, double r) {
        appendRoundedRectPath(x, yTop, w, h, r, r, r, r);
    }

    /** Appends a rounded-rectangle subpath with per-corner radii (TL, TR, BR, BL); top-left origin. */
    private void appendRoundedRectPath(double x, double yTop, double w, double h,
                                       double tl, double tr, double br, double bl) {
        double lim = Math.min(w, h) / 2.0;
        tl = Math.min(Math.max(0, tl), lim);
        tr = Math.min(Math.max(0, tr), lim);
        br = Math.min(Math.max(0, br), lim);
        bl = Math.min(Math.max(0, bl), lim);
        if (tl <= 0 && tr <= 0 && br <= 0 && bl <= 0) {
            moveTo(x, yTop); lineTo(x + w, yTop); lineTo(x + w, yTop + h); lineTo(x, yTop + h); closePath();
            return;
        }
        double kTl = tl * KAPPA, kTr = tr * KAPPA, kBr = br * KAPPA, kBl = bl * KAPPA;
        moveTo(x + tl, yTop);
        lineTo(x + w - tr, yTop);
        curveTo(x + w - tr + kTr, yTop, x + w, yTop + tr - kTr, x + w, yTop + tr);
        lineTo(x + w, yTop + h - br);
        curveTo(x + w, yTop + h - br + kBr, x + w - br + kBr, yTop + h, x + w - br, yTop + h);
        lineTo(x + bl, yTop + h);
        curveTo(x + bl - kBl, yTop + h, x, yTop + h - bl + kBl, x, yTop + h - bl);
        lineTo(x, yTop + tl);
        curveTo(x, yTop + tl - kTl, x + tl - kTl, yTop, x + tl, yTop);
        closePath();
    }

    /**
     * Fills a CSS border ring: the region between the outer rounded rectangle and the
     * per-side-inset inner edge (whose corners are elliptical), using the even-odd rule.
     * Border widths {@code wl/wt/wr/wb} inset each side, so a thick side tapers smoothly
     * to a thin side around the corner. Clip to a side's wedge to colour that side.
     */
    public ContentStreamBuilder fillBorderRing(double x, double yTop, double w, double h,
                                               double tl, double tr, double br, double bl,
                                               double wl, double wt, double wr, double wb, int rgb) {
        setColor(rgb, false);
        appendRoundedRectPath(x, yTop, w, h, tl, tr, br, bl);
        appendInnerBorderPath(x, yTop, w, h, tl, tr, br, bl, wl, wt, wr, wb);
        sb.append("f*\n");                              // even-odd: outer minus inner = the border band
        return this;
    }

    /** Appends the inner border edge (per-side inset, elliptical corners), clockwise; top-left origin. */
    private void appendInnerBorderPath(double x, double yTop, double w, double h,
                                       double tl, double tr, double br, double bl,
                                       double wl, double wt, double wr, double wb) {
        double il = x + wl, it = yTop + wt, ir = x + w - wr, ib = yTop + h - wb;
        if (ir <= il || ib <= it) { moveTo(il, it); lineTo(ir, it); lineTo(ir, ib); lineTo(il, ib); closePath(); return; }
        double K = KAPPA;
        double tlx = Math.max(0, tl - wl), tly = Math.max(0, tl - wt);
        double trx = Math.max(0, tr - wr), tryy = Math.max(0, tr - wt);
        double brx = Math.max(0, br - wr), bryy = Math.max(0, br - wb);
        double blx = Math.max(0, bl - wl), blyy = Math.max(0, bl - wb);
        moveTo(x + tl, it);
        lineTo(x + w - tr, it);
        curveTo(x + w - tr + trx * K, it, ir, yTop + tr - tryy * K, ir, yTop + tr);
        lineTo(ir, yTop + h - br);
        curveTo(ir, yTop + h - br + bryy * K, x + w - br + brx * K, ib, x + w - br, ib);
        lineTo(x + bl, ib);
        curveTo(x + bl - blx * K, ib, il, yTop + h - bl + blyy * K, il, yTop + h - bl);
        lineTo(il, yTop + tl);
        curveTo(il, yTop + tl - tly * K, x + tl - tlx * K, it, x + tl, it);
        closePath();
    }

    /** Sets a dash pattern (on/off lengths in pt). */
    public ContentStreamBuilder setDash(double on, double off) {
        sb.append('[').append(fmt(on)).append(' ').append(fmt(off)).append("] 0 d\n");
        return this;
    }

    /** Clears any dash pattern (solid line). */
    public ContentStreamBuilder clearDash() {
        sb.append("[] 0 d\n");
        return this;
    }

    /** Selects a registered graphics state (e.g. constant alpha) via {@code /name gs}. */
    public ContentStreamBuilder setGraphicsState(String gsName) {
        sb.append('/').append(gsName).append(" gs\n");
        return this;
    }

    /** Sets the line cap style: 0 butt, 1 round, 2 square. */
    public ContentStreamBuilder setLineCap(int cap) {
        sb.append(cap).append(" J\n");
        return this;
    }

    /** Fills a rounded rectangle with per-corner radii (top-left origin). */
    public ContentStreamBuilder fillRoundedRect(double x, double yTop, double w, double h,
                                                double tl, double tr, double br, double bl, int rgb) {
        setColor(rgb, false);
        appendRoundedRectPath(x, yTop, w, h, tl, tr, br, bl);
        return fill();
    }

    /** Strokes a rounded-rectangle outline with per-corner radii (top-left origin). */
    public ContentStreamBuilder strokeRoundedRect(double x, double yTop, double w, double h,
                                                  double tl, double tr, double br, double bl,
                                                  double lineWidthPt, int rgb) {
        setColor(rgb, true);
        setLineWidth(lineWidthPt);
        appendRoundedRectPath(x, yTop, w, h, tl, tr, br, bl);
        return stroke();
    }

    /** Intersects the clip with a rounded rectangle with per-corner radii (top-left origin). */
    public ContentStreamBuilder clipRoundedRect(double x, double yTop, double w, double h,
                                                double tl, double tr, double br, double bl) {
        appendRoundedRectPath(x, yTop, w, h, tl, tr, br, bl);
        sb.append("W n\n");
        return this;
    }

    /** Fills a rounded rectangle (top-left origin). r <= 0 falls back to a sharp rectangle. */
    public ContentStreamBuilder fillRoundedRect(double x, double yTop, double w, double h, double r, int rgb) {
        setColor(rgb, false);
        appendRoundedRectPath(x, yTop, w, h, r);
        return fill();
    }

    /** Strokes a rounded-rectangle outline (top-left origin). */
    public ContentStreamBuilder strokeRoundedRect(double x, double yTop, double w, double h,
                                                  double r, double lineWidthPt, int rgb) {
        setColor(rgb, true);
        setLineWidth(lineWidthPt);
        appendRoundedRectPath(x, yTop, w, h, r);
        return stroke();
    }

    /** Intersects the clip path with a rectangle (top-left origin). */
    public ContentStreamBuilder clipRect(double x, double yTop, double w, double h) {
        double yPdf = pageHeightPt - yTop - h;
        sb.append(fmt(x)).append(' ').append(fmt(yPdf)).append(' ')
          .append(fmt(w)).append(' ').append(fmt(h)).append(" re W n\n");
        return this;
    }

    /** Intersects the clip path with a polygon (top-left origin points), non-zero winding. */
    public ContentStreamBuilder clipPolygon(double[] xs, double[] ysTop) {
        if (xs == null || xs.length < 3 || xs.length != ysTop.length) return this;
        moveTo(xs[0], ysTop[0]);
        for (int i = 1; i < xs.length; i++) lineTo(xs[i], ysTop[i]);
        closePath();
        sb.append("W n\n");
        return this;
    }

    /** Intersects the clip path with a rounded rectangle (top-left origin). */
    public ContentStreamBuilder clipRoundedRect(double x, double yTop, double w, double h, double r) {
        appendRoundedRectPath(x, yTop, w, h, r);
        sb.append("W n\n");
        return this;
    }

    /** Paints the named shading (fills the current clip region) via the {@code sh} operator. */
    public ContentStreamBuilder paintShading(String shadingResource) {
        sb.append('/').append(shadingResource).append(" sh\n");
        return this;
    }

    public byte[] toBytes() {
        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private void setColor(int rgb, boolean stroking) {
        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        sb.append(fmt(r)).append(' ').append(fmt(g)).append(' ').append(fmt(b))
          .append(stroking ? " RG\n" : " rg\n");
    }

    private static String fmt(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) return Long.toString((long) v);
        StringBuilder b = new StringBuilder(16);
        appendFixed3(b, v);
        return b.toString();
    }

    /** Appends {@code v} with exactly three decimals (matches {@code "%.3f"}) with no Formatter. */
    static void appendFixed3(StringBuilder b, double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) { b.append('0'); return; }
        if (v < 0) { b.append('-'); v = -v; }
        long scaled = Math.round(v * 1000.0);   // half-up on the magnitude, like %.3f
        b.append(scaled / 1000).append('.');
        int frac = (int) (scaled % 1000);
        b.append((char) ('0' + frac / 100))
         .append((char) ('0' + (frac / 10) % 10))
         .append((char) ('0' + frac % 10));
    }

    /** Package-private hook for the number-format parity test. */
    static String formatNumber(double v) { return fmt(v); }

    /** Escapes a PDF literal string: backslash, parens, and non-printables (WinAnsi). */
    private static String escapeLiteral(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '('  -> out.append("\\(");
                case ')'  -> out.append("\\)");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 32 || c > 126) {
                        int wa = winAnsiByte(c);
                        if (wa >= 0) appendOctal(out, wa);
                        else if (c <= 255) appendOctal(out, c);
                        else out.append('?'); // non-WinAnsi needs an embedded font (io.font)
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    /** Appends {@code b} (0-255) as a three-digit octal escape, e.g. {@code \225}. */
    private static void appendOctal(StringBuilder out, int b) {
        b &= 0xFF;
        out.append('\\')
           .append((char) ('0' + ((b >> 6) & 7)))
           .append((char) ('0' + ((b >> 3) & 7)))
           .append((char) ('0' + (b & 7)));
    }

    /** Maps common Unicode punctuation to its WinAnsi (CP1252) byte, or -1 if none. */
    private static int winAnsiByte(char c) {
        return switch (c) {
            case '\u20AC' -> 0x80;  // euro
            case '\u201A' -> 0x82;  case '\u0192' -> 0x83;  case '\u201E' -> 0x84;
            case '\u2026' -> 0x85;  // ellipsis
            case '\u2020' -> 0x86;  case '\u2021' -> 0x87;  case '\u02C6' -> 0x88;
            case '\u2030' -> 0x89;  case '\u0160' -> 0x8A;  case '\u2039' -> 0x8B;
            case '\u0152' -> 0x8C;  case '\u017D' -> 0x8E;
            case '\u2018' -> 0x91;  case '\u2019' -> 0x92;  // curly single quotes
            case '\u201C' -> 0x93;  case '\u201D' -> 0x94;  // curly double quotes
            case '\u2022' -> 0x95;  // bullet
            case '\u2013' -> 0x96;  case '\u2014' -> 0x97;  // en/em dash
            case '\u02DC' -> 0x98;  case '\u2122' -> 0x99;  // tilde, trademark
            case '\u0161' -> 0x9A;  case '\u203A' -> 0x9B;  case '\u0153' -> 0x9C;
            case '\u017E' -> 0x9E;  case '\u0178' -> 0x9F;
            default -> -1;
        };
    }
}
