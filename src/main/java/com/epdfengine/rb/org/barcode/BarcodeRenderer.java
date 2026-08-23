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
 * Draws a {@link BarcodeMatrix} as crisp vector rectangles. The barcode package
 * stays free of any PDF/render dependency: callers pass a {@link RectFiller}
 * (e.g. {@code ContentStreamBuilder}'s fillRect bound to black) so the render
 * layer owns the actual content-stream emission.
 */
public final class BarcodeRenderer {

    /** Sink for one filled (dark) rectangle, in points with a top-left origin. */
    @FunctionalInterface
    public interface RectFiller {
        void fill(double xPt, double yTopPt, double wPt, double hPt);
    }

    private BarcodeRenderer() {}

    /**
     * Draws {@code m} at ({@code xPt},{@code yTopPt}). For 1D symbologies each dark
     * bar spans {@code barHeightPt}; for 2D each dark module is a {@code moduleSizePt}
     * square. The quiet zone is left blank (assumed light page). Adjacent dark modules
     * in a row are merged into a single rectangle to minimise content-stream ops.
     */
    public static void draw(BarcodeMatrix m, double xPt, double yTopPt,
                            double moduleSizePt, double barHeightPt, RectFiller sink) {
        int q = m.quietZone();
        boolean oneD = m.isOneDimensional();
        for (int row = 0; row < m.height(); row++) {
            double rowTop = oneD ? yTopPt : yTopPt + (q + row) * moduleSizePt;
            double rowH = oneD ? barHeightPt : moduleSizePt;
            int col = 0;
            while (col < m.width()) {
                if (!m.dark(col, row)) { col++; continue; }
                int start = col;
                while (col < m.width() && m.dark(col, row)) col++;
                double x = xPt + (q + start) * moduleSizePt;
                sink.fill(x, rowTop, (col - start) * moduleSizePt, rowH);
            }
        }
    }

    /** Total drawn width in points, including both quiet zones. */
    public static double widthPt(BarcodeMatrix m, double moduleSizePt) {
        return (m.width() + 2 * m.quietZone()) * moduleSizePt;
    }

    /** Total drawn height in points (1D uses barHeightPt; 2D includes quiet zones). */
    public static double heightPt(BarcodeMatrix m, double moduleSizePt, double barHeightPt) {
        return m.isOneDimensional() ? barHeightPt : (m.height() + 2 * m.quietZone()) * moduleSizePt;
    }
}
