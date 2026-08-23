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
package com.epdfengine.rb.org.html;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A node in the parsed HTML tree — either an element (tag + attributes + children)
 * or a text node. Deliberately minimal: no namespaces, no scripting, no live DOM
 * mutation observers. Tag names and attribute keys are lower-cased.
 */
public final class HtmlNode {

    public enum Type { ELEMENT, TEXT }

    private final Type type;
    private final String tagName;                       // null for TEXT
    private final String text;                          // null for ELEMENT
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private final List<HtmlNode> children = new ArrayList<>();
    private HtmlNode parent;                             // set when added as a child

    private HtmlNode(Type type, String tagName, String text) {
        this.type = type;
        this.tagName = tagName;
        this.text = text;
    }

    public static HtmlNode element(String tagName) {
        return new HtmlNode(Type.ELEMENT, tagName.toLowerCase(Locale.ROOT), null);
    }

    public static HtmlNode text(String text) {
        return new HtmlNode(Type.TEXT, null, text);
    }

    public Type type()                    { return type; }
    public boolean isElement()            { return type == Type.ELEMENT; }
    public boolean isText()               { return type == Type.TEXT; }
    public String tagName()               { return tagName; }
    public String text()                  { return text; }
    public Map<String, String> attributes() { return attributes; }
    public List<HtmlNode> children()      { return children; }
    public HtmlNode parent()              { return parent; }

    public void addChild(HtmlNode child)  { if (child != null) { child.parent = this; children.add(child); } }

    /** Attribute value (lower-cased key), or {@code null} if absent. */
    public String attr(String name) {
        return attributes.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean hasTag(String name) {
        return type == Type.ELEMENT && tagName.equals(name);
    }

    /** Concatenated text of this node and all descendants. */
    public String textContent() {
        StringBuilder sb = new StringBuilder();
        collectText(this, sb);
        return sb.toString();
    }

    private static void collectText(HtmlNode n, StringBuilder sb) {
        if (n.isText()) {
            sb.append(n.text);
        } else {
            for (HtmlNode c : n.children) collectText(c, sb);
        }
    }
}
