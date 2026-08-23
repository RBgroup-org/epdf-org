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
package com.epdfengine.rb.org.css;

import com.epdfengine.rb.org.common.Edges;
import com.epdfengine.rb.org.common.Length;
import com.epdfengine.rb.org.css.CssParser.Rule;
import com.epdfengine.rb.org.html.HtmlNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves an element's {@link ComputedStyle} by cascading: inherited values from
 * the parent, then user-agent defaults for the tag, then matched author rules
 * (sorted by specificity then source order), then the inline {@code style}
 * attribute. Maps the common CSS properties onto {@code ComputedStyle}.
 */
public final class StyleResolver {

    private static final Set<String> INLINE_TAGS = Set.of(
            "a", "span", "b", "i", "em", "strong", "small", "code", "label",
            "sub", "sup", "abbr", "cite", "q", "mark", "u", "s", "big", "tt");

    private final List<Rule> rules;

    public StyleResolver(List<Rule> rules) {
        this.rules = (rules != null) ? rules : List.of();
    }

    public ComputedStyle resolve(HtmlNode element, ComputedStyle parent) {
        ComputedStyle cs = (parent != null) ? parent.inheritedCopy() : new ComputedStyle();
        applyUserAgentDefaults(element.tagName(), cs);

        List<Rule> matched = new ArrayList<>();
        for (Rule r : rules) if (r.selector().matches(element)) matched.add(r);
        matched.sort((a, b) -> {
            int s = Integer.compare(a.selector().specificity(), b.selector().specificity());
            return s != 0 ? s : Integer.compare(a.order(), b.order());
        });
        for (Rule r : matched) applyDeclarations(cs, r.declarations());

        String inline = element.attr("style");
        if (inline != null) applyDeclarations(cs, CssParser.parseInline(inline));
        return cs;
    }

    // --- user-agent defaults ---

    private static void applyUserAgentDefaults(String tag, ComputedStyle cs) {
        cs.display(INLINE_TAGS.contains(tag) ? Display.INLINE : Display.BLOCK);
        double base = cs.fontSizePt();
        switch (tag) {
            case "h1" -> heading(cs, base * 2.00);
            case "h2" -> heading(cs, base * 1.50);
            case "h3" -> heading(cs, base * 1.17);
            case "h4" -> heading(cs, base * 1.00);
            case "h5" -> heading(cs, base * 0.83);
            case "h6" -> heading(cs, base * 0.75);
            case "p", "figure" -> cs.margin(Edges.symmetric(base, 0));
            case "blockquote" -> cs.margin(Edges.of(base, base * 2.7, base, base * 2.7));
            case "dd" -> cs.margin(Edges.of(0, 0, 0, base * 2.7));
            case "b", "strong" -> cs.bold(true);
            case "i", "em" -> cs.italic(true);
            case "small" -> cs.fontSizePt(base * 0.83);
            case "a" -> cs.colorRgb(0x0645AD).textDecoration(TextDecoration.UNDERLINE);
            case "u", "ins" -> cs.textDecoration(TextDecoration.UNDERLINE);
            case "s", "del", "strike" -> cs.textDecoration(TextDecoration.LINE_THROUGH);
            case "ul" -> cs.margin(Edges.symmetric(base, 0)).padding(Edges.of(0, 0, 0, base * 2)).listStyleType(ListStyleType.DISC);
            case "ol" -> cs.margin(Edges.symmetric(base, 0)).padding(Edges.of(0, 0, 0, base * 2)).listStyleType(ListStyleType.DECIMAL);
            case "pre" -> cs.whiteSpace(WhiteSpace.PRE);
            case "code", "kbd", "samp", "tt" -> { /* monospace mapping needs a registered font */ }
            case "hr" -> cs.margin(Edges.symmetric(base / 2, 0)).border(Edges.of(0.5, 0, 0, 0)).borderColorRgb(0x999999);
            case "table" -> cs.display(Display.TABLE);
            case "thead", "tbody", "tfoot" -> cs.display(Display.TABLE_ROW_GROUP);
            case "tr" -> cs.display(Display.TABLE_ROW);
            case "td" -> cs.display(Display.TABLE_CELL).verticalAlign(VerticalAlign.TOP);
            case "th" -> cs.display(Display.TABLE_CELL).verticalAlign(VerticalAlign.TOP).bold(true).textAlign(TextAlign.CENTER);
            default -> { }
        }
    }

