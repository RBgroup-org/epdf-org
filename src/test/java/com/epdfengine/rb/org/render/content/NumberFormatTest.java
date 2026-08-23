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
package com.epdfengine.rb.org.render.content;

import com.epdfengine.rb.org.testkit.Assert;

import java.util.Locale;
import java.util.Random;

/**
 * Verifies the hand-rolled coordinate formatter matches {@code "%.3f"} numerically and is
 * substantially faster than {@link String#format} (the per-coordinate hot path).
 */
public final class NumberFormatTest {

    public static void main(String[] args) {
        parity();
        speed();
        System.out.println("PASS — content-stream number formatter (parity + speed)");
    }

    private static void parity() {
        int checked = 0, exact = 0;
        // Coordinate-like grid: fine steps across a page, plus larger values and negatives.
        for (int i = -5000; i <= 200000; i++) {
            double v = i * 0.001;
            checked++;
            if (matchExact(v)) exact++;
            assertValid(v);
        }
        for (double v : new double[]{0, -0.0, 1, 595.28, 841.89, 14400, 1_000_000, 0.0005, 2.675, 0.1245}) {
            assertValid(v);
        }
        Random rnd = new Random(42);
        for (int i = 0; i < 200_000; i++) {
            double v = (rnd.nextDouble() - 0.5) * 30_000;
            assertValid(v);
        }
        System.out.println("  parity: " + exact + "/" + checked + " byte-identical to %.3f on the coordinate grid");
        Assert.isTrue(exact >= checked - 5, "coordinate grid essentially byte-identical to %.3f");
    }

    /** The formatter must produce a valid 3-decimal (or integer) string that rounds to the same value. */
    private static void assertValid(double v) {
        String s = ContentStreamBuilder.formatNumber(v);
        if (v == Math.rint(v) && !Double.isInfinite(v)) {
            Assert.isTrue(s.matches("-?\\d+"), "integer format: " + v + " -> " + s);
        } else {
            Assert.isTrue(s.matches("-?\\d+\\.\\d{3}"), "3-decimal format: " + v + " -> " + s);
            double back = Double.parseDouble(s);
            Assert.isTrue(Math.abs(back - v) <= 0.0006, "rounds to same 3dp: " + v + " -> " + s);
        }
    }

    private static boolean matchExact(double v) {
        String ref = (v == Math.rint(v) && !Double.isInfinite(v))
                ? Long.toString((long) v)
                : String.format(Locale.ROOT, "%.3f", v);
        return ContentStreamBuilder.formatNumber(v).equals(ref);
    }

    private static void speed() {
        int n = 2_000_000;
        double[] samples = new double[1024];
        Random rnd = new Random(7);
        for (int i = 0; i < samples.length; i++) samples[i] = (rnd.nextDouble() - 0.5) * 2000;

        // Warm up both paths.
        long sink = 0;
        for (int w = 0; w < 100_000; w++) {
            sink += ContentStreamBuilder.formatNumber(samples[w & 1023]).length();
            sink += String.format(Locale.ROOT, "%.3f", samples[w & 1023]).length();
        }

        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) sink += ContentStreamBuilder.formatNumber(samples[i & 1023]).length();
        long fast = System.nanoTime() - t0;

        long t1 = System.nanoTime();
        for (int i = 0; i < n; i++) sink += String.format(Locale.ROOT, "%.3f", samples[i & 1023]).length();
        long slow = System.nanoTime() - t1;

        System.out.println("  speed: fmt=" + (fast / 1_000_000) + "ms  String.format=" + (slow / 1_000_000)
                + "ms  speedup=" + String.format(Locale.ROOT, "%.1fx", (double) slow / fast) + " (sink=" + sink + ")");
        Assert.isTrue(fast < slow, "hand-rolled formatter is faster than String.format");
    }
}
