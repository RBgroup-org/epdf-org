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
package com.epdfengine.rb.org.runtime;

/**
 * Per-job resource ceilings. These bound the work a single render can consume so one
 * hostile or malformed input cannot exhaust the JVM (defence against decompression
 * bombs, entity expansion, runaway layouts) — a prerequisite for running the engine as
 * a shared, multi-tenant service. All values are primitives to avoid per-check boxing.
 *
 * <p>A limit of {@code 0} (or negative) disables that particular check.</p>
 */
public record ResourceLimits(
        long maxInputBytes,       // HTML/XML source size
        long maxResourceBytes,    // any single fetched font/image
        long maxImagePixels,      // decoded image area (w * h)
        int  maxDrawables,        // laid-out primitive count (runaway-layout guard)
        long jobTimeoutMillis) {  // wall-clock budget per job

    /** Conservative defaults suited to a shared service (32 MB input, 64 MB/asset, 40 MP, 30 s). */
    public static ResourceLimits defaults() {
        return new ResourceLimits(
                32L * 1024 * 1024,
                64L * 1024 * 1024,
                40_000_000L,
                5_000_000,
                30_000L);
    }

    /** Unbounded limits — for trusted, single-tenant embedding where no capping is wanted. */
    public static ResourceLimits unbounded() {
        return new ResourceLimits(0, 0, 0, 0, 0);
    }

    public ResourceLimits withTimeoutMillis(long ms) {
        return new ResourceLimits(maxInputBytes, maxResourceBytes, maxImagePixels, maxDrawables, ms);
    }

    public ResourceLimits withMaxInputBytes(long bytes) {
        return new ResourceLimits(bytes, maxResourceBytes, maxImagePixels, maxDrawables, jobTimeoutMillis);
    }

    /** Validates the input source size; throws {@link ResourceLimitException} when exceeded. */
    public void checkInputBytes(long bytes) {
        if (maxInputBytes > 0 && bytes > maxInputBytes) {
            throw new ResourceLimitException("input " + bytes + " B exceeds limit " + maxInputBytes + " B");
        }
    }

    /** Validates a fetched resource size (font/image); throws when exceeded. */
    public void checkResourceBytes(long bytes) {
        if (maxResourceBytes > 0 && bytes > maxResourceBytes) {
            throw new ResourceLimitException("resource " + bytes + " B exceeds limit " + maxResourceBytes + " B");
        }
    }

    /** Validates a decoded image area in pixels; throws when exceeded. */
    public void checkImagePixels(long pixels) {
        if (maxImagePixels > 0 && pixels > maxImagePixels) {
            throw new ResourceLimitException("image " + pixels + " px exceeds limit " + maxImagePixels + " px");
        }
    }
}
