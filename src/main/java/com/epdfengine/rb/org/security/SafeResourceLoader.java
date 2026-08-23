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
package com.epdfengine.rb.org.security;

import com.epdfengine.rb.org.core.ResourceLoader;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A {@link ResourceLoader} decorator that enforces a {@link NetworkGuard} SSRF policy before
 * delegating the actual fetch. Local/embedded references ({@code data:} URIs and relative names)
 * pass straight through; {@code http}/{@code https} references are validated (scheme + resolved
 * addresses) and other schemes are allowed only if the policy permits them.
 */
public final class SafeResourceLoader implements ResourceLoader {

    private static final Pattern SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");

    private final ResourceLoader delegate;
    private final NetworkGuard guard;

    public SafeResourceLoader(ResourceLoader delegate) {
        this(delegate, new NetworkGuard());
    }

    public SafeResourceLoader(ResourceLoader delegate, NetworkGuard guard) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    @Override
    public byte[] load(String reference) {
        if (reference == null) return null;
        String s = reference.trim();
        if (s.startsWith("data:") || !SCHEME.matcher(s).find()) {
            return delegate.load(reference);   // embedded or relative → no network
        }
        URI uri = URI.create(s);
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals("http") || scheme.equals("https")) {
            guard.checkUri(uri);               // throws on SSRF / disallowed scheme
        } else if (!guard.isSchemeAllowed(scheme)) {
            throw new NetworkGuard.BlockedResourceException("scheme not allowed: " + scheme);
        }
        return delegate.load(reference);
    }
}
