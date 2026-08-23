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
 * Hyphenation. {@link com.epdfengine.rb.org.text.hyphen.Hyphenator} is a clean-room
 * implementation of Frank Liang's competing-patterns algorithm (as used by TeX):
 * substrings of a word are matched against a pattern set, odd/even inter-letter
 * values compete by maximum, and an odd value marks a legal break subject to
 * {@code leftMin}/{@code rightMin} fragment minimums. Language-specific pattern data
 * is supplied separately via {@link com.epdfengine.rb.org.text.hyphen.HyphenationPatterns}.
 */
package com.epdfengine.rb.org.text.hyphen;
