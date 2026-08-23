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
 * Low-level resources. {@code font}: TrueType parsing, glyph subsetting and
 * multi-font selection with per-codepoint fallback. {@code image}: PNG/JPEG/GIF/BMP
 * (via {@code javax.imageio}) and WebP (clean-room VP8 lossy + VP8L lossless)
 * decoding to RGB(A) for embedding as image XObjects, plus recompression. Colour is
 * sRGB-based (the ICC output intent lives in {@code pdfa}).
 */
package com.epdfengine.rb.org.io;
