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

import com.epdfengine.rb.org.barcode.BarcodeImages;
import com.epdfengine.rb.org.barcode.BarcodeMatrix;
import com.epdfengine.rb.org.barcode.Barcodes;
import com.epdfengine.rb.org.io.image.DecodedImage;
import com.epdfengine.rb.org.io.image.ImageDecoder;
import com.epdfengine.rb.org.layout.box.Box;
import com.epdfengine.rb.org.layout.box.ImageBox;

/**
 * A 1D/2D barcode — the Mode B way to drop a QR, Code 128, Code 39, EAN-13 or Data
 * Matrix symbol into a document. The engine encodes the symbol clean-room, rasterises
 * it and embeds it as an image. Size it with {@link #width(double)} (points).
 */
public final class Barcode extends Styleable<Barcode> {

    private final String type;
    private final String data;
    private int moduleScale = 4;   // px per module for the rasterised symbol

    private Barcode(String type, String data) { this.type = type; this.data = data; }

    public static Barcode qr(String data)         { return new Barcode("qr", data); }
    public static Barcode code128(String data)    { return new Barcode("code128", data); }
    public static Barcode code39(String data)     { return new Barcode("code39", data); }
    public static Barcode ean13(String data)      { return new Barcode("ean13", data); }
    public static Barcode dataMatrix(String data) { return new Barcode("datamatrix", data); }

    /** Any engine-supported symbology by name (e.g. {@code "qr"}, {@code "code128"}). */
    public static Barcode of(String type, String data) { return new Barcode(type, data); }

    /** Pixels per module in the rasterised symbol (crispness); default 4. */
    public Barcode moduleScale(int px) { this.moduleScale = Math.max(1, px); return this; }

    @Override public Box toBox() {
        BarcodeMatrix m = Barcodes.encode(type, data);
        byte[] png = m.isOneDimensional()
                ? BarcodeImages.toPng(m, Math.max(1, moduleScale / 2), 70)
                : BarcodeImages.toPng(m, moduleScale);
        DecodedImage img = ImageDecoder.decode(png);
        return new ImageBox(img, style);
    }
}
