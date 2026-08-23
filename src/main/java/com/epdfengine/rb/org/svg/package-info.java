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
 * SVG rendering — both <b>vector</b> (to PDF path operators, resolution-independent)
 * and <b>raster</b> (to a bitmap via Java2D for the fallback path).
 *
 * <p><b>Implemented so far:</b> {@code SvgParser} (parses an {@code <svg>} HtmlNode
 * into an {@code SvgImage}: viewBox, {@code path d} = M L H V C S Q T Z absolute and
 * relative, {@code rect/circle/ellipse/line/polyline/polygon}, transforms
 * translate/scale/rotate/matrix, named/#hex/rgb() colours, and inline style); the
 * {@code SvgPath}/{@code SvgImage} model; and {@code SvgRasterizer} (Java2D → RGBA).
 * Vector drawing itself lives in {@code render.Renderer}. Not yet: gradients,
 * patterns, clip paths, text and filters; arcs currently degrade to a line.</p>
 */
package com.epdfengine.rb.org.svg;
