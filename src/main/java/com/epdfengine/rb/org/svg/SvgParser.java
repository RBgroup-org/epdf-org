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

import com.epdfengine.rb.org.html.HtmlNode;
import com.epdfengine.rb.org.svg.SvgPath.Seg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses an {@code <svg>} element (as an {@link HtmlNode}) into an {@link SvgImage}
 * of flattened {@link SvgPath}s in viewBox space. Element transforms are baked into
 * coordinates; paint (fill/stroke/stroke-width) is inherited down the tree.
 *
 * <p>Supported: {@code g a svg}, {@code path} (M L H V C S Q T Z, absolute and
 * relative; arcs degrade to a line), {@code rect circle ellipse line polyline
 * polygon}; transforms {@code translate scale rotate matrix}; colours as named /
 * {@code #rgb} / {@code #rrggbb} / {@code rgb(...)}. Gradients, patterns, clip
 * paths, text and filters are not yet handled.</p>
 */
public final class SvgParser {

    private static final double[] IDENTITY = {1, 0, 0, 1, 0, 0};
    private static final double KAPPA = 0.5522847498307936;

    private record Paint(Integer fill, Integer stroke, double strokeWidth) {}

    private SvgParser() {}

    public static SvgImage parse(HtmlNode svg) {
        double[] vb = parseViewBox(svg.attr("viewbox"));
        double wPx = parseLenPx(svg.attr("width"));
        double hPx = parseLenPx(svg.attr("height"));
        if (vb == null) {
            double w = wPx > 0 ? wPx : 300;
            double h = hPx > 0 ? hPx : 150;
            vb = new double[]{0, 0, w, h};
        }
        double widthPt  = (wPx > 0 ? wPx : vb[2]) * 0.75;   // px -> pt
        double heightPt = (hPx > 0 ? hPx : vb[3]) * 0.75;

        List<SvgPath> paths = new ArrayList<>();
        Paint root = new Paint(0x000000, null, 1.0); // SVG default: fill black, no stroke
        walk(svg, IDENTITY, root, paths);
        return new SvgImage(vb, widthPt, heightPt, paths);
    }

    private static void walk(HtmlNode el, double[] ctm, Paint paint, List<SvgPath> out) {
        for (HtmlNode c : el.children()) {
            if (!c.isElement()) continue;
            double[] childCtm = ctm;
            String tf = c.attr("transform");
            if (tf != null) childCtm = mul(ctm, parseTransform(tf));
            Paint p = resolvePaint(c, paint);

            switch (c.tagName()) {
                case "g", "a", "svg" -> walk(c, childCtm, p, out);
                case "path" -> {
                    String d = c.attr("d");
                    if (d != null) {
                        List<Seg> segs = buildPath(d, childCtm);
                        if (!segs.isEmpty()) out.add(new SvgPath(segs, p.fill(), p.stroke(), p.strokeWidth()));
                    }
                }
                case "rect" -> addRect(c, childCtm, p, out);
                case "circle" -> {
                    double cx = numAttr(c, "cx", 0), cy = numAttr(c, "cy", 0), r = numAttr(c, "r", 0);
                    if (r > 0) out.add(new SvgPath(ellipse(cx, cy, r, r, childCtm), p.fill(), p.stroke(), p.strokeWidth()));
                }
                case "ellipse" -> {
                    double cx = numAttr(c, "cx", 0), cy = numAttr(c, "cy", 0);
                    double rx = numAttr(c, "rx", 0), ry = numAttr(c, "ry", 0);
                    if (rx > 0 && ry > 0) out.add(new SvgPath(ellipse(cx, cy, rx, ry, childCtm), p.fill(), p.stroke(), p.strokeWidth()));
                }
                case "line" -> {
                    double x1 = numAttr(c, "x1", 0), y1 = numAttr(c, "y1", 0);
                    double x2 = numAttr(c, "x2", 0), y2 = numAttr(c, "y2", 0);
                    List<Seg> segs = new ArrayList<>();
                    segs.add(move(childCtm, x1, y1));
                    segs.add(line(childCtm, x2, y2));
                    out.add(new SvgPath(segs, null, p.stroke(), p.strokeWidth())); // lines have no fill
                }
                case "polyline" -> addPoly(c, childCtm, p, out, false);
                case "polygon" -> addPoly(c, childCtm, p, out, true);
                default -> { /* text, defs, gradients, etc. — not yet handled */ }
            }
        }
    }

    // --- shape builders ---

    private static void addRect(HtmlNode c, double[] ctm, Paint p, List<SvgPath> out) {
        double x = numAttr(c, "x", 0), y = numAttr(c, "y", 0);
        double w = numAttr(c, "width", 0), h = numAttr(c, "height", 0);
        if (w <= 0 || h <= 0) return;
        List<Seg> segs = new ArrayList<>();
        segs.add(move(ctm, x, y));
        segs.add(line(ctm, x + w, y));
        segs.add(line(ctm, x + w, y + h));
        segs.add(line(ctm, x, y + h));
        segs.add(new Seg(SvgPath.CLOSE, new double[0]));
        out.add(new SvgPath(segs, p.fill(), p.stroke(), p.strokeWidth()));
    }

    private static void addPoly(HtmlNode c, double[] ctm, Paint p, List<SvgPath> out, boolean close) {
        double[] pts = nums(c.attr("points"));
        if (pts.length < 4) return;
        List<Seg> segs = new ArrayList<>();
        segs.add(move(ctm, pts[0], pts[1]));
        for (int i = 2; i + 1 < pts.length; i += 2) segs.add(line(ctm, pts[i], pts[i + 1]));
        if (close) segs.add(new Seg(SvgPath.CLOSE, new double[0]));
        // polyline is not filled; polygon uses the fill.
        out.add(new SvgPath(segs, close ? p.fill() : null, p.stroke(), p.strokeWidth()));
    }

    private static List<Seg> ellipse(double cx, double cy, double rx, double ry, double[] m) {
        List<Seg> s = new ArrayList<>();
        s.add(move(m, cx + rx, cy));
        s.add(cubic(m, cx + rx, cy + ry * KAPPA, cx + rx * KAPPA, cy + ry, cx, cy + ry));
        s.add(cubic(m, cx - rx * KAPPA, cy + ry, cx - rx, cy + ry * KAPPA, cx - rx, cy));
        s.add(cubic(m, cx - rx, cy - ry * KAPPA, cx - rx * KAPPA, cy - ry, cx, cy - ry));
        s.add(cubic(m, cx + rx * KAPPA, cy - ry, cx + rx, cy - ry * KAPPA, cx + rx, cy));
        s.add(new Seg(SvgPath.CLOSE, new double[0]));
        return s;
    }

    // --- path 'd' parsing ---

    private static List<Seg> buildPath(String d, double[] ctm) {
        List<Seg> segs = new ArrayList<>();
        List<String> t = tokenizeD(d);
        int[] p = {0};
        char cmd = 0;
        double cx = 0, cy = 0, startX = 0, startY = 0;
        double prevC2x = 0, prevC2y = 0, prevQx = 0, prevQy = 0;
        boolean prevCubic = false, prevQuad = false;

        while (p[0] < t.size()) {
            String tok = t.get(p[0]);
            if (tok.length() == 1 && Character.isLetter(tok.charAt(0))) {
                cmd = tok.charAt(0);
                p[0]++;
                if (cmd == 'Z' || cmd == 'z') {
                    segs.add(new Seg(SvgPath.CLOSE, new double[0]));
                    cx = startX; cy = startY; prevCubic = false; prevQuad = false;
                }
                continue;
            }
            if (cmd == 0) { p[0]++; continue; }
            boolean rel = Character.isLowerCase(cmd);
            switch (Character.toUpperCase(cmd)) {
                case 'M' -> {
                    double x = num(t, p), y = num(t, p);
                    if (rel) { x += cx; y += cy; }
                    cx = x; cy = y; startX = x; startY = y;
                    segs.add(move(ctm, x, y));
                    cmd = rel ? 'l' : 'L';                 // subsequent pairs are implicit line-to
                    prevCubic = false; prevQuad = false;
                }
                case 'L' -> {
                    double x = num(t, p), y = num(t, p);
                    if (rel) { x += cx; y += cy; }
                    cx = x; cy = y; segs.add(line(ctm, x, y)); prevCubic = false; prevQuad = false;
                }
                case 'H' -> {
                    double x = num(t, p); if (rel) x += cx; cx = x;
                    segs.add(line(ctm, cx, cy)); prevCubic = false; prevQuad = false;
                }
                case 'V' -> {
                    double y = num(t, p); if (rel) y += cy; cy = y;
                    segs.add(line(ctm, cx, cy)); prevCubic = false; prevQuad = false;
                }
                case 'C' -> {
                    double x1 = num(t, p), y1 = num(t, p), x2 = num(t, p), y2 = num(t, p), x = num(t, p), y = num(t, p);
                    if (rel) { x1 += cx; y1 += cy; x2 += cx; y2 += cy; x += cx; y += cy; }
                    segs.add(cubic(ctm, x1, y1, x2, y2, x, y));
                    prevC2x = x2; prevC2y = y2; cx = x; cy = y; prevCubic = true; prevQuad = false;
                }
                case 'S' -> {
                    double x2 = num(t, p), y2 = num(t, p), x = num(t, p), y = num(t, p);
                    if (rel) { x2 += cx; y2 += cy; x += cx; y += cy; }
                    double x1 = prevCubic ? 2 * cx - prevC2x : cx;
                    double y1 = prevCubic ? 2 * cy - prevC2y : cy;
                    segs.add(cubic(ctm, x1, y1, x2, y2, x, y));
                    prevC2x = x2; prevC2y = y2; cx = x; cy = y; prevCubic = true; prevQuad = false;
                }
                case 'Q' -> {
                    double qx = num(t, p), qy = num(t, p), x = num(t, p), y = num(t, p);
                    if (rel) { qx += cx; qy += cy; x += cx; y += cy; }
                    segs.add(quadAsCubic(ctm, cx, cy, qx, qy, x, y));
                    prevQx = qx; prevQy = qy; cx = x; cy = y; prevQuad = true; prevCubic = false;
                }
                case 'T' -> {
                    double x = num(t, p), y = num(t, p);
                    if (rel) { x += cx; y += cy; }
                    double qx = prevQuad ? 2 * cx - prevQx : cx;
                    double qy = prevQuad ? 2 * cy - prevQy : cy;
                    segs.add(quadAsCubic(ctm, cx, cy, qx, qy, x, y));
                    prevQx = qx; prevQy = qy; cx = x; cy = y; prevQuad = true; prevCubic = false;
                }
                case 'A' -> {
                    // Arc: consume rx ry rot large sweep x y; degrade to a line to the endpoint.
                    num(t, p); num(t, p); num(t, p); num(t, p); num(t, p);
                    double x = num(t, p), y = num(t, p);
                    if (rel) { x += cx; y += cy; }
                    cx = x; cy = y; segs.add(line(ctm, x, y)); prevCubic = false; prevQuad = false;
                }
                default -> p[0]++;
            }
        }
        return segs;
    }

    private static Seg quadAsCubic(double[] m, double cx, double cy, double qx, double qy, double x, double y) {
        double c1x = cx + 2.0 / 3 * (qx - cx), c1y = cy + 2.0 / 3 * (qy - cy);
        double c2x = x + 2.0 / 3 * (qx - x),  c2y = y + 2.0 / 3 * (qy - y);
        return cubic(m, c1x, c1y, c2x, c2y, x, y);
    }

    private static List<String> tokenizeD(String d) {
        List<String> toks = new ArrayList<>();
        int i = 0, n = d.length();
        while (i < n) {
            char c = d.charAt(i);
            if (Character.isLetter(c)) { toks.add(String.valueOf(c)); i++; }
            else if (c == ',' || Character.isWhitespace(c)) { i++; }
            else {
                int st = i;
                if (c == '+' || c == '-') i++;
                while (i < n && (Character.isDigit(d.charAt(i)) || d.charAt(i) == '.')) i++;
                if (i < n && (d.charAt(i) == 'e' || d.charAt(i) == 'E')) {
                    i++;
                    if (i < n && (d.charAt(i) == '+' || d.charAt(i) == '-')) i++;
                    while (i < n && Character.isDigit(d.charAt(i))) i++;
                }
                if (i > st) toks.add(d.substring(st, i)); else i++;
            }
        }
        return toks;
    }

    private static double num(List<String> toks, int[] p) {
        if (p[0] >= toks.size()) { p[0]++; return 0; }
        String s = toks.get(p[0]++);
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
    }

    // --- transforms & geometry ---

    private static Seg move(double[] m, double x, double y)  { double[] q = apply(m, x, y); return new Seg(SvgPath.MOVE, new double[]{q[0], q[1]}); }
    private static Seg line(double[] m, double x, double y)  { double[] q = apply(m, x, y); return new Seg(SvgPath.LINE, new double[]{q[0], q[1]}); }

    private static Seg cubic(double[] m, double x1, double y1, double x2, double y2, double x, double y) {
        double[] a = apply(m, x1, y1), b = apply(m, x2, y2), c = apply(m, x, y);
        return new Seg(SvgPath.CUBIC, new double[]{a[0], a[1], b[0], b[1], c[0], c[1]});
    }

    private static double[] apply(double[] m, double x, double y) {
        return new double[]{m[0] * x + m[2] * y + m[4], m[1] * x + m[3] * y + m[5]};
    }

    private static double[] mul(double[] a, double[] b) {
        return new double[]{
                a[0] * b[0] + a[2] * b[1],
                a[1] * b[0] + a[3] * b[1],
                a[0] * b[2] + a[2] * b[3],
                a[1] * b[2] + a[3] * b[3],
                a[0] * b[4] + a[2] * b[5] + a[4],
                a[1] * b[4] + a[3] * b[5] + a[5]
        };
    }

    private static double[] parseTransform(String s) {
        double[] m = IDENTITY;
        int i = 0, n = s.length();
        while (i < n) {
            while (i < n && !Character.isLetter(s.charAt(i))) i++;
            int st = i;
            while (i < n && (Character.isLetter(s.charAt(i)))) i++;
            String name = s.substring(st, i);
            int op = s.indexOf('(', i);
            if (op < 0) break;
            int cp = s.indexOf(')', op);
            if (cp < 0) break;
            double[] a = nums(s.substring(op + 1, cp));
            double[] t = switch (name) {
                case "translate" -> new double[]{1, 0, 0, 1, a.length > 0 ? a[0] : 0, a.length > 1 ? a[1] : 0};
                case "scale" -> {
                    double sx = a.length > 0 ? a[0] : 1;
                    double sy = a.length > 1 ? a[1] : sx;
                    yield new double[]{sx, 0, 0, sy, 0, 0};
                }
                case "rotate" -> {
                    double ang = Math.toRadians(a.length > 0 ? a[0] : 0);
                    double co = Math.cos(ang), si = Math.sin(ang);
                    double[] r = {co, si, -si, co, 0, 0};
                    if (a.length >= 3) {
                        double[] t1 = {1, 0, 0, 1, a[1], a[2]};
                        double[] t2 = {1, 0, 0, 1, -a[1], -a[2]};
                        r = mul(mul(t1, r), t2);
                    }
                    yield r;
                }
                case "matrix" -> a.length >= 6 ? new double[]{a[0], a[1], a[2], a[3], a[4], a[5]} : IDENTITY;
                default -> IDENTITY;
            };
            m = mul(m, t);
            i = cp + 1;
        }
        return m;
    }

    // --- attribute / value parsing ---

    private static Paint resolvePaint(HtmlNode el, Paint parent) {
        Integer fill = parent.fill(), stroke = parent.stroke();
        double sw = parent.strokeWidth();

        String fa = el.attr("fill");
        if (fa != null) fill = "none".equalsIgnoreCase(fa) ? null : orElse(parseColor(fa), parent.fill());
        String sa = el.attr("stroke");
        if (sa != null) stroke = "none".equalsIgnoreCase(sa) ? null : orElse(parseColor(sa), parent.stroke());
        String swa = el.attr("stroke-width");
        if (swa != null) sw = parseLenPx(swa) > 0 ? parseLenPx(swa) : sw;

        // Minimal inline style="fill:..;stroke:..;stroke-width:.." override.
        String style = el.attr("style");
        if (style != null) {
            for (String decl : style.split(";")) {
                int colon = decl.indexOf(':');
                if (colon <= 0) continue;
                String k = decl.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String v = decl.substring(colon + 1).trim();
                switch (k) {
                    case "fill" -> fill = "none".equalsIgnoreCase(v) ? null : orElse(parseColor(v), fill);
                    case "stroke" -> stroke = "none".equalsIgnoreCase(v) ? null : orElse(parseColor(v), stroke);
                    case "stroke-width" -> { double w = parseLenPx(v); if (w > 0) sw = w; }
                    default -> { }
                }
            }
        }
        return new Paint(fill, stroke, sw);
    }

    private static Integer orElse(Integer a, Integer b) { return a != null ? a : b; }

    private static double numAttr(HtmlNode el, String name, double def) {
        return parseNum(el.attr(name), def);
    }

    private static double parseNum(String s, double def) {
        if (s == null) return def;
        s = s.trim();
        int i = 0;
        while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.' || s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
        if (i == 0) return def;
        try { return Double.parseDouble(s.substring(0, i)); } catch (NumberFormatException e) { return def; }
    }

    private static double parseLenPx(String s) {
        if (s == null) return 0;
        return parseNum(s.replace("px", "").trim(), 0);
    }

    private static double[] parseViewBox(String s) {
        if (s == null) return null;
        double[] v = nums(s);
        return v.length == 4 ? v : null;
    }

    private static double[] nums(String s) {
        if (s == null || s.isBlank()) return new double[0];
        String[] parts = s.trim().split("[\\s,]+");
        List<Double> list = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isEmpty()) continue;
            try { list.add(Double.parseDouble(part)); } catch (NumberFormatException ignored) { }
        }
        double[] out = new double[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }

    private static final Map<String, Integer> NAMED_COLORS = Map.ofEntries(
            Map.entry("black", 0x000000), Map.entry("white", 0xFFFFFF), Map.entry("red", 0xFF0000),
            Map.entry("green", 0x008000), Map.entry("blue", 0x0000FF), Map.entry("yellow", 0xFFFF00),
            Map.entry("gray", 0x808080), Map.entry("grey", 0x808080), Map.entry("silver", 0xC0C0C0),
            Map.entry("maroon", 0x800000), Map.entry("olive", 0x808000), Map.entry("lime", 0x00FF00),
            Map.entry("aqua", 0x00FFFF), Map.entry("teal", 0x008080), Map.entry("navy", 0x000080),
            Map.entry("fuchsia", 0xFF00FF), Map.entry("purple", 0x800080), Map.entry("orange", 0xFFA500));

    private static Integer parseColor(String s) {
        if (s == null) return null;
        s = s.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.equals("none") || s.equals("transparent") || s.equals("currentcolor")) return null;
        if (s.startsWith("#")) {
            String h = s.substring(1);
            try {
                if (h.length() == 3) {
                    int r = Integer.parseInt(h.substring(0, 1), 16);
                    int g = Integer.parseInt(h.substring(1, 2), 16);
                    int b = Integer.parseInt(h.substring(2, 3), 16);
                    return (r * 17 << 16) | (g * 17 << 8) | (b * 17);
                }
                if (h.length() == 6) return Integer.parseInt(h, 16);
            } catch (NumberFormatException e) { return null; }
            return null;
        }
        if (s.startsWith("rgb(") && s.endsWith(")")) {
            double[] c = nums(s.substring(4, s.length() - 1).replace("%", ""));
            if (c.length >= 3) {
                int r = clamp255((int) Math.round(c[0]));
                int g = clamp255((int) Math.round(c[1]));
                int b = clamp255((int) Math.round(c[2]));
                return (r << 16) | (g << 8) | b;
            }
            return null;
        }
        return NAMED_COLORS.get(s);
    }

    private static int clamp255(int v) { return Math.max(0, Math.min(255, v)); }
}
