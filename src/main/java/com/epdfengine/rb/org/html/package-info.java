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
/**
 * HTML5 tokenizer + tree builder producing a DOM. Error-tolerant, no script
 * execution, bounded nesting/node counts.
 *
 * <p><b>Implemented so far:</b> {@code HtmlNode} (element/text DOM) and
 * {@code HtmlParser} (a forgiving tag-soup tree builder: comments, doctype, void
 * and raw-text ({@code script}/{@code style}) elements, quoted/unquoted/boolean
 * attributes, and numeric + ~25 named entities; no script execution, no external
 * entities).</p>
 */
package com.epdfengine.rb.org.html;
