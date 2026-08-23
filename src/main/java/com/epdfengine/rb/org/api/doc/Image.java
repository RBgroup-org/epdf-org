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
package com.epdfengine.rb.org.api.doc;

import com.epdfengine.rb.org.io.image.DecodedImage;
import com.epdfengine.rb.org.io.image.ImageDecoder;
import com.epdfengine.rb.org.layout.box.Box;
import com.epdfengine.rb.org.layout.box.ImageBox;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A raster image — the Mode B counterpart of {@code <img>}. Decodes PNG/JPEG/WebP
 * bytes into the box tree; set {@link #width(double)}/{@link #height(double)} to size
 * it, otherwise it uses its intrinsic pixels.
 */
public final class Image extends Styleable<Image> {

    private final byte[] bytes;

    private Image(byte[] bytes) { this.bytes = bytes; }

    /** An image from encoded bytes (PNG/JPEG/WebP). */
    public static Image of(byte[] bytes) { return new Image(bytes); }

    /** An image loaded from a file on disk. */
    public static Image fromFile(Path file) {
        try {
            return new Image(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read image " + file, e);
        }
    }

    /** Alternate text for accessibility (PDF/UA Figure {@code /Alt}). */
    public Image alt(String text) { style.setStructAlt(text); return this; }

    @Override public Box toBox() {
        DecodedImage img = ImageDecoder.decode(bytes);
        return new ImageBox(img, style);
    }
}
