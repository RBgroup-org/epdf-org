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

import com.epdfengine.rb.org.core.ResourceLoader;

import java.util.Objects;

/**
 * A {@link ResourceLoader} decorator that caches resolved resource bytes in a shared
 * {@link ByteBoundedCache}. For a service that renders the same template (logo, fonts, CSS
 * assets) across many requests, this turns repeated file/network reads into memory hits.
 *
 * <p>The cache is safe to share across concurrent jobs. Cached byte arrays are returned by
 * reference and MUST be treated as read-only — the render pipeline never mutates them.
 * {@code null} results (unresolved references) are not cached.</p>
 */
public final class CachingResourceLoader implements ResourceLoader {

    private final ResourceLoader delegate;
    private final ByteBoundedCache<String, byte[]> cache;

    /** Wraps {@code delegate} with a private {@code maxBytes}-bounded cache. */
    public CachingResourceLoader(ResourceLoader delegate, long maxBytes) {
        this(delegate, new ByteBoundedCache<>(maxBytes, b -> b == null ? 0L : b.length));
    }

    /** Wraps {@code delegate} using a caller-supplied (possibly shared) cache. */
    public CachingResourceLoader(ResourceLoader delegate, ByteBoundedCache<String, byte[]> cache) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    @Override
    public byte[] load(String reference) {
        if (reference == null) return null;
        return cache.computeIfAbsent(reference, delegate::load);
    }

    public ByteBoundedCache.Stats cacheStats() { return cache.stats(); }
    public void clearCache() { cache.clear(); }
}
