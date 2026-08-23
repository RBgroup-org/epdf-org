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
package com.epdfengine.rb.org.xml.dita;

import com.epdfengine.rb.org.xml.XmlNode;
import com.epdfengine.rb.org.xml.XmlParser;
import com.epdfengine.rb.org.xml.XmlToHtml;

import java.util.Map;

/**
 * Transforms a DITA topic (Concept, Task, Reference, Glossary, Troubleshooting, or a
 * generic Topic) into a styled HTML document that the engine's HTML/CSS pipeline renders
 * to PDF — a clean-room analogue of a DITA-OT PDF transform. The element→HTML mapping is
 * a {@link XmlToHtml.Resolver}; presentation lives in {@link #defaultCss()}, which a caller
 * may override to customise the transform without changing the mapping.
 */
public final class DitaTransformer {

    private DitaTransformer() {}

    /** Renders a topic XML string to a full HTML document using the default stylesheet. */
    public static String topicToHtml(String xml) {
        return topicToHtml(xml, defaultCss(), null);
    }

    /** Renders a topic XML string to a full HTML document with a caller-supplied stylesheet. */
    public static String topicToHtml(String xml, String css) {
        return topicToHtml(xml, css, null);
    }

    /**
     * Renders a topic XML string to a full HTML document with a stylesheet and an optional
     * conditional-processing filter ({@link DitaVal}). Passing {@code null} applies no filtering.
     */
    public static String topicToHtml(String xml, String css, DitaVal filter) {
        XmlNode root = XmlParser.parse(xml);
        if (root != null) prepare(root, filter);
        String body = (root == null) ? "" : XmlToHtml.transform(root, DitaTransformer::ruleFor);
        return "<!DOCTYPE html><html><head><style>" + css + "</style></head><body>"
                + "<article class=\"dita\">" + body + "</article></body></html>";
    }

    /** Renders just the topic HTML fragment (no document wrapper) — used when combining a map. */
    public static String topicFragment(String xml) {
        XmlNode root = XmlParser.parse(xml);
        return topicFragment(root);
    }

    /** Renders an already-parsed topic to an HTML fragment (preprocesses in place). */
    public static String topicFragment(XmlNode root) {
        return topicFragment(root, null);
    }

    /** Renders an already-parsed topic to an HTML fragment with an optional {@link DitaVal} filter. */
    public static String topicFragment(XmlNode root, DitaVal filter) {
        return topicFragment(root, filter, null);
    }

    /**
     * Renders a topic fragment with an optional filter and a map key space for {@code keyref}
     * resolution ({@code key -> [href, text]}); {@code null} keys disables keyref processing.
     */
    public static String topicFragment(XmlNode root, DitaVal filter, Map<String, String[]> keys) {
        if (root == null) return "";
        prepare(root, filter, keys);
        return XmlToHtml.transform(root, DitaTransformer::ruleFor);
    }

    /**
     * Runs the DITA processing pipeline in place, in the order a DITA-OT run applies it:
     * conditional filtering (DITAVAL) \u2192 key reference ({@code @keyref}) resolution \u2192
     * content-reference ({@code @conref}) resolution \u2192 footnote numbering \u2192 generated-text
     * fill-in (xref labels, image alt).
     */
    private static void prepare(XmlNode root, DitaVal filter) {
        prepare(root, filter, null);
    }

    private static void prepare(XmlNode root, DitaVal filter, Map<String, String[]> keys) {
        if (filter != null && !filter.isEmpty()) filterConditional(root, filter);
        if (keys != null && !keys.isEmpty()) resolveKeyrefs(root, keys);
        resolveConrefs(root);
        numberFootnotes(root);
        preprocess(root);
    }

    /** Resolves {@code @keyref} (and {@code conkeyref} key part): fills in the key's href and/or text. */
    private static void resolveKeyrefs(XmlNode n, Map<String, String[]> keys) {
        if (n.isElement() && keys != null) {
            String kr = firstToken(n.attr("keyref").isBlank() ? n.attr("conkeyref") : n.attr("keyref"));
            if (!kr.isBlank()) {
                int slash = kr.indexOf('/');
                String key = slash > 0 ? kr.substring(0, slash) : kr;   // key part of key/element
                String[] hv = keys.get(key);
                if (hv != null) {
                    if (hv[0] != null && !hv[0].isBlank() && n.attr("href").isBlank()) n.setAttr("href", hv[0]);
                    if (hv[1] != null && !hv[1].isBlank() && n.textContent().isBlank()) n.addChild(XmlNode.text(hv[1]));
                }
            }
        }
        for (XmlNode c : n.children()) resolveKeyrefs(c, keys);
    }

