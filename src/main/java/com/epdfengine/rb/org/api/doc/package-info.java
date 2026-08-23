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
 * Programmatic layout model (Mode B): {@code Document}, {@code Div},
 * {@code Paragraph}, {@code Text}, {@code Image}, {@code Table}/{@code Row}/
 * {@code Cell}, {@code List}, {@code Barcode}, {@code SvgGraphic}. These build the
 * same internal box tree as the markup pipeline.
 */
package com.epdfengine.rb.org.api.doc;