    private static void heading(ComputedStyle cs, double sizePt) {
        cs.fontSizePt(sizePt).bold(true).margin(Edges.symmetric(sizePt * 0.4, 0));
    }

    // --- declaration application ---

    private void applyDeclarations(ComputedStyle cs, Map<String, String> d) {
        String fs = d.get("font-size");
        if (fs != null) {
            double pt = lengthToPt(fs, cs.fontSizePt());
            if (!Double.isNaN(pt) && pt > 0) cs.fontSizePt(pt);
        }
        for (Map.Entry<String, String> e : d.entrySet()) {
            apply(cs, e.getKey(), e.getValue());
        }
    }

    private void apply(ComputedStyle cs, String k, String v) {
        double em = cs.fontSizePt();
        switch (k) {
            case "display" -> { Display disp = display(v); if (disp != null) cs.display(disp); }
            case "color" -> { Integer c = CssColor.parse(v); if (c != null) cs.colorRgb(c); }
            case "accent-color" -> { Integer c = CssColor.parse(v); if (c != null) cs.accentColorRgb(c); }
            case "background-color" -> {
                Integer argb = CssColor.parseArgb(v.trim());
                if (argb != null) { cs.backgroundRgb(argb & 0xFFFFFF); cs.backgroundAlpha(CssColor.alphaOf(argb)); }
            }
            case "background" -> applyBackground(cs, v);
            case "background-image" -> {
                Gradient g = parseAnyGradient(v);
                if (g != null) cs.backgroundGradient(g);
            }
            case "backdrop-filter", "-webkit-backdrop-filter" -> {
                double b = blurAmountPt(v, em);
                if (b > 0) cs.backdropBlurPt(b);
            }
            case "font-weight" -> cs.bold(isBold(v));
            case "font-style" -> cs.italic(v.contains("italic") || v.contains("oblique"));
            case "font-family" -> cs.fontFamily(firstFamily(v));
            case "font" -> applyFontShorthand(cs, v);
            case "text-align" -> { TextAlign ta = textAlign(v); if (ta != null) cs.textAlign(ta); }
            case "white-space" -> cs.whiteSpace(WhiteSpace.parse(v));
            case "list-style-type" -> cs.listStyleType(ListStyleType.parse(v));
            case "list-style" -> cs.listStyleType(listStyleFromShorthand(v));
            case "vertical-align" -> cs.verticalAlign(VerticalAlign.parse(v));
            case "border-collapse" -> cs.borderCollapse(v.trim().equalsIgnoreCase("collapse"));
            case "border-spacing" -> cs.borderSpacingPt(Math.max(0, pt(v, em)));
            case "flex-direction" -> cs.flexDirection(FlexDirection.parse(v));
            case "flex-wrap" -> cs.flexWrap(!v.trim().equalsIgnoreCase("nowrap"));
            case "justify-content" -> cs.justifyContent(JustifyContent.parse(v));
            case "align-items" -> cs.alignItems(AlignItems.parse(v));
            case "flex-grow" -> cs.flexGrow(Math.max(0, parseDoubleSafe(v, 0)));
            case "flex-shrink" -> cs.flexShrink(Math.max(0, parseDoubleSafe(v, 1)));
            case "flex-basis" -> cs.flexBasis(lengthValue(v));
            case "flex" -> applyFlexShorthand(cs, v);
            case "gap" -> applyGap(cs, v, em);
            case "column-gap" -> cs.columnGapPt(Math.max(0, pt(v, em)));
            case "row-gap" -> cs.rowGapPt(Math.max(0, pt(v, em)));
            case "column-count" -> cs.columnCount(firstInt(v, 1));
            case "columns" -> { int n = firstInt(v, 0); if (n > 0) cs.columnCount(n); }
            case "grid-template-columns" -> cs.gridTemplateColumns(v.trim());
            case "grid-template-rows" -> cs.gridTemplateRows(v.trim());
            case "grid-auto-rows" -> cs.gridAutoRows(v.trim());
            case "grid-template-areas" -> cs.gridTemplateAreas(v.trim());
            case "grid-column" -> cs.gridColumnSpan(parseGridColumnSpan(v));
            case "grid-row" -> cs.gridRowSpan(parseGridColumnSpan(v));
            case "grid-area" -> applyGridArea(cs, v);
            case "grid-auto-flow" -> {
                String gf = v.toLowerCase(Locale.ROOT);
                cs.gridAutoFlowDense(gf.contains("dense"));
                cs.gridAutoFlowColumn(gf.contains("column"));
            }
            case "align-self" -> cs.alignSelf(v.trim().equalsIgnoreCase("auto") ? null : AlignItems.parse(v));
            case "line-height" -> cs.lineHeightFactor(lineHeightFactor(v, em));
            case "box-sizing" -> cs.borderBoxSizing(v.contains("border-box"));
            case "width" -> cs.width(lengthValue(v));
            case "height" -> cs.height(lengthValue(v));
            case "min-width" -> cs.minWidth(lengthValue(v));
            case "max-width" -> cs.maxWidth(lengthValue(v));
            case "margin" -> cs.margin(edges(v, em));
            case "margin-top" -> cs.margin(withTop(cs.margin(), pt(v, em)));
            case "margin-right" -> cs.margin(withRight(cs.margin(), pt(v, em)));
            case "margin-bottom" -> cs.margin(withBottom(cs.margin(), pt(v, em)));
            case "margin-left" -> cs.margin(withLeft(cs.margin(), pt(v, em)));
            case "padding" -> cs.padding(edges(v, em));
            case "padding-top" -> cs.padding(withTop(cs.padding(), pt(v, em)));
            case "padding-right" -> cs.padding(withRight(cs.padding(), pt(v, em)));
            case "padding-bottom" -> cs.padding(withBottom(cs.padding(), pt(v, em)));
            case "padding-left" -> cs.padding(withLeft(cs.padding(), pt(v, em)));
            case "border" -> applyBorder(cs, v, em, 0);
            case "border-top" -> applyBorder(cs, v, em, 1);
            case "border-right" -> applyBorder(cs, v, em, 2);
            case "border-bottom" -> applyBorder(cs, v, em, 3);
            case "border-left" -> applyBorder(cs, v, em, 4);
            case "border-width" -> cs.border(Edges.of(Math.max(0, pt(v, em))));
            case "border-color" -> { Integer c = CssColor.parse(v); if (c != null) cs.borderColorRgb(c); }
            case "border-top-color" -> { Integer c = CssColor.parse(v); if (c != null) cs.borderColorTop(c); }
            case "border-right-color" -> { Integer c = CssColor.parse(v); if (c != null) cs.borderColorRight(c); }
            case "border-bottom-color" -> { Integer c = CssColor.parse(v); if (c != null) cs.borderColorBottom(c); }
            case "border-left-color" -> { Integer c = CssColor.parse(v); if (c != null) cs.borderColorLeft(c); }
            case "border-style", "border-top-style", "border-right-style", "border-bottom-style", "border-left-style"
                    -> cs.borderStyle(BorderStyle.parse(firstToken(v)));
            case "border-radius" -> applyBorderRadius(cs, v, em);
            case "page-break-before", "break-before" -> cs.pageBreakBefore(isForcedBreak(v));
            case "text-decoration", "text-decoration-line" -> cs.textDecoration(TextDecoration.parse(v));
            case "letter-spacing" -> cs.letterSpacingPt(v.trim().equalsIgnoreCase("normal") ? 0 : pt(v, em));
            case "box-shadow" -> cs.boxShadow(Shadow.parse(v, em));
            case "animation-name" -> cs.animationName(v.trim());
            case "animation-duration" -> cs.animationDurationMs(parseTimeMs(v));
            case "animation" -> applyAnimationShorthand(cs, v);
            default -> { }
        }
    }

