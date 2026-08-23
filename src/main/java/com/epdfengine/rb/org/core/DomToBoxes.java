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
package com.epdfengine.rb.org.core;

import com.epdfengine.rb.org.css.ComputedStyle;
import com.epdfengine.rb.org.css.Display;
import com.epdfengine.rb.org.css.ListStyleType;
import com.epdfengine.rb.org.css.StyleResolver;
import com.epdfengine.rb.org.html.HtmlNode;
import com.epdfengine.rb.org.html.HtmlParser;
import com.epdfengine.rb.org.io.image.DecodedImage;
import com.epdfengine.rb.org.io.image.ImageDecoder;
import com.epdfengine.rb.org.layout.StructNode;
import com.epdfengine.rb.org.layout.box.BlockBox;
import com.epdfengine.rb.org.layout.box.Box;
import com.epdfengine.rb.org.layout.box.BreakBox;
import com.epdfengine.rb.org.layout.box.FormFieldBox;
import com.epdfengine.rb.org.layout.box.ImageBox;
import com.epdfengine.rb.org.layout.box.InlineBox;
import com.epdfengine.rb.org.layout.box.ListItemBox;
import com.epdfengine.rb.org.layout.box.SvgBox;
import com.epdfengine.rb.org.layout.box.TableCellBox;
import com.epdfengine.rb.org.layout.box.TextBox;
import com.epdfengine.rb.org.svg.SvgParser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

/**
 * Converts a parsed HTML DOM plus a {@link StyleResolver} into the layout box tree.
 * Walks from {@code <body>} (or the document root if none), resolving each
 * element's style and emitting {@code BlockBox}/{@code InlineBox}/{@code TextBox},
 * inline {@code <svg>} as {@code SvgBox}. {@code display:none} subtrees are pruned.
 *
 * <p>First-pass scope: {@code <img>} loading and {@code <br>} are not yet wired;
 * inline vs. block is chosen from the resolved {@code display}.</p>
 */
public final class DomToBoxes {

    private static final Set<String> SKIP = Set.of(
            "head", "title", "meta", "link", "style", "script", "base", "noscript",
            "col", "colgroup");

    private final StyleResolver resolver;
    private final ResourceLoader resources;
    private int nextStructGroup = 0;              // unique tag-group id per source block (PDF/UA)
    private int autoFieldSeq = 0;                 // auto-name for unnamed form fields
    private java.util.Map<String, java.util.List<com.epdfengine.rb.org.css.CssParser.Keyframe>> keyframes = java.util.Map.of();
    private final java.util.List<StructNode> structTree = new java.util.ArrayList<>();

    public DomToBoxes(StyleResolver resolver) {
        this(resolver, null);
    }

    public DomToBoxes(StyleResolver resolver, ResourceLoader resources) {
        this.resolver = resolver;
        this.resources = resources;
    }

    /** Registers the document's {@code @keyframes} sets for CSS animation resolution. */
    public void setKeyframes(java.util.Map<String, java.util.List<com.epdfengine.rb.org.css.CssParser.Keyframe>> k) {
        this.keyframes = (k != null) ? k : java.util.Map.of();
    }

    public BlockBox build(HtmlNode document) {
        HtmlNode body = findBody(document);
        HtmlNode container = (body != null) ? body : document;
        ComputedStyle rootStyle = new ComputedStyle();
        BlockBox root = new BlockBox(rootStyle);
        buildChildren(container, rootStyle, root, -1);
        return root;
    }

    /** The logical-structure registry built during {@link #build}; empty until then. */
    public java.util.List<StructNode> structTree() { return structTree; }

