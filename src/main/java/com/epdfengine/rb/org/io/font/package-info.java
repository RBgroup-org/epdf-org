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
 * Font parsing, subsetting and selection (no fonts are bundled — the user supplies
 * them). {@code TrueTypeFont} is a clean-room sfnt reader (head/maxp/hhea/hmtx/cmap
 * formats 4 and 12; advance widths, ascent/descent, bbox; raw bytes for embedding).
 * {@code TrueTypeSubsetter} builds a subset containing only the glyphs used.
 * {@code FontRegistry} registers many fonts by family+weight+style, resolves with
 * fallback, and picks a covering font per codepoint for mixed-script runs.
 * CFF/OTF outlines and TTC collections are out of scope (CFF-flavoured OTF is
 * rejected rather than mis-embedded).
 */
package com.epdfengine.rb.org.io.font;