    private static String firstToken(String v) {
        String t = v.trim();
        int sp = t.indexOf(' ');
        return sp < 0 ? t : t.substring(0, sp);
    }

    /** Parses border-radius (1-4 values, optional {@code /} vertical radii) into TL/TR/BR/BL. */
    private void applyBorderRadius(ComputedStyle cs, String v, double em) {
        String h = v.contains("/") ? v.substring(0, v.indexOf('/')).trim() : v.trim();
        String[] parts = h.split("\\s+");
        double[] q = new double[parts.length];
        for (int i = 0; i < parts.length; i++) q[i] = Math.max(0, pt(parts[i], em));
        double tl, tr, br, bl;
        switch (q.length) {
            case 1 -> { tl = tr = br = bl = q[0]; }
            case 2 -> { tl = br = q[0]; tr = bl = q[1]; }
            case 3 -> { tl = q[0]; tr = bl = q[1]; br = q[2]; }
            default -> { tl = q[0]; tr = q[1]; br = q[2]; bl = q[3]; }
        }
        cs.borderRadii(tl, tr, br, bl);
    }

    private static void applyFlexShorthand(ComputedStyle cs, String v) {
        String s = v.trim().toLowerCase(Locale.ROOT);
        if (s.equals("none")) { cs.flexGrow(0).flexShrink(0).flexBasis(Length.AUTO); return; }
        if (s.equals("auto")) { cs.flexGrow(1).flexShrink(1).flexBasis(Length.AUTO); return; }
        if (s.equals("initial")) { cs.flexGrow(0).flexShrink(1).flexBasis(Length.AUTO); return; }
        double grow = 0, shrink = 1;
        Length basis = Length.AUTO;
        boolean basisSet = false;
        int numIdx = 0;
        for (String tok : s.split("\\s+")) {
            if (tok.isEmpty()) continue;
            if (isNumberOnly(tok)) {
                double n = parseDoubleSafe(tok, 0);
                if (numIdx == 0) grow = n;
                else if (numIdx == 1) shrink = n;
                numIdx++;
            } else {
                basis = lengthValue(tok);
                basisSet = true;
            }
        }
        if (!basisSet && numIdx >= 1) basis = Length.px(0);   // "flex: 1" => basis 0
        cs.flexGrow(Math.max(0, grow)).flexShrink(Math.max(0, shrink)).flexBasis(basis);
    }

