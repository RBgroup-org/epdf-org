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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches a document's external resources <b>concurrently</b> before layout. On Java 21 this runs
 * on the engine's virtual-thread pool, so a page with N remote images/fonts pays roughly one
 * network round-trip instead of N sequential ones — the payoff that makes virtual threads worth
 * the move. Layout then reads the pre-loaded bytes through a {@link PrefetchedResourceLoader}.
 */
public final class ResourcePrefetcher {

    private static final Pattern IMG_SRC = Pattern.compile("(?is)<img\\b[^>]*\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern CSS_URL = Pattern.compile("(?i)url\\(\\s*[\"']?([^\"')]+)[\"']?\\s*\\)");

    private ResourcePrefetcher() {}

    /** Extracts referenced resources ({@code <img src>} and CSS {@code url(...)}), skipping {@code data:} URIs. */
    public static Set<String> scanReferences(String html) {
        Set<String> refs = new LinkedHashSet<>();
        if (html == null) return refs;
        collect(IMG_SRC.matcher(html), refs);
        collect(CSS_URL.matcher(html), refs);
        return refs;
    }

    private static void collect(Matcher m, Set<String> out) {
        while (m.find()) {
            String ref = m.group(1).trim();
            if (!ref.isEmpty() && !ref.startsWith("data:")) out.add(ref);
        }
    }

    /**
     * Loads every reference concurrently on {@code io} and returns the successful results. Failures
     * are dropped (layout falls back to loading them normally / skipping the element gracefully).
     */
    public static Map<String, byte[]> prefetch(Set<String> refs, ResourceLoader loader, ExecutorService io) {
        Map<String, byte[]> out = new ConcurrentHashMap<>();
        if (refs == null || refs.isEmpty() || loader == null) return out;
        List<Future<?>> futures = new ArrayList<>(refs.size());
        for (String ref : refs) {
            futures.add(io.submit(() -> {
                try {
                    byte[] bytes = loader.load(ref);
                    if (bytes != null) out.put(ref, bytes);
                } catch (RuntimeException ignored) { /* fall back to normal load at layout time */ }
            }));
        }
        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception ignored) { }
        }
        return out;
    }

    /** Convenience: scan {@code html}, prefetch on {@code io}, and return a caching loader over {@code loader}. */
    public static ResourceLoader prefetching(String html, ResourceLoader loader, ExecutorService io) {
        Map<String, byte[]> pre = prefetch(scanReferences(html), loader, io);
        return new PrefetchedResourceLoader(pre, loader);
    }
}
