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
 * A symbology-independent grid of black/white modules produced by a barcode
 * encoder. 1D symbologies produce a single logical row (drawn at a fixed bar
 * height); 2D symbologies produce a square matrix. A {@code quietZone} (in
 * modules) is recorded so renderers can add the mandatory light margin.
 */
public final class BarcodeMatrix {

    private final boolean[][] modules;   // [row][col]; true = dark
    private final int width;
    private final int height;
    private final int quietZone;

    public BarcodeMatrix(boolean[][] modules, int quietZone) {
        this.modules = modules;
        this.height = modules.length;
        this.width = modules.length == 0 ? 0 : modules[0].length;
        this.quietZone = quietZone;
    }

    /** Builds a 1-row matrix from a bar/space module array (true = dark bar). */
    public static BarcodeMatrix ofRow(boolean[] row, int quietZone) {
        return new BarcodeMatrix(new boolean[][] { row }, quietZone);
    }

    public int width()  { return width; }
    public int height() { return height; }
    public int quietZone() { return quietZone; }
    public boolean isOneDimensional() { return height == 1; }

    public boolean dark(int col, int row) {
        return row >= 0 && row < height && col >= 0 && col < width && modules[row][col];
    }
}
