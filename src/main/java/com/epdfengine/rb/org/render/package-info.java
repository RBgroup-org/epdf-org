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
 * Render: maps positioned drawables to PDF content. {@code content} builds the
 * content stream + graphics state.
 *
 * <p>{@code Renderer} runs two passes — embed the fonts and images/raster assets
 * actually used, then draw text (base-14 or embedded CID glyphs), rectangles,
 * gradients, images and vector/raster SVG. It emits marked content and a logical
 * structure tree for tagged / accessible PDF (PDF/UA, PDF/A level-a) when a
 * conformance target is set. {@code FontEmbedder} writes
 * Type0/CIDFontType2/FontFile2 with Identity-H + ToUnicode + subset {@code W};
 * {@code ShadowRasterizer} and {@code BackdropRasterizer} render box-shadow and
 * backdrop-blur.</p>
 */
package com.epdfengine.rb.org.render;
