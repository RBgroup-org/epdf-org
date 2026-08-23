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
package com.epdfengine.rb.org.io.image;

/**
 * A decoded (or passthrough) raster image ready to embed as a PDF image XObject.
 *
 * <ul>
 *   <li>{@code JPEG} — the original JPEG bytes are embedded directly via DCTDecode
 *       (no re-encoding); {@code components} selects the colour space.</li>
 *   <li>{@code RGB} — interleaved 8-bit R,G,B samples ({@code width*height*3}).</li>
 *   <li>{@code RGBA} — RGB samples plus an 8-bit {@code alpha} plane
 *       ({@code width*height}) emitted as a soft mask.</li>
 * </ul>
 */
public final class DecodedImage {

    public enum Kind { JPEG, RGB, RGBA }

    private final Kind kind;
    private final int width;
    private final int height;
    private final int components;   // JPEG only: 1 (gray), 3 (RGB/YCbCr), 4 (CMYK/YCCK)
    private final byte[] data;      // JPEG bytes, or interleaved RGB samples
    private final byte[] alpha;     // RGBA only: one byte per pixel

    private DecodedImage(Kind kind, int width, int height, int components, byte[] data, byte[] alpha) {
        this.kind = kind;
        this.width = width;
        this.height = height;
        this.components = components;
        this.data = data;
        this.alpha = alpha;
    }

    public static DecodedImage jpeg(byte[] jpegBytes, int width, int height, int components) {
        return new DecodedImage(Kind.JPEG, width, height, components, jpegBytes, null);
    }

    public static DecodedImage rgb(byte[] rgb, int width, int height) {
        return new DecodedImage(Kind.RGB, width, height, 3, rgb, null);
    }

    public static DecodedImage rgba(byte[] rgb, byte[] alpha, int width, int height) {
        return new DecodedImage(Kind.RGBA, width, height, 3, rgb, alpha);
    }

    public Kind kind()       { return kind; }
    public int width()       { return width; }
    public int height()      { return height; }
    public int components()  { return components; }
    public byte[] data()     { return data; }
    public byte[] alpha()    { return alpha; }

    /** Intrinsic aspect ratio (width/height), or 1 if height is zero. */
    public double aspect() { return height == 0 ? 1.0 : (double) width / height; }
}
