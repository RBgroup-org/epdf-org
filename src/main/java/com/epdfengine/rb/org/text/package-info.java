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
 * International text support. Implemented: {@code hyphen} — Liang competing-patterns
 * hyphenation ({@link com.epdfengine.rb.org.text.hyphen.Hyphenator} +
 * {@code HyphenationPatterns}). Bidi (UAX&nbsp;#9) and complex-script shaping
 * (Arabic/Indic/Thai OpenType GSUB/GPOS) are planned but not yet present in this
 * build. See docs/05-fonts-text-intl.md.
 */
package com.epdfengine.rb.org.text;