    private void applyGap(ComputedStyle cs, String v, double emPt) {
        String[] parts = v.trim().split("\\s+");
        double row = pt(parts[0], emPt);
        double col = parts.length > 1 ? pt(parts[1], emPt) : row;
        cs.rowGapPt(Math.max(0, row)).columnGapPt(Math.max(0, col));
    }

    /** Parses grid-column into a column span: {@code span N}, {@code a / b}, {@code a / span N}. */
    private static int parseGridColumnSpan(String v) {
        String s = v.trim().toLowerCase(Locale.ROOT);
        if (s.contains("/")) {
            String[] parts = s.split("/");
            String a = parts[0].trim();
            String b = parts.length > 1 ? parts[1].trim() : "";
            if (b.startsWith("span")) return Math.max(1, intOr(b.substring(4), 1));
            Integer ai = intOrNull(a), bi = intOrNull(b);
            if (ai != null && bi != null) return Math.max(1, bi - ai);
            return 1;
        }
        if (s.startsWith("span")) return Math.max(1, intOr(s.substring(4), 1));
        return 1;
    }

    private static int intOr(String s, int def) {
        Double n = leadingNumber(s.trim());
        return n != null ? (int) Math.round(n) : def;
    }

    /** First bare integer in a value (e.g. the count in {@code columns: 200px 3}), else {@code def}. */
    private static int firstInt(String v, int def) {
        if (v == null) return def;
        for (String tok : v.trim().split("[\\s,]+")) {
            if (tok.matches("\\d+")) return Integer.parseInt(tok);
        }
        return def;
    }

    private static Integer intOrNull(String s) {
        Double n = leadingNumber(s.trim());
        return n != null ? (int) Math.round(n) : null;
    }

    /** Parses grid-area: a named area, or {@code r-start / c-start / r-end / c-end} line placement. */
    private static void applyGridArea(ComputedStyle cs, String v) {
        String s = v.trim();
        if (s.contains("/")) {
            String[] p = s.split("/");
            int rowSpan = spanFromLines(p.length > 0 ? p[0] : "", p.length > 2 ? p[2] : "");
            int colSpan = spanFromLines(p.length > 1 ? p[1] : "", p.length > 3 ? p[3] : "");
            if (rowSpan > 1) cs.gridRowSpan(rowSpan);
            if (colSpan > 1) cs.gridColumnSpan(colSpan);
        } else if (!s.isEmpty()) {
            cs.gridArea(s.toLowerCase(Locale.ROOT));
        }
    }