    private void buildChildren(HtmlNode parent, ComputedStyle parentStyle, Box parentBox, int parentStructId) {
        for (HtmlNode c : parent.children()) {
            if (c.isText()) {
                String t = c.text();
                if (t != null && !t.isBlank()) parentBox.add(new TextBox(t, parentStyle));
                continue;
            }
            if (!c.isElement()) continue;
            String tag = c.tagName();
            if (SKIP.contains(tag)) continue;

            ComputedStyle cs = resolver.resolve(c, parentStyle);
            if (cs.display() == Display.NONE) continue;
            applyAnimation(cs);
            String elemId = c.attr("id");
            if (elemId != null && !elemId.isBlank()) cs.setAnchorId(elemId.trim());

            // PDF/UA: build a nested logical-structure node for each block-level element
            // (Table→TR→TD, L→LI→LBody, …). Inline elements inherit their block's tag group,
            // except <a href> which becomes an inline Link element carrying the URI.
            int childStructId = parentStructId;
            boolean blockLevel = cs.display() != Display.INLINE && cs.display() != Display.INLINE_BLOCK;
            String href = "a".equals(tag) ? c.attr("href") : null;
            boolean isLink = href != null && !href.isBlank();
            boolean structural = (blockLevel && !"br".equals(tag) && !"svg".equals(tag)) || isLink;
            if (structural) {
                String role = isLink ? "Link" : roleForTag(tag);
                int myId = nextStructGroup++;
                String alt = null;
                if ("Figure".equals(role)) {
                    alt = c.attr("alt");
                    if (alt == null || alt.isBlank()) alt = "image";   // PDF/UA: a Figure must have Alt
                }
                structTree.add(new StructNode(myId, role, parentStructId, alt, null));
                if ("LI".equals(role)) {
                    // A list item's content and any block children belong to an LBody child.
                    int lbody = nextStructGroup++;
                    structTree.add(new StructNode(lbody, "LBody", myId, null, null));
                    cs.setStructRole("LBody");
                    cs.setStructGroupId(lbody);
                    childStructId = lbody;
                } else {
                    if (isLink) cs.setLinkHref(href.trim());
                    cs.setStructRole(role);
                    cs.setStructGroupId(myId);
                    childStructId = myId;
                }
            }

            switch (tag) {
                case "br" -> parentBox.add(new BreakBox(cs));
                case "img" -> {
                    Box imgBox = loadImage(c.attr("src"), cs);
                    if (imgBox != null) {
                        cs.setStructAlt(c.attr("alt"));
                        parentBox.add(imgBox);
                    }
                }
                case "svg" -> parentBox.add(new SvgBox(SvgParser.parse(c), cs));
                case "li" -> {
                    ListStyleType type = cs.listStyleType();
                    String marker = type.isOrdinal() ? type.ordinal(liOrdinal(c)) : "";
                    ListItemBox li = new ListItemBox(cs, type, marker);
                    parentBox.add(li);
                    buildChildren(c, cs, li, childStructId);
                }
                case "td", "th" -> {
                    TableCellBox cell = new TableCellBox(cs, attrInt(c, "colspan", 1), attrInt(c, "rowspan", 1));
                    parentBox.add(cell);
                    buildChildren(c, cs, cell, childStructId);
                }
                case "input", "textarea", "select", "button" -> {
                    Box field = buildFormField(c, cs);
                    if (field != null) parentBox.add(field);
                }
                case "datalist" -> { /* not rendered; its options back an <input list=...> */ }
                default -> {
                    boolean inline = cs.display() == Display.INLINE || cs.display() == Display.INLINE_BLOCK;
                    Box box = inline ? new InlineBox(cs) : new BlockBox(cs);
                    parentBox.add(box);
                    buildChildren(c, cs, box, childStructId);
                }
            }
        }
    }

