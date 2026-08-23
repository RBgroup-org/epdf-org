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
package com.epdfengine.rb.org.xml;

import java.util.Map;

/**
 * Maps a parsed XML tree onto an HTML string so any XML vocabulary can reuse the
 * engine's HTML/CSS layout pipeline unchanged. The mapping itself is supplied by a
 * {@link Resolver} (e.g. the DITA transformer) — this class is vocabulary-agnostic
 * and lives entirely apart from the HTML layout code, so XML support can evolve
 * without touching HTML rendering.
 */
public final class XmlToHtml {

    private XmlToHtml() {}

    /** Chooses how each XML element becomes HTML; may inspect the element's parent for context. */
    public interface Resolver {
        Rule ruleFor(XmlNode element);
    }

    /** One element's mapping: an HTML tag + CSS class, or a transparent/drop/text-only behaviour. */
    public static final class Rule {
        public enum Mode { WRAP, TRANSPARENT, DROP, TEXT_ONLY }
        final String htmlTag;
        final String cssClass;
        final Mode mode;
        final boolean voidTag;                 // img/br/hr: no children, self-closing
        final Map<String, String> attrMap;     // xml attr -> html attr (e.g. href->src)
        final String prefixHtml;               // raw HTML injected right after the open tag (e.g. a label)

        private Rule(String htmlTag, String cssClass, Mode mode, boolean voidTag, Map<String, String> attrMap, String prefixHtml) {
            this.htmlTag = htmlTag; this.cssClass = cssClass; this.mode = mode; this.voidTag = voidTag;
            this.attrMap = attrMap; this.prefixHtml = prefixHtml;
        }

        public static Rule wrap(String htmlTag, String cssClass) {
            return new Rule(htmlTag, cssClass, Mode.WRAP, false, null, null);
        }
        public static Rule wrap(String htmlTag, String cssClass, Map<String, String> attrMap) {
            return new Rule(htmlTag, cssClass, Mode.WRAP, false, attrMap, null);
        }
        public static Rule voidTag(String htmlTag, String cssClass, Map<String, String> attrMap) {
            return new Rule(htmlTag, cssClass, Mode.WRAP, true, attrMap, null);
        }
        /** A WRAP rule that injects {@code prefixHtml} (e.g. a generated label) before its children. */
        public static Rule labeled(String htmlTag, String cssClass, String prefixHtml) {
            return new Rule(htmlTag, cssClass, Mode.WRAP, false, null, prefixHtml);
        }
        public static Rule transparent() { return new Rule(null, null, Mode.TRANSPARENT, false, null, null); }
        public static Rule drop()        { return new Rule(null, null, Mode.DROP, false, null, null); }
        public static Rule textOnly(String htmlTag, String cssClass) {
            return new Rule(htmlTag, cssClass, Mode.TEXT_ONLY, false, null, null);
        }
    }

    /** Transforms the XML subtree rooted at {@code root} into an HTML fragment string. */
    public static String transform(XmlNode root, Resolver resolver) {
        StringBuilder sb = new StringBuilder(4096);
        emit(root, resolver, sb);
        return sb.toString();
    }

    private static void emit(XmlNode n, Resolver r, StringBuilder sb) {
        if (n == null) return;
        if (n.isText()) {
            if (!n.text().isBlank()) sb.append(escapeText(n.text()));   // drop pure indentation whitespace
            return;
        }
        Rule rule = r.ruleFor(n);
        if (rule == null) rule = Rule.wrap("div", "x-" + safeClass(n.localName()));

        switch (rule.mode) {
            case DROP:
                return;
            case TEXT_ONLY: {
                String cls = classOf(rule, n);
                openTag(sb, rule.htmlTag, cls, n, rule);
                sb.append(escapeText(n.textContent()));
                sb.append("</").append(rule.htmlTag).append('>');
                return;
            }
            case TRANSPARENT:
                for (XmlNode c : n.children()) emit(c, r, sb);
                return;
            case WRAP:
            default: {
                String cls = classOf(rule, n);
                openTag(sb, rule.htmlTag, cls, n, rule);
                if (rule.voidTag) return;
                if (rule.prefixHtml != null) sb.append(rule.prefixHtml);
                for (XmlNode c : n.children()) emit(c, r, sb);
                sb.append("</").append(rule.htmlTag).append('>');
            }
        }
    }

    private static void openTag(StringBuilder sb, String tag, String cls, XmlNode n, Rule rule) {
        sb.append('<').append(tag);
        if (cls != null && !cls.isBlank()) sb.append(" class=\"").append(escapeAttr(cls)).append('"');
        if (n.hasAttr("id") && !n.attr("id").isBlank()) sb.append(" id=\"").append(escapeAttr(n.attr("id"))).append('"');
        if (rule.attrMap != null) {
            for (Map.Entry<String, String> e : rule.attrMap.entrySet()) {
                if (n.hasAttr(e.getKey()) && !n.attr(e.getKey()).isBlank()) {
                    sb.append(' ').append(e.getValue()).append("=\"").append(escapeAttr(n.attr(e.getKey()))).append('"');
                }
            }
        }
        sb.append('>');
    }

    private static String classOf(Rule rule, XmlNode n) {
        String cls = rule.cssClass;
        String oc = n.attr("outputclass");     // DITA @outputclass adds presentation classes
        if (oc != null && !oc.isBlank()) cls = (cls == null || cls.isBlank()) ? oc.trim() : cls + " " + oc.trim();
        return cls;
    }

    private static String safeClass(String s) {
        return (s == null) ? "" : s.replaceAll("[^A-Za-z0-9_-]", "-");
    }

    private static String escapeText(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeAttr(String s) {
        return escapeText(s).replace("\"", "&quot;");
    }
}
