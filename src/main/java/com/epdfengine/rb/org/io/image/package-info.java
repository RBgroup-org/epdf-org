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
 * Image decoding for embedding as PDF image XObjects. {@code DecodedImage} carries
 * either a JPEG passthrough (embedded directly via DCTDecode — dimensions read from
 * the SOF marker, no pixel decode) or RGB(A) samples; {@code ImageDecoder} decodes
 * PNG/GIF/BMP (via {@code javax.imageio}) and WebP (clean-room {@code WebpDecoder} —
 * VP8 lossy + VP8L lossless) to RGB(A), turning alpha into a PDF soft mask.
 * {@code ImageOptimizer} recompresses/downsamples on save.
 */
package com.epdfengine.rb.org.io.image;
