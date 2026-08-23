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
package com.epdfengine.rb.org.layout.box;

import com.epdfengine.rb.org.css.ComputedStyle;
import com.epdfengine.rb.org.io.image.DecodedImage;

/**
 * A replaced (leaf) box holding a decoded raster image. The layout engine sizes it
 * from the style width/height when given, otherwise from the image's intrinsic
 * pixels (preserving aspect ratio). Block-level for now.
 */
public final class ImageBox extends Box {

    private final DecodedImage image;

    public ImageBox(DecodedImage image, ComputedStyle style) {
        super(style);
        this.image = image;
    }

    public DecodedImage image() { return image; }

    @Override public Box add(Box child) {
        throw new UnsupportedOperationException("ImageBox is a replaced leaf and cannot have children");
    }

    @Override public boolean isBlockLevel()  { return true; }
    @Override public boolean isInlineLevel() { return false; }
}