    private static int spanFromLines(String start, String end) {
        String e = end.trim().toLowerCase(Locale.ROOT);
        if (e.startsWith("span")) return Math.max(1, intOr(e.substring(4), 1));
        Integer a = intOrNull(start), b = intOrNull(end);
        if (a != null && b != null) return Math.max(1, b - a);
        return 1;
    }

    private static boolean isNumberOnly(String s) {
        return s.matches("[-+]?\\d*\\.?\\d+");
    }

    private static double parseDoubleSafe(String v, double def) {
        Double n = leadingNumber(v.trim());
        return n != null ? n : def;
    }

    private static ListStyleType listStyleFromShorthand(String v) {
        for (String tok : v.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            switch (tok) {
                case "inside", "outside", "" -> { }
                default -> {
                    if (!tok.startsWith("url(")) return ListStyleType.parse(tok);
                }
            }
        }
        return ListStyleType.DISC;
    }

    private static void applyBackground(ComputedStyle cs, String v) {
        Gradient g = parseAnyGradient(v);
        if (g != null) { cs.backgroundGradient(g); return; }
        Integer argb = CssColor.parseArgb(v.trim());
        if (argb != null) { cs.backgroundRgb(argb & 0xFFFFFF); cs.backgroundAlpha(CssColor.alphaOf(argb)); return; }
        Integer c = firstColor(v);
        if (c != null) cs.backgroundRgb(c);
    }

    /** A forced page break (page-break-before/break-before: always|page|left|right). */
    private static boolean isForcedBreak(String v) {
        String s = v.trim().toLowerCase(Locale.ROOT);
        return s.equals("always") || s.equals("page") || s.equals("left")
                || s.equals("right") || s.equals("recto") || s.equals("verso");
    }