    private static String firstToken(String s) {
        if (s == null) return "";
        s = s.trim();
        int sp = s.indexOf(' ');
        return sp > 0 ? s.substring(0, sp) : s;
    }

    /** Removes descendants excluded by the conditional-processing filter. */
    private static void filterConditional(XmlNode n, DitaVal filter) {
        java.util.List<XmlNode> kept = new java.util.ArrayList<>();
        boolean changed = false;
        for (XmlNode c : n.children()) {
            if (c.isElement() && filter.isExcluded(c)) { changed = true; continue; }
            kept.add(c);
        }
        if (changed) { n.clearChildren(); for (XmlNode c : kept) n.addChild(c); }
        for (XmlNode c : n.children()) filterConditional(c, filter);
    }

    // --- content references (@conref), same-file resolution -----------------------------------

    /** Resolves same-file {@code @conref} references by replacing each with a copy of its target. */
    private static void resolveConrefs(XmlNode root) {
        java.util.Map<String, XmlNode> byId = new java.util.HashMap<>();
        indexIds(root, byId);
        if (!byId.isEmpty()) resolveConrefWalk(root, byId, 0);
    }

    private static void indexIds(XmlNode n, java.util.Map<String, XmlNode> byId) {
        if (n.isElement()) {
            String id = n.attr("id");
            if (!id.isBlank()) byId.putIfAbsent(id, n);
            for (XmlNode c : n.children()) indexIds(c, byId);
        }
    }

    private static void resolveConrefWalk(XmlNode n, java.util.Map<String, XmlNode> byId, int depth) {
        for (XmlNode c : new java.util.ArrayList<>(n.children())) {
            if (!c.isElement()) continue;
            String conref = c.attr("conref");
            if (!conref.isBlank() && depth < 32) {
                XmlNode target = conrefTarget(conref, byId);
                if (target != null && target != c && !isAncestor(c, target)) {
                    XmlNode copy = target.deepCopy();
                    copy.attributes().remove("conref");
                    int idx = n.indexOf(c);
                    n.removeChild(c);
                    n.addChild(idx, copy);
                    resolveConrefWalk(copy, byId, depth + 1);
                    continue;
                }
            }
            resolveConrefWalk(c, byId, depth);
        }
    }

    /** The local target element of a {@code conref}, or {@code null} (cross-file → keep fallback). */
    private static XmlNode conrefTarget(String conref, java.util.Map<String, XmlNode> byId) {
        int hash = conref.indexOf('#');
        if (hash != 0) return null;   // no '#', or a path before it → not a same-file reference
        String frag = conref.substring(1);
        if (frag.isBlank()) return null;
        int slash = frag.lastIndexOf('/');
        return byId.get(slash >= 0 ? frag.substring(slash + 1) : frag);
    }

    private static boolean isAncestor(XmlNode node, XmlNode maybeAncestor) {
        for (XmlNode p = node.parent(); p != null; p = p.parent()) if (p == maybeAncestor) return true;
        return false;
    }

    // --- footnotes (<fn>) ----------------------------------------------------------------------

    /** Replaces each {@code <fn>} with a numbered callout and appends a collected notes list. */
    private static void numberFootnotes(XmlNode root) {
        java.util.List<XmlNode> notes = new java.util.ArrayList<>();
        collectFns(root, notes);
        if (notes.isEmpty()) return;
        XmlNode list = XmlNode.element("fn-list");
        int n = 1;
        for (XmlNode fn : notes) {
            String label = String.valueOf(n++);
            XmlNode parent = fn.parent();
            if (parent == null) continue;
            XmlNode marker = XmlNode.element("fn-ref");
            marker.addChild(XmlNode.text(label));
            int idx = parent.indexOf(fn);
            parent.removeChild(fn);
            parent.addChild(idx, marker);

            XmlNode item = XmlNode.element("fn-item");
            XmlNode num = XmlNode.element("fn-item-num");
            num.addChild(XmlNode.text(label + "."));
            item.addChild(num);
            XmlNode body = XmlNode.element("fn-item-body");
            for (XmlNode c : fn.children()) body.addChild(c.deepCopy());
            item.addChild(body);
            list.addChild(item);
        }
        root.addChild(list);
    }

