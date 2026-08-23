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
 * Layout engine: consumes the box tree and produces positioned drawables.
 *
 * <p>{@code LayoutEngine} implements block + inline flow (vertical stacking,
 * padding/border/background, greedy line-wrap with font metrics, text-align),
 * <b>flexbox</b>, <b>CSS grid</b> (tracks, areas, spans, stretch), <b>tables</b>
 * (rows/cells, colspan/rowspan, repeating headers, row equalization),
 * multi-column, list markers, page fragmentation with keep-together,
 * internal-link anchors, and image/SVG/form placement. Output is the sealed
 * {@code Drawable} model ({@code PaintedRect}/{@code PositionedText}/
 * {@code PaintedImage}/{@code PaintedSvg}/{@code PaintedShadow}/
 * {@code PositionedFormField}) in a {@code LaidOutDocument}, plus {@code StructNode}
 * for tagging. {@code LayoutParams} carries page geometry; {@code TextMeasurer} /
 * {@code ApproxTextMeasurer} measure text. Box types live in {@code layout.box}.
 * See docs/04-layout-engine.md.</p>
 */
package com.epdfengine.rb.org.layout;
