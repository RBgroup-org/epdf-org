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
package com.epdfengine.rb.org.css;

/**
 * One sampled frame of a CSS {@code @keyframes} animation: the interpolated
 * background colour, opacity and 2D transform (rotate/scale/translate) at a
 * point in the cycle. {@code rgb == -1} means the element's static background
 * is kept for that frame.
 */
public record AnimFrame(int rgb, double alpha, double rotateDeg, double scale, double tx, double ty) {

    public boolean hasTransform() { return rotateDeg != 0 || scale != 1.0 || tx != 0 || ty != 0; }
}
