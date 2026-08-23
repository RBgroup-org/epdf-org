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
 * Multimedia / motion. {@link com.epdfengine.rb.org.multimedia.PdfAnimation} builds
 * document JavaScript that flip-books a PDF by cycling the visibility of Optional
 * Content Groups (layers) on a timer — one layer per frame.
 *
 * <p>Motion requires a viewer that runs document JavaScript (Adobe Acrobat/Reader);
 * viewers without a JS engine show only the default-visible frame, so frame&nbsp;0
 * should be a sensible static poster.</p>
 */
package com.epdfengine.rb.org.multimedia;
