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
package com.epdfengine.rb.org.core;

import java.util.Map;
import java.util.Objects;

/**
 * A {@link ResourceLoader} that serves bytes already fetched by {@link ResourcePrefetcher},
 * falling back to a delegate for anything not pre-loaded. Layout runs on a CPU worker and never
 * blocks on I/O for a prefetched reference.
 */
public final class PrefetchedResourceLoader implements ResourceLoader {

    private final Map<String, byte[]> prefetched;
    private final ResourceLoader delegate;

    public PrefetchedResourceLoader(Map<String, byte[]> prefetched, ResourceLoader delegate) {
        this.prefetched = Objects.requireNonNull(prefetched, "prefetched");
        this.delegate = delegate;
    }

    @Override
    public byte[] load(String reference) {
        if (reference == null) return null;
        byte[] hit = prefetched.get(reference);
        if (hit != null) return hit;
        return delegate != null ? delegate.load(reference) : null;
    }
}