    /** Builds an AcroForm control box from {@code <input>}/{@code <textarea>}/{@code <select>}/{@code <button>}. */
    private Box buildFormField(HtmlNode c, ComputedStyle cs) {
        String tag = c.tagName();
        String name = c.attr("name");
        if (name == null || name.isBlank()) name = tag + "_" + (autoFieldSeq++);
        String placeholder = c.attr("placeholder");
        int maxLen = parseIntAttr(c.attr("maxlength"), -1);
        boolean readOnly = c.attributes().containsKey("readonly") || c.attributes().containsKey("disabled");
        boolean required = c.attributes().containsKey("required");
        Double min = parseDoubleAttr(c.attr("min"));
        Double max = parseDoubleAttr(c.attr("max"));
        Double step = parseDoubleAttr(c.attr("step"));
        String pattern = c.attr("pattern");
        boolean comb = c.attributes().containsKey("data-comb") && maxLen > 0;
        switch (tag) {
            case "textarea":
                return new FormFieldBox(FormFieldBox.Kind.TEXTAREA, name, c.textContent(), null, false, cs)
                        .modern(placeholder, maxLen, readOnly, required, false);
            case "button":
                String cap = c.textContent();
                return new FormFieldBox(FormFieldBox.Kind.BUTTON, name, cap.isBlank() ? "Button" : cap, null, false, cs);
            case "select": {
                java.util.List<String> opts = new java.util.ArrayList<>();
                String selected = "";
                for (HtmlNode o : c.children()) {
                    if (!"option".equals(o.tagName())) continue;
                    String ov = o.attr("value");
                    String v = ov != null ? ov : o.textContent();
                    opts.add(v);
                    if (o.attributes().containsKey("selected")) selected = v;
                }
                boolean multiple = c.attributes().containsKey("multiple");
                return new FormFieldBox(FormFieldBox.Kind.CHOICE, name, selected, opts.toArray(new String[0]), false, cs)
                        .modern(placeholder, -1, readOnly, required, multiple);
            }
            case "input": {
                String type = c.attr("type");
                type = (type == null ? "text" : type).toLowerCase();
                String value = c.attr("value");
                boolean checked = c.attributes().containsKey("checked");
                switch (type) {
                    case "checkbox": return new FormFieldBox(FormFieldBox.Kind.CHECKBOX, name, value, null, checked, cs);
                    case "radio":    return new FormFieldBox(FormFieldBox.Kind.RADIO, name, value == null ? "" : value, null, checked, cs);
                    case "password": return new FormFieldBox(FormFieldBox.Kind.PASSWORD, name, value, null, false, cs)
                            .modern(placeholder, maxLen, readOnly, required, false);
                    case "submit": case "button":
                        return new FormFieldBox(FormFieldBox.Kind.BUTTON, name, value == null ? "Submit" : value, null, false, cs);
                    case "hidden": return null;
                    default: {
                        // <input list="dl"> -> editable combo box backed by a <datalist>
                        String listId = c.attr("list");
                        if (listId != null && !listId.isBlank()) {
                            java.util.List<String> opts = datalistOptions(c, listId);
                            if (!opts.isEmpty()) {
                                return new FormFieldBox(FormFieldBox.Kind.CHOICE, name, value == null ? "" : value,
                                        opts.toArray(new String[0]), false, cs)
                                        .modern(placeholder, -1, readOnly, required, false)
                                        .advanced(null, null, null, null, false, true);
                            }
                        }
                        // text, email, tel, url, number, search, date, time, ... -> styled text field
                        return new FormFieldBox(FormFieldBox.Kind.TEXT, name, value, null, false, cs)
                                .modern(placeholder, maxLen, readOnly, required, false)
                                .advanced(min, max, step, pattern, comb, false);
                    }
                }
            }
            default: return null;
        }
    }

    private static int parseIntAttr(String v, int def) {
        if (v == null || v.isBlank()) return def;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static Double parseDoubleAttr(String v) {
        if (v == null || v.isBlank()) return null;
        try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return null; }
    }

    /** Returns the option values of the {@code <datalist id=listId>} in the same document. */
    private static java.util.List<String> datalistOptions(HtmlNode from, String listId) {
        HtmlNode root = from;
        while (root.parent() != null) root = root.parent();
        HtmlNode dl = findById(root, "datalist", listId);
        java.util.List<String> opts = new java.util.ArrayList<>();
        if (dl != null) {
            for (HtmlNode o : dl.children()) {
                if (!"option".equals(o.tagName())) continue;
                String ov = o.attr("value");
                opts.add(ov != null ? ov : o.textContent());
            }
        }
        return opts;
    }

    private static HtmlNode findById(HtmlNode node, String tag, String id) {
        if (node.isElement() && tag.equals(node.tagName()) && id.equals(node.attr("id"))) return node;
        for (HtmlNode ch : node.children()) {
            HtmlNode r = findById(ch, tag, id);
            if (r != null) return r;
        }
        return null;
    }

    private static final int ANIM_FRAMES = 8;

    /** Samples an element's {@code @keyframes} (background-color, opacity, transform) into OCG frames. */
    private void applyAnimation(ComputedStyle cs) {
        String name = cs.animationName();
        if (name == null || keyframes.isEmpty()) return;
        var kfs = keyframes.get(name);
        if (kfs == null || kfs.size() < 2) return;
        boolean animBg = false, animOpacity = false, animTransform = false;
        for (var k : kfs) {
            if (bgOf(k) != null) animBg = true;
            if (k.declarations().containsKey("opacity")) animOpacity = true;
            if (k.declarations().containsKey("transform")) animTransform = true;
        }
        if (!animBg && !animOpacity && !animTransform) return;
        int def = cs.backgroundRgb() != null ? cs.backgroundRgb() : 0xFFFFFF;
        com.epdfengine.rb.org.css.AnimFrame[] frames = new com.epdfengine.rb.org.css.AnimFrame[ANIM_FRAMES];
        for (int i = 0; i < ANIM_FRAMES; i++) {
            double t = (double) i / (ANIM_FRAMES - 1);
            int rgb = animBg ? sampleBg(kfs, t, def) : -1;
            double alpha = animOpacity ? sampleScalar(kfs, t, "opacity", 1.0) : 1.0;
            double[] tf = animTransform ? sampleTransform(kfs, t) : new double[]{ 0, 1, 0, 0 };
            frames[i] = new com.epdfengine.rb.org.css.AnimFrame(rgb, alpha, tf[0], tf[1], tf[2], tf[3]);
        }
        double ms = cs.animationDurationMs() > 0 ? cs.animationDurationMs() : 2000;
        if (animBg) cs.backgroundRgb(sampleBg(kfs, 0, def));   // static poster colour
        cs.animFrames(frames, ms);
    }