    private static void collectFns(XmlNode n, java.util.List<XmlNode> out) {
        for (XmlNode c : n.children()) {
            if (c.isElement()) {
                if (c.localName().equals("fn")) out.add(c);
                else collectFns(c, out);
            }
        }
    }

    /** The topic's title text (first {@code <title>} / {@code <glossterm>} found), or {@code ""}. */
    public static String titleOf(XmlNode root) {
        XmlNode t = firstTitle(root);
        return t == null ? "" : t.textContent().trim();
    }

    private static XmlNode firstTitle(XmlNode n) {
        if (n == null) return null;
        for (XmlNode c : n.children()) {
            if (c.isElement() && (c.localName().equals("title") || c.localName().equals("glossterm"))) return c;
        }
        for (XmlNode c : n.children()) {
            XmlNode t = firstTitle(c);
            if (t != null) return t;
        }
        return null;
    }

    /** Fills in text the DITA processor would normally generate: xref labels and image alt text. */
    private static void preprocess(XmlNode n) {
        if (n.isElement()) {
            String ln = n.localName();
            if ((ln.equals("xref") || ln.equals("link")) && n.textContent().isBlank() && !n.attr("href").isBlank()) {
                n.addChild(XmlNode.text(readableHref(n.attr("href"))));
            } else if (ln.equals("image") && n.attr("alt").isBlank()) {
                XmlNode alt = n.child("alt");
                if (alt != null) n.setAttr("alt", alt.textContent().trim());
            }
        }
        for (XmlNode c : n.children()) preprocess(c);
    }

    /** A human-readable label for an href with no link text (fragment id, else the base file name). */
    private static String readableHref(String href) {
        String h = href.trim();
        if (h.startsWith("http") || h.startsWith("mailto:")) return h;
        int hash = h.indexOf('#');
        if (hash >= 0) {
            String frag = h.substring(hash + 1);
            if (!frag.isBlank()) return frag;
            h = h.substring(0, hash);
        }
        int slash = Math.max(h.lastIndexOf('/'), h.lastIndexOf('\\'));
        if (slash >= 0) h = h.substring(slash + 1);
        int dot = h.lastIndexOf('.');
        if (dot > 0) h = h.substring(0, dot);
        return h.isBlank() ? href : h;
    }

    private static final Map<String, String> IMG_ATTRS = Map.of("href", "src", "alt", "alt");
    private static final Map<String, String> HREF_TO_HREF = Map.of("href", "href");

