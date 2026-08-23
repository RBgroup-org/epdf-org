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
package com.epdfengine.rb.org.barcode;

/**
 * Public entry point for creating barcodes by symbology name. Returns a
 * symbology-independent {@link BarcodeMatrix} that callers can rasterize
 * ({@link BarcodeImages}) or draw as vectors ({@link BarcodeRenderer}).
 */
public final class Barcodes {

    private Barcodes() {}

    /**
     * Encodes {@code data} using the named symbology. Recognised names (case-
     * insensitive): {@code qr}/{@code qrcode}, {@code code128}/{@code 128},
     * {@code code39}/{@code 39}, {@code ean13}/{@code ean}/{@code upca},
     * {@code datamatrix}/{@code dm}.
     */
    public static BarcodeMatrix encode(String type, String data) {
        if (type == null) throw new IllegalArgumentException("barcode: null type");
        switch (type.trim().toLowerCase()) {
            case "qr": case "qrcode": case "qr-code":
                return QrCode.encode(data, QrCode.Ecc.M);
            case "datamatrix": case "data-matrix": case "dm": case "ecc200":
                return DataMatrix.encode(data);
            case "code128": case "c128": case "128":
                return Code128.encode(data);
            case "code39": case "c39": case "39":
                return Code39.encode(data);
            case "ean13": case "ean": case "ean-13":
                return EAN13.encode(data);
            case "upca": case "upc-a":
                return EAN13.encode("0" + data);          // UPC-A is EAN-13 with a leading zero
            default:
                throw new IllegalArgumentException("barcode: unknown symbology '" + type + "'");
        }
    }
}
