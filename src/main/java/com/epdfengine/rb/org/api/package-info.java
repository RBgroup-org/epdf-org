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
 * Public programmatic API. The <b>Mode B</b> document model lives in
 * {@code api.doc} ({@code Document}, {@code Div}, {@code Paragraph}, {@code Table},
 * {@code ListBlock}, {@code Image}, {@code Barcode}, {@code SvgGraphic}, …), which
 * builds the same box tree as the markup pipeline. The engine facade and
 * request/result/config types live in {@code runtime}
 * ({@link com.epdfengine.rb.org.runtime.Epdf}, {@code RenderRequest},
 * {@code RenderResult}, {@code EngineConfig}); <b>Mode A</b> (markup) is driven via
 * {@code core.HtmlRenderer} or {@code RenderRequest.of(html)}. Both modes converge
 * on one layout/render/writer path.
 */
package com.epdfengine.rb.org.api;