    /** The DITA element → HTML mapping (context-aware for titles). */
    public static XmlToHtml.Rule ruleFor(XmlNode e) {
        String n = e.localName();
        switch (n) {
            // --- topic types (root + nested) ---
            case "concept": case "task": case "reference": case "topic":
            case "glossentry": case "glossgroup": case "troubleshooting":
                return XmlToHtml.Rule.wrap("section", "topic " + n);
            case "title": {
                String p = e.parent() != null ? e.parent().localName() : "";
                boolean top = p.equals("concept") || p.equals("task") || p.equals("reference")
                        || p.equals("topic") || p.equals("glossentry") || p.equals("troubleshooting");
                return XmlToHtml.Rule.wrap(top ? "h1" : "h2", "title");
            }
            case "shortdesc": return XmlToHtml.Rule.wrap("p", "shortdesc");
            case "abstract":  return XmlToHtml.Rule.wrap("div", "abstract");

            // --- bodies ---
            case "body": case "conbody": case "taskbody": case "refbody":
            case "glossbody": case "troublebody":
                return XmlToHtml.Rule.wrap("div", "body");
            case "section":   return XmlToHtml.Rule.wrap("section", "section");
            case "example":   return XmlToHtml.Rule.wrap("section", "example");
            case "sectiondiv": case "bodydiv": return XmlToHtml.Rule.wrap("div", null);

            // --- task structure ---
            case "prereq":     return labeled("prereq", "Before you begin");
            case "context":    return XmlToHtml.Rule.wrap("div", "context");
            case "steps":      return XmlToHtml.Rule.wrap("ol", "steps");
            case "steps-unordered": return XmlToHtml.Rule.wrap("ul", "steps");
            case "steps-informal":  return XmlToHtml.Rule.wrap("div", "steps-informal");
            case "step":       return XmlToHtml.Rule.wrap("li", "step");
            case "cmd":        return XmlToHtml.Rule.wrap("div", "cmd");
            case "info":       return XmlToHtml.Rule.wrap("div", "info");
            case "substeps":   return XmlToHtml.Rule.wrap("ol", "substeps");
            case "substep":    return XmlToHtml.Rule.wrap("li", "substep");
            case "choices":    return XmlToHtml.Rule.wrap("ul", "choices");
            case "choice":     return XmlToHtml.Rule.wrap("li", "choice");
            case "stepresult": return XmlToHtml.Rule.wrap("div", "stepresult");
            case "stepxmp":    return XmlToHtml.Rule.wrap("div", "stepxmp");
            case "tutorialinfo": return XmlToHtml.Rule.wrap("div", "info");
            case "result":     return labeled("result", "Result");
            case "postreq":    return labeled("postreq", "What to do next");

            // --- reference / troubleshooting ---
            case "refsyn":     return XmlToHtml.Rule.wrap("div", "refsyn");
            case "properties": return XmlToHtml.Rule.wrap("table", "properties");
            case "condition":  return labeled("condition", "Condition");
            case "cause":      return labeled("cause", "Cause");
            case "remedy":     return labeled("remedy", "Remedy");
            case "responsibleParty": return XmlToHtml.Rule.wrap("div", "info");

            // --- glossary ---
            case "glossterm":   return XmlToHtml.Rule.wrap("h1", "title");
            case "glossdef":    return XmlToHtml.Rule.wrap("div", "body");
            case "glossSurfaceForm": return XmlToHtml.Rule.wrap("p", "shortdesc");

            // --- blocks ---
            case "p":          return XmlToHtml.Rule.wrap("p", null);
            case "ul":         return XmlToHtml.Rule.wrap("ul", null);
            case "ol":         return XmlToHtml.Rule.wrap("ol", null);
            case "li":         return XmlToHtml.Rule.wrap("li", null);
            case "sl":         return XmlToHtml.Rule.wrap("ul", "sl");
            case "sli":        return XmlToHtml.Rule.wrap("li", null);
            case "dl":         return XmlToHtml.Rule.wrap("table", "dl");
            case "dlentry":    return XmlToHtml.Rule.wrap("tr", null);
            case "dt":         return XmlToHtml.Rule.wrap("td", "dt");
            case "dd":         return XmlToHtml.Rule.wrap("td", "dd");
            case "note":       return XmlToHtml.Rule.wrap("div", "note note-" + noteType(e));
            case "codeblock":  return XmlToHtml.Rule.wrap("pre", "codeblock");
            case "pre":        return XmlToHtml.Rule.wrap("pre", null);
            case "lines":      return XmlToHtml.Rule.wrap("pre", "lines");
            case "fig":        return XmlToHtml.Rule.wrap("figure", "fig");
            case "figgroup":   return XmlToHtml.Rule.wrap("div", null);

            // --- tables ---
            case "table":      return XmlToHtml.Rule.wrap("table", null);
            case "tgroup":     return XmlToHtml.Rule.transparent();
            case "thead":      return XmlToHtml.Rule.wrap("thead", null);
            case "tbody":      return XmlToHtml.Rule.wrap("tbody", null);
            case "row":        return XmlToHtml.Rule.wrap("tr", null);
            case "entry":      return XmlToHtml.Rule.wrap("td", null);
            case "simpletable": return XmlToHtml.Rule.wrap("table", "simpletable");
            case "sthead":     return XmlToHtml.Rule.wrap("thead", null);
            case "strow":      return XmlToHtml.Rule.wrap("tr", null);
            case "stentry":    return XmlToHtml.Rule.wrap("td", null);
            case "colspec": case "spanspec": return XmlToHtml.Rule.drop();

            // --- inline ---
            case "b":          return XmlToHtml.Rule.wrap("b", null);
            case "strong":     return XmlToHtml.Rule.wrap("b", null);
            case "i":          return XmlToHtml.Rule.wrap("i", null);
            case "em":         return XmlToHtml.Rule.wrap("i", null);
            case "u":          return XmlToHtml.Rule.wrap("u", null);
            case "line-through": case "overline": return XmlToHtml.Rule.wrap("span", n);
            case "sup":        return XmlToHtml.Rule.wrap("span", "sup");
            case "sub":        return XmlToHtml.Rule.wrap("span", "sub");
            case "term":       return XmlToHtml.Rule.wrap("i", "term");
            case "keyword":    return XmlToHtml.Rule.wrap("span", "keyword");
            case "ph":         return XmlToHtml.Rule.wrap("span", null);
            case "uicontrol":  return XmlToHtml.Rule.wrap("b", "uicontrol");
            case "menucascade": return XmlToHtml.Rule.wrap("span", "menucascade");
            case "wintitle":   return XmlToHtml.Rule.wrap("b", "wintitle");
            case "cmdname":    return XmlToHtml.Rule.wrap("code", "cmdname");
            case "codeph":     return XmlToHtml.Rule.wrap("code", "codeph");
            case "filepath":   return XmlToHtml.Rule.wrap("code", "filepath");
            case "userinput":  return XmlToHtml.Rule.wrap("code", "userinput");
            case "systemoutput": return XmlToHtml.Rule.wrap("code", "systemoutput");
            case "varname":    return XmlToHtml.Rule.wrap("code", "varname");
            case "apiname":    return XmlToHtml.Rule.wrap("code", "apiname");
            case "parmname":   return XmlToHtml.Rule.wrap("code", "parmname");
            case "option":     return XmlToHtml.Rule.wrap("code", "option");
            case "synph":      return XmlToHtml.Rule.wrap("code", null);
            case "xref":       return XmlToHtml.Rule.wrap("a", "xref", HREF_TO_HREF);
            case "link":       return XmlToHtml.Rule.wrap("a", "link", HREF_TO_HREF);
            case "image":      return XmlToHtml.Rule.voidTag("img", "image", IMG_ATTRS);
            case "alt":        return XmlToHtml.Rule.drop();

            // --- footnotes (fn is replaced by numberFootnotes; the rest are synthetic) ---
            case "fn":           return XmlToHtml.Rule.drop();
            case "fn-ref":       return XmlToHtml.Rule.wrap("sup", "fn-ref");
            case "fn-list":      return XmlToHtml.Rule.labeled("div", "footnotes",
                                        "<div class=\"labeled-title\">Notes</div>");
            case "fn-item":      return XmlToHtml.Rule.wrap("div", "fn-item");
            case "fn-item-num":  return XmlToHtml.Rule.wrap("span", "fn-item-num");
            case "fn-item-body": return XmlToHtml.Rule.wrap("span", "fn-item-body");

            // --- related links ---
            case "related-links": return XmlToHtml.Rule.labeled("div", "related-links",
                                        "<div class=\"labeled-title\">Related information</div>");
            case "linklist":     return XmlToHtml.Rule.wrap("div", "linklist");
            case "linkpool":     return XmlToHtml.Rule.wrap("div", "linkpool");
            case "linktext":     return XmlToHtml.Rule.transparent();
            case "linkinfo":     return XmlToHtml.Rule.wrap("div", "linkinfo");
            case "desc":         return XmlToHtml.Rule.wrap("div", "desc");

            // --- metadata / prolog: not rendered ---
            case "prolog": case "metadata": case "titlealts": case "data": case "resourceid":
            case "author": case "critdates": case "permissions": case "publisher":
            case "copyright": case "keywords": case "audience": case "category":
                return XmlToHtml.Rule.drop();

            default:
                return null;   // unknown element -> generic <div class="x-name">
        }
    }