    private static Integer bgOf(com.epdfengine.rb.org.css.CssParser.Keyframe k) {
        String v = k.declarations().get("background-color");
        if (v == null) v = k.declarations().get("background");
        return v == null ? null : com.epdfengine.rb.org.css.CssColor.parse(v);
    }

    private static int sampleBg(java.util.List<com.epdfengine.rb.org.css.CssParser.Keyframe> kfs, double t, int def) {
        int prevRgb = def;
        double prevStop = 0;
        boolean havePrev = false;
        for (var k : kfs) {
            Integer rgb = bgOf(k);
            if (rgb == null) continue;
            if (!havePrev) {
                prevRgb = rgb; prevStop = k.stop(); havePrev = true;
                if (t <= k.stop()) return rgb;
                continue;
            }
            if (t <= k.stop()) {
                double span = k.stop() - prevStop;
                double f = span <= 0 ? 0 : (t - prevStop) / span;
                return lerpRgb(prevRgb, rgb, f);
            }
            prevRgb = rgb; prevStop = k.stop();
        }
        return prevRgb;
    }

    private static double sampleScalar(java.util.List<com.epdfengine.rb.org.css.CssParser.Keyframe> kfs,
                                       double t, String key, double def) {
        double prevV = def, prevStop = 0;
        boolean havePrev = false;
        for (var k : kfs) {
            String s = k.declarations().get(key);
            Double val = (s == null) ? null : numOr(s, Double.NaN);
            if (val == null || val.isNaN()) continue;
            if (!havePrev) {
                prevV = val; prevStop = k.stop(); havePrev = true;
                if (t <= k.stop()) return val;
                continue;
            }
            if (t <= k.stop()) {
                double span = k.stop() - prevStop;
                double f = span <= 0 ? 0 : (t - prevStop) / span;
                return prevV + (val - prevV) * f;
            }
            prevV = val; prevStop = k.stop();
        }
        return prevV;
    }

    private static double[] sampleTransform(java.util.List<com.epdfengine.rb.org.css.CssParser.Keyframe> kfs, double t) {
        double[] prev = { 0, 1, 0, 0 };
        double prevStop = 0;
        boolean havePrev = false;
        for (var k : kfs) {
            String s = k.declarations().get("transform");
            if (s == null) continue;
            double[] cur = parseTransform(s);
            if (!havePrev) {
                prev = cur; prevStop = k.stop(); havePrev = true;
                if (t <= k.stop()) return cur;
                continue;
            }
            if (t <= k.stop()) {
                double span = k.stop() - prevStop;
                double f = span <= 0 ? 0 : (t - prevStop) / span;
                return new double[]{ lerp(prev[0], cur[0], f), lerp(prev[1], cur[1], f),
                        lerp(prev[2], cur[2], f), lerp(prev[3], cur[3], f) };
            }
            prev = cur; prevStop = k.stop();
        }
        return prev;
    }