    /** Extracts the {@code blur(<len>)} radius (in pt) from a {@code backdrop-filter} value, else 0. */
    private static double blurAmountPt(String v, double emPt) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("blur\\(\\s*([0-9.]+)\\s*(px|pt|em|rem)?\\s*\\)")
                .matcher(v.toLowerCase(Locale.ROOT));
        if (!m.find()) return 0;
        double n;
        try { n = Double.parseDouble(m.group(1)); } catch (NumberFormatException e) { return 0; }
        String u = (m.group(2) == null) ? "px" : m.group(2);
        return switch (u) {
            case "pt"        -> n;
            case "em", "rem" -> n * emPt;
            default          -> n * 0.75;   // px -> pt
        };
    }

    private static Gradient parseAnyGradient(String v) {
        String low = v.toLowerCase(Locale.ROOT);
        if (low.contains("linear-gradient")) {
            Gradient g = Gradient.parseLinear(extractFunc(v, "linear-gradient("));
            if (g != null) return g;
        }
        if (low.contains("radial-gradient")) {
            Gradient g = Gradient.parseRadial(extractFunc(v, "radial-gradient("));
            if (g != null) return g;
        }
        if (low.contains("conic-gradient")) {
            Gradient g = Gradient.parseConic(extractFunc(v, "conic-gradient("));
            if (g != null) return g;
        }
        return null;
    }

    private static String extractFunc(String v, String name) {
        int i = v.toLowerCase(Locale.ROOT).indexOf(name);
        if (i < 0) return v;
        int depth = 0, j = i;
        for (; j < v.length(); j++) {
            char c = v.charAt(j);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) { j++; break; }
        }
        return v.substring(i, j);
    }

    private static final java.util.Set<String> ANIM_KEYWORDS = java.util.Set.of(
            "infinite", "linear", "ease", "ease-in", "ease-out", "ease-in-out", "step-start", "step-end",
            "alternate", "alternate-reverse", "reverse", "normal", "forwards", "backwards", "both",
            "none", "running", "paused");

    /** {@code animation: <name> <duration> ...} — pulls out the name and the first time value. */
    private static void applyAnimationShorthand(ComputedStyle cs, String v) {
        String name = null;
        double ms = 0;
        for (String tok : v.trim().split("\\s+")) {
            String t = tok.trim();
            if (t.isEmpty()) continue;
            String low = t.toLowerCase(Locale.ROOT);
            if (low.endsWith("ms") || low.endsWith("s")) { if (ms == 0) ms = parseTimeMs(t); continue; }
            if (ANIM_KEYWORDS.contains(low) || low.matches("[-+]?\\d*\\.?\\d+")) continue;
            if (low.startsWith("cubic-bezier") || low.startsWith("steps")) continue;
            if (name == null) name = t;
        }
        if (name != null) cs.animationName(name);
        if (ms > 0) cs.animationDurationMs(ms);
    }

    private static double parseTimeMs(String v) {
        String t = v.trim().toLowerCase(Locale.ROOT);
        try {
            if (t.endsWith("ms")) return Double.parseDouble(t.substring(0, t.length() - 2).trim());
            if (t.endsWith("s")) return Double.parseDouble(t.substring(0, t.length() - 1).trim()) * 1000.0;
        } catch (NumberFormatException ignored) { }
        return 0;
    }

    private void applyBorder(ComputedStyle cs, String v, double em, int side) {
        double width = Double.NaN;
        Integer color = null;
        BorderStyle style = null;
        for (String token : v.trim().split("\\s+")) {
            switch (token.toLowerCase(Locale.ROOT)) {
                case "solid" -> style = BorderStyle.SOLID;
                case "dashed" -> style = BorderStyle.DASHED;
                case "dotted" -> style = BorderStyle.DOTTED;
                case "none", "hidden" -> style = BorderStyle.NONE;
                default -> {
                    double w = lengthToPt(token, em);
                    if (!Double.isNaN(w)) { width = w; }
                    else { Integer c = CssColor.parse(token); if (c != null) color = c; }
                }
            }
        }
        if (Double.isNaN(width)) width = (style == BorderStyle.NONE) ? 0.0 : 1.0;
        Edges b = cs.border();
        Edges nb = switch (side) {
            case 1 -> withTop(b, width);
            case 2 -> withRight(b, width);
            case 3 -> withBottom(b, width);
            case 4 -> withLeft(b, width);
            default -> Edges.of(width);
        };
        cs.border(nb);
        if (color != null) {
            switch (side) {
                case 1 -> cs.borderColorTop(color);
                case 2 -> cs.borderColorRight(color);
                case 3 -> cs.borderColorBottom(color);
                case 4 -> cs.borderColorLeft(color);
                default -> cs.borderColorRgb(color);
            }
        }
        if (style != null) cs.borderStyle(style);
    }

    private void applyFontShorthand(ComputedStyle cs, String v) {
        String lower = v.toLowerCase(Locale.ROOT);
        if (lower.contains("bold")) cs.bold(true);
        if (lower.contains("italic") || lower.contains("oblique")) cs.italic(true);
        for (String token : v.split("\\s+")) {
            double pt = lengthToPt(token.split("/")[0], cs.fontSizePt());
            if (!Double.isNaN(pt) && pt > 0) { cs.fontSizePt(pt); break; }
        }
        int comma = v.indexOf(',');
        int lastSpace = v.lastIndexOf(' ', comma < 0 ? v.length() - 1 : comma);
        if (lastSpace >= 0 && lastSpace + 1 < v.length()) {
            cs.fontFamily(firstFamily(v.substring(lastSpace + 1)));
        }
    }

    // --- value parsing helpers ---

    private static Display display(String v) {
        return switch (v.trim().toLowerCase(Locale.ROOT)) {
            case "block" -> Display.BLOCK;
            case "inline" -> Display.INLINE;
            case "inline-block" -> Display.INLINE_BLOCK;
            case "none" -> Display.NONE;
            case "flex" -> Display.FLEX;
            case "inline-flex" -> Display.FLEX;
            case "grid" -> Display.GRID;
            case "inline-grid" -> Display.GRID;
            case "table" -> Display.TABLE;
            case "table-row-group", "table-header-group", "table-footer-group" -> Display.TABLE_ROW_GROUP;
            case "table-row" -> Display.TABLE_ROW;
            case "table-cell" -> Display.TABLE_CELL;
            case "list-item" -> Display.LIST_ITEM;
            default -> null;
        };
    }

    private static TextAlign textAlign(String v) {
        return switch (v.trim().toLowerCase(Locale.ROOT)) {
            case "left" -> TextAlign.LEFT;
            case "right" -> TextAlign.RIGHT;
            case "center" -> TextAlign.CENTER;
            case "justify" -> TextAlign.JUSTIFY;
            case "start" -> TextAlign.START;
            case "end" -> TextAlign.END;
            default -> null;
        };
    }

    private static boolean isBold(String v) {
        String s = v.trim().toLowerCase(Locale.ROOT);
        if (s.equals("bold") || s.equals("bolder")) return true;
        try { return Integer.parseInt(s) >= 600; } catch (NumberFormatException e) { return false; }
    }

    private static Integer firstColor(String v) {
        for (String token : v.trim().split("\\s+")) {
            Integer c = CssColor.parse(token);
            if (c != null) return c;
        }
        return CssColor.parse(v);
    }

    private static String firstFamily(String v) {
        String first = v.split(",")[0].trim();
        if (first.length() >= 2 && (first.charAt(0) == '"' || first.charAt(0) == '\'')) {
            first = first.substring(1, first.length() - 1);
        }
        return first.isEmpty() ? null : first;
    }

    private static double lineHeightFactor(String v, double emPt) {
        String s = v.trim().toLowerCase(Locale.ROOT);
        Double num = leadingNumber(s);
        if (num != null && (s.equals(num.toString()) || isUnitless(s))) return num;
        double pt = lengthToPt(s, emPt);
        return (!Double.isNaN(pt) && emPt > 0) ? pt / emPt : 1.2;
    }

    private static boolean isUnitless(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isDigit(c) && c != '.' && c != '-' && c != '+') return false;
        }
        return !s.isEmpty();
    }

    private static Length lengthValue(String v) {
        String s = v.trim().toLowerCase(Locale.ROOT);
        if (s.equals("auto") || s.isEmpty()) return Length.AUTO;
        Double num = leadingNumber(s);
        if (num == null) return Length.AUTO;
        if (s.endsWith("%")) return Length.percent(num);
        if (s.endsWith("px")) return Length.px(num);
        if (s.endsWith("pt")) return Length.pt(num);
        if (s.endsWith("mm")) return Length.mm(num);
        if (s.endsWith("cm")) return Length.cm(num);
        if (s.endsWith("in")) return Length.inches(num);
        if (s.endsWith("em") || s.endsWith("rem")) return Length.em(num);
        return Length.px(num);
    }

    private static Edges edges(String v, double emPt) {
        String[] parts = v.trim().split("\\s+");
        double[] q = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            double p = lengthToPt(parts[i], emPt);
            q[i] = Double.isNaN(p) ? 0 : p;
        }
        return switch (q.length) {
            case 1 -> Edges.of(q[0]);
            case 2 -> Edges.symmetric(q[0], q[1]);
            case 3 -> Edges.of(q[0], q[1], q[2], q[1]);
            default -> Edges.of(q[0], q[1], q[2], q[3]);
        };
    }

    private static double pt(String v, double emPt) {
        double p = lengthToPt(v, emPt);
        return Double.isNaN(p) ? 0 : p;
    }

    /** Converts a CSS length to points; returns {@code NaN} for percentages/invalid. */
    private static double lengthToPt(String value, double emPt) {
        if (value == null) return Double.NaN;
        String s = value.trim().toLowerCase(Locale.ROOT);
        Double num = leadingNumber(s);
        if (num == null) return Double.NaN;
        double v = num;
        if (s.endsWith("px")) return v * 0.75;
        if (s.endsWith("pt")) return v;
        if (s.endsWith("pc")) return v * 12.0;
        if (s.endsWith("in")) return v * 72.0;
        if (s.endsWith("cm")) return v * 72.0 / 2.54;
        if (s.endsWith("mm")) return v * 72.0 / 25.4;
        if (s.endsWith("rem")) return v * emPt;
        if (s.endsWith("em")) return v * emPt;
        if (s.endsWith("%")) return Double.NaN;
        return v == 0 ? 0 : v * 0.75;   // unitless: treat as px (0 stays 0)
    }

    private static Double leadingNumber(String s) {
        int i = 0, n = s.length();
        if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
        int start = i;
        while (i < n && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
        if (i == start) return null;
        try { return Double.parseDouble(s.substring(0, i)); } catch (NumberFormatException e) { return null; }
    }

    private static Edges withTop(Edges e, double v)    { return new Edges(v, e.right(), e.bottom(), e.left()); }
    private static Edges withRight(Edges e, double v)  { return new Edges(e.top(), v, e.bottom(), e.left()); }
    private static Edges withBottom(Edges e, double v) { return new Edges(e.top(), e.right(), v, e.left()); }
    private static Edges withLeft(Edges e, double v)   { return new Edges(e.top(), e.right(), e.bottom(), v); }
}
