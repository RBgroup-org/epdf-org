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

import com.epdfengine.rb.org.io.image.DecodedImage;

/**
 * A raster image placed on a page. Coordinates are top-left origin, in points; the
 * painter embeds the image once and draws it with an image transform. The
 * {@link DecodedImage} identity is used by the renderer to embed each image only
 * once even if it appears multiple times.
 */
public final class PaintedImage implements Drawable {

    private final int page;
    private final double x, y, width, height;
    private final DecodedImage image;
    private String altText;
    private String structRole;
    private int structGroupId = -1;
    private com.epdfengine.rb.org.css.AnimFrame[] animFrames = null;
    private double animMs = 0;

    public PaintedImage(int page, double x, double y, double width, double height, DecodedImage image) {
        this.page = page;
        this.x = x; this.y = y; this.width = width; this.height = height;
        this.image = image;
    }

    @Override public int pageIndex() { return page; }

    public double x()      { return x; }
    public double y()      { return y; }
    public double width()  { return width; }
    public double height() { return height; }
    public DecodedImage image() { return image; }

    /** Alternate text for the Figure structure element (PDF/UA); may be null. */
    public String altText()      { return altText; }
    public String structRole()   { return structRole; }
    public int structGroupId()   { return structGroupId; }
    public PaintedImage tag(String role, int groupId, String alt) {
        this.structRole = role; this.structGroupId = groupId; this.altText = alt; return this;
    }

    public PaintedImage animFrames(com.epdfengine.rb.org.css.AnimFrame[] frames, double ms) { this.animFrames = frames; this.animMs = ms; return this; }
    public com.epdfengine.rb.org.css.AnimFrame[] animFrames() { return animFrames; }
    public double animMs() { return animMs; }
    public boolean isAnimated() { return animFrames != null && animFrames.length > 1; }
}