    /** {@code rotate()/scale()/translate()} -> {rotateDeg, scale, txPt, tyPt}. */
    private static double[] parseTransform(String s) {
        double rot = 0, scale = 1, tx = 0, ty = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(rotate|scalex|scaley|scale|translatex|translatey|translate)\\s*\\(([^)]*)\\)")
                .matcher(s.toLowerCase(java.util.Locale.ROOT));
        while (m.find()) {
            String fn = m.group(1);
            String[] args = m.group(2).split(",");
            switch (fn) {
                case "rotate" -> rot = degOf(args[0]);
                case "scale", "scalex", "scaley" -> scale = numOr(args[0], 1);
                case "translatex" -> tx = pxToPt(args[0]);
                case "translatey" -> ty = pxToPt(args[0]);
                case "translate" -> { tx = pxToPt(args[0]); if (args.length > 1) ty = pxToPt(args[1]); }
            }
        }
        return new double[]{ rot, scale, tx, ty };
    }

    private static double lerp(double a, double b, double f) { return a + (b - a) * f; }

    private static double degOf(String s) {
        s = s.trim();
        if (s.endsWith("deg")) s = s.substring(0, s.length() - 3);
        return numOr(s, 0);
    }

    private static double pxToPt(String s) {
        s = s.trim();
        double mult = 1;
        if (s.endsWith("px")) { s = s.substring(0, s.length() - 2); mult = 0.75; }
        else if (s.endsWith("pt")) { s = s.substring(0, s.length() - 2); }
        else if (s.endsWith("%")) { return 0; }
        return numOr(s, 0) * mult;
    }

    private static double numOr(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static int lerpRgb(int a, int b, double f) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) Math.round(ar + (br - ar) * f);
        int g = (int) Math.round(ag + (bg - ag) * f);
        int bl = (int) Math.round(ab + (bb - ab) * f);
        return (r << 16) | (g << 8) | bl;
    }

    /** Maps an HTML tag to a standard PDF structure role (nested Table/List roles included). */
    private static String roleForTag(String tag) {
        if (tag == null) return "P";
        if (tag.length() == 2 && tag.charAt(0) == 'h' && tag.charAt(1) >= '1' && tag.charAt(1) <= '6') {
            return "H" + tag.charAt(1);
        }
        return switch (tag) {
            case "p" -> "P";
            case "ul", "ol", "menu", "dl" -> "L";
            case "li", "dt", "dd" -> "LI";
            case "table" -> "Table";
            case "thead" -> "THead";
            case "tbody" -> "TBody";
            case "tfoot" -> "TFoot";
            case "tr" -> "TR";
            case "td" -> "TD";
            case "th" -> "TH";
            case "caption", "figcaption" -> "Caption";
            case "figure", "img" -> "Figure";
            case "blockquote" -> "BlockQuote";
            case "a" -> "Link";
            default -> "Div";                        // generic grouping container
        };
    }

    /** Loads an {@code <img src>} into an {@link ImageBox} (raster) or {@link SvgBox} (SVG). */
    private Box loadImage(String src, ComputedStyle cs) {        if (src == null || src.isBlank()) return null;
        String s = src.trim();
        try {
            byte[] bytes;
            if (s.startsWith("data:")) {
                int comma = s.indexOf(',');
                if (comma < 0) return null;
                String meta = s.substring(5, comma);
                String payload = s.substring(comma + 1);
                bytes = meta.contains("base64")
                        ? Base64.getDecoder().decode(payload)
                        : payload.getBytes(StandardCharsets.ISO_8859_1);
                if (meta.contains("svg")) return svgFromBytes(bytes, cs);
            } else if (resources != null) {
                bytes = resources.load(s);
            } else {
                return null;
            }
            if (bytes == null || bytes.length == 0) return null;
            if (looksLikeSvg(s, bytes)) return svgFromBytes(bytes, cs);
            DecodedImage img = ImageDecoder.decode(bytes);
            return new ImageBox(img, cs);
        } catch (RuntimeException e) {
            return null;                                 // unsupported/broken image: skip gracefully
        }
    }

    private static boolean looksLikeSvg(String src, byte[] bytes) {
        if (src.toLowerCase().endsWith(".svg")) return true;
        int n = Math.min(bytes.length, 256);
        return new String(bytes, 0, n, StandardCharsets.US_ASCII).toLowerCase().contains("<svg");
    }

    private Box svgFromBytes(byte[] bytes, ComputedStyle cs) {
        HtmlNode doc = new HtmlParser().parse(new String(bytes, StandardCharsets.UTF_8));
        HtmlNode svg = findFirst(doc, "svg");
        return (svg != null) ? new SvgBox(SvgParser.parse(svg), cs) : null;
    }

    private static HtmlNode findFirst(HtmlNode node, String tag) {
        if (node.isElement() && tag.equals(node.tagName())) return node;
        for (HtmlNode c : node.children()) {
            HtmlNode f = findFirst(c, tag);
            if (f != null) return f;
        }
        return null;
    }

    /** 1-based position of {@code li} among its sibling list items, honouring an ol {@code start}. */
    private static int liOrdinal(HtmlNode li) {
        HtmlNode parent = li.parent();
        int start = 1;
        if (parent != null && "ol".equals(parent.tagName())) {
            String s = parent.attr("start");
            if (s != null) {
                try { start = Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) { }
            }
        }
        int i = 0;
        if (parent != null) {
            for (HtmlNode ch : parent.children()) {
                if (ch.isElement() && "li".equals(ch.tagName())) {
                    if (ch == li) break;
                    i++;
                }
            }
        }
        return start + i;
    }

    private static int attrInt(HtmlNode el, String name, int def) {
        String v = el.attr(name);
        if (v == null) return def;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static HtmlNode findBody(HtmlNode node) {
        if (node.isElement() && "body".equals(node.tagName())) return node;
        for (HtmlNode c : node.children()) {
            HtmlNode b = findBody(c);
            if (b != null) return b;
        }
        return null;
    }
}