    /** A labelled block: a bold generated heading (as a DITA-OT PDF transform emits) plus the content. */
    private static XmlToHtml.Rule labeled(String cls, String label) {
        return XmlToHtml.Rule.labeled("div", cls + " labeled",
                "<div class=\"labeled-title\">" + label + "</div>");
    }

    private static String noteType(XmlNode e) {
        String t = e.attr("type");
        return (t == null || t.isBlank()) ? "note" : t.trim();
    }

    /** The bundled DITA presentation stylesheet (override via {@link #topicToHtml(String,String)}). */
    public static String defaultCss() {
        return """
            body { margin: 0; font-family: Helvetica, Arial, sans-serif; color: #1f2937; }
            .dita { padding: 30pt 34pt; }
            h1.title { font-size: 22pt; margin: 0 0 6pt; color: #0f172a; }
            h2.title { font-size: 14pt; margin: 16pt 0 5pt; color: #1e293b; }
            .section > h2.title, .example > h2.title { margin-top: 14pt; }
            p { font-size: 10.5pt; line-height: 1.55; margin: 0 0 8pt; color: #374151; }
            p.shortdesc { font-size: 12pt; color: #475569; margin: 0 0 12pt; line-height: 1.5; }
            .abstract { color: #475569; margin: 0 0 12pt; }
            .body { }
            .section { margin: 12pt 0 0; }
            .example { margin: 12pt 0 0; padding: 10pt 12pt; background: #f8fafc; border-radius: 8px; }
            ul, ol { margin: 0 0 8pt; padding-left: 18pt; }
            li { font-size: 10.5pt; line-height: 1.5; margin: 0 0 4pt; color: #374151; }
            ol.steps { padding-left: 20pt; }
            ol.steps > li.step { margin: 0 0 8pt; }
            .step .cmd { font-weight: bold; color: #1e293b; }
            .step .info, .step .stepresult, .step .stepxmp { font-weight: normal; color: #475569; margin-top: 3pt; }
            .context, .refsyn, .stepresult { margin: 0 0 8pt; }
            .labeled { margin: 10pt 0; padding: 9pt 12pt; border-left: 3pt solid #94a3b8; background: #f8fafc; border-radius: 0 6px 6px 0; }
            .labeled-title { font-weight: bold; font-size: 9pt; color: #0f172a; letter-spacing: 0.4pt; margin: 0 0 4pt; }
            .prereq { border-left-color: #6366f1; }
            .result { border-left-color: #16a34a; }
            .postreq { border-left-color: #0ea5e9; }
            .cause { border-left-color: #f59e0b; }
            .remedy { border-left-color: #16a34a; }
            .condition { border-left-color: #64748b; }
            .note { margin: 10pt 0; padding: 9pt 12pt 9pt 12pt; border-left: 3pt solid #3b82f6; background: #eff6ff; border-radius: 0 6px 6px 0; font-size: 10pt; color: #1e40af; }
            .note-warning, .note-caution, .note-danger { border-left-color: #ef4444; background: #fef2f2; color: #b91c1c; }
            .note-tip { border-left-color: #16a34a; background: #f0fdf4; color: #166534; }
            .note-important, .note-attention { border-left-color: #f59e0b; background: #fffbeb; color: #92400e; }
            pre.codeblock, pre.lines { background: #0f172a; color: #e2e8f0; border-radius: 8px; padding: 10pt 12pt; font-family: monospace; font-size: 9.5pt; line-height: 1.4; margin: 0 0 8pt; }
            code { background: #f1f5f9; color: #be123c; border-radius: 4px; padding: 1pt 5pt; font-family: monospace; font-size: 9.5pt; }
            code.userinput { color: #0f172a; font-weight: bold; }
            code.filepath { color: #7c3aed; }
            b.uicontrol { color: #0f172a; }
            i.term { color: #4f46e5; font-style: italic; }
            a.xref, a.link { color: #4f46e5; text-decoration: underline; }
            table { width: 100%; border-collapse: collapse; margin: 0 0 10pt; }
            th, td, thead td { border: 0.6pt solid #cbd5e1; padding: 5pt 7pt; font-size: 9.5pt; vertical-align: top; }
            thead td, thead th { background: #eef2ff; font-weight: bold; color: #1e293b; }
            table.dl td.dt { font-weight: bold; width: 30%; background: #f8fafc; }
            table.simpletable thead td { background: #eef2ff; }
            figure.fig { margin: 10pt 0; }
            img.image { max-width: 100%; }
            sup.fn-ref { font-size: 7pt; color: #4f46e5; vertical-align: super; font-weight: bold; }
            .footnotes { margin: 14pt 0 0; padding: 9pt 12pt; border-left: 3pt solid #94a3b8; background: #f8fafc; border-radius: 0 6px 6px 0; }
            .fn-item { font-size: 9pt; color: #475569; line-height: 1.45; margin: 0 0 3pt; }
            .fn-item-num { font-weight: bold; color: #0f172a; margin-right: 5pt; }
            .related-links { margin: 14pt 0 0; padding: 9pt 12pt; border-left: 3pt solid #0ea5e9; background: #f0f9ff; border-radius: 0 6px 6px 0; }
            .related-links a.link, .related-links a.xref { display: block; margin: 0 0 3pt; color: #0369a1; }
            .linkinfo, .desc { font-size: 9pt; color: #64748b; margin: 0 0 4pt; }
            """;
    }
}
