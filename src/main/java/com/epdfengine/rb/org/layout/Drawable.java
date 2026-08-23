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
package com.epdfengine.rb.org.layout;

/**
 * A single positioned output element of the layout stage. Drawables are emitted
 * in paint order (backgrounds/borders before the text they decorate), so painting
 * the list in sequence yields the correct z-order. Sealed: the render stage
 * switches over the complete set.
 */
public sealed interface Drawable permits PaintedRect, PositionedText, PaintedImage, PaintedSvg, PaintedShadow, PositionedFormField {

    /** Zero-based page this drawable belongs to. */
    int pageIndex();
}
