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

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * SSRF defence for outbound resource fetches. Classifies hosts/addresses that must never be
 * reached from a server-side renderer — loopback, link-local (incl. the cloud metadata endpoint
 * {@code 169.254.169.254}), private/site-local ranges, unique-local IPv6, multicast and wildcard —
 * and enforces a scheme allow-list. A host application wires this into its resource loader so a
 * malicious {@code <img src="http://169.254.169.254/…">} cannot exfiltrate instance credentials.
 */
public final class NetworkGuard {

    private final Set<String> allowedSchemes;
    private final boolean allowPrivate;

    /** Default policy: only {@code http}/{@code https}, private networks blocked. */
    public NetworkGuard() {
        this(Set.of("http", "https"), false);
    }

    public NetworkGuard(Set<String> allowedSchemes, boolean allowPrivate) {
        this.allowedSchemes = Set.copyOf(allowedSchemes);
        this.allowPrivate = allowPrivate;
    }

    /** Whether {@code scheme} is permitted by the policy (case-insensitive). */
    public boolean isSchemeAllowed(String scheme) {
        return scheme != null && allowedSchemes.contains(scheme.toLowerCase(Locale.ROOT));
    }

    /**
     * An address is blocked when it is loopback / link-local / site-local / unique-local /
     * multicast / wildcard (unless {@link #allowPrivate} is set). Public addresses are allowed.
     */
    public boolean isBlockedAddress(InetAddress addr) {
        if (addr == null) return true;
        if (allowPrivate) return false;
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                || addr.isMulticastAddress()) {
            return true;
        }
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            int o0 = b[0] & 0xFF, o1 = b[1] & 0xFF;
            if (o0 == 169 && o1 == 254) return true;        // AWS/GCP/Azure metadata range
            if (o0 == 100 && o1 >= 64 && o1 <= 127) return true; // carrier-grade NAT 100.64/10
        } else if (b.length == 16) {
            if ((b[0] & 0xFE) == 0xFC) return true;         // unique-local fc00::/7
        }
        return false;
    }

    /**
     * Validates a fully-qualified URI: scheme must be allowed and <b>every</b> resolved address
     * must be permitted (guards against DNS entries that point at internal IPs). Throws
     * {@link BlockedResourceException} otherwise.
     */
    public void checkUri(URI uri) {
        if (uri == null) throw new BlockedResourceException("null URI");
        String scheme = uri.getScheme();
        if (!isSchemeAllowed(scheme)) {
            throw new BlockedResourceException("scheme not allowed: " + scheme);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new BlockedResourceException("missing host: " + uri);
        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (Exception e) {
            throw new BlockedResourceException("cannot resolve host: " + host);
        }
        for (InetAddress a : resolved) {
            if (isBlockedAddress(a)) {
                throw new BlockedResourceException("host resolves to a blocked address: " + host + " -> " + a.getHostAddress());
            }
        }
    }

    /** Thrown when a resource reference violates the network policy. */
    public static final class BlockedResourceException extends RuntimeException {
        public BlockedResourceException(String message) { super(message); }
    }
}
