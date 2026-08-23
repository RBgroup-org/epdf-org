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
package com.epdfengine.rb.org.spi;

/** Receives engine telemetry (counters/timers) so a host can publish to Micrometer/Prometheus. */
@FunctionalInterface
public interface TelemetrySink {
    void record(String metric, long value);

    default void event(String name) { record(name, 1L); }

    TelemetrySink NONE = (metric, value) -> { };
}
