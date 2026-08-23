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
package com.epdfengine.rb.org.api.doc;

import com.epdfengine.rb.org.html.HtmlNode;
import com.epdfengine.rb.org.html.HtmlParser;
import com.epdfengine.rb.org.layout.box.Box;
import com.epdfengine.rb.org.layout.box.SvgBox;
import com.epdfengine.rb.org.svg.SvgParser;

/**
 * Vector SVG artwork — the Mode B counterpart of an inline {@code <svg>}. Parses SVG
 * markup and draws it as native PDF vector operators (crisp at any zoom).
 */
public final class SvgGraphic extends Styleable<SvgGraphic> {

    private final String markup;

    private SvgGraphic(String markup) { this.markup = markup; }

    /** An SVG from its markup string (must contain an {@code <svg>} element). */
    public static SvgGraphic of(String svgMarkup) { return new SvgGraphic(svgMarkup); }

    @Override public Box toBox() {
        HtmlNode doc = new HtmlParser().parse(markup);
        HtmlNode svg = findSvg(doc);
        if (svg == null) throw new IllegalArgumentException("markup contains no <svg> element");
        return new SvgBox(SvgParser.parse(svg), style);
    }

    private static HtmlNode findSvg(HtmlNode node) {
        if (node.isElement() && "svg".equals(node.tagName())) return node;
        for (HtmlNode c : node.children()) {
            HtmlNode f = findSvg(c);
            if (f != null) return f;
        }
        return null;
    }
}
