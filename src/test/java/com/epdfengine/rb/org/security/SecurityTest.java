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

import com.epdfengine.rb.org.common.Deflate;
import com.epdfengine.rb.org.common.Inflate;
import com.epdfengine.rb.org.core.ResourceLoader;
import com.epdfengine.rb.org.testkit.Assert;

import java.net.InetAddress;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

/** SSRF network guard, safe resource loader, and decompression-bomb defence. */
public final class SecurityTest {

    public static void main(String[] args) throws Exception {
        networkGuard();
        safeLoader();
        decompressionBomb();
        System.out.println("PASS — security (SSRF guard + safe loader + decompression bomb)");
    }

    private static void networkGuard() throws Exception {
        NetworkGuard g = new NetworkGuard();
        Assert.isTrue(g.isBlockedAddress(InetAddress.getByName("127.0.0.1")), "loopback blocked");
        Assert.isTrue(g.isBlockedAddress(InetAddress.getByName("169.254.169.254")), "cloud metadata blocked");
        Assert.isTrue(g.isBlockedAddress(InetAddress.getByName("10.0.0.5")), "10/8 private blocked");
        Assert.isTrue(g.isBlockedAddress(InetAddress.getByName("172.16.0.1")), "172.16/12 private blocked");
        Assert.isTrue(g.isBlockedAddress(InetAddress.getByName("192.168.1.1")), "192.168/16 private blocked");
        Assert.isTrue(!g.isBlockedAddress(InetAddress.getByName("8.8.8.8")), "public address allowed");
        Assert.isTrue(g.isSchemeAllowed("https") && !g.isSchemeAllowed("file"), "scheme allow-list");

        boolean blocked = false;
        try { g.checkUri(URI.create("http://169.254.169.254/latest/meta-data/")); }
        catch (NetworkGuard.BlockedResourceException e) { blocked = true; }
        Assert.isTrue(blocked, "metadata URI rejected by checkUri");
    }

    private static void safeLoader() {
        AtomicInteger delegated = new AtomicInteger();
        ResourceLoader base = ref -> { delegated.incrementAndGet(); return ("bytes:" + ref).getBytes(); };
        SafeResourceLoader safe = new SafeResourceLoader(base);

        Assert.isTrue(safe.load("logo.png") != null, "relative reference passes through");
        Assert.isTrue(safe.load("data:text/plain,hi") != null, "data URI passes through");
        Assert.isTrue(safe.load("http://8.8.8.8/x.png") != null, "public URL allowed");

        boolean loopback = false;
        try { safe.load("http://127.0.0.1/secret"); }
        catch (NetworkGuard.BlockedResourceException e) { loopback = true; }
        Assert.isTrue(loopback, "loopback URL blocked");

        boolean fileScheme = false;
        try { safe.load("file:///etc/passwd"); }
        catch (NetworkGuard.BlockedResourceException e) { fileScheme = true; }
        Assert.isTrue(fileScheme, "file scheme blocked by default policy");
    }

    private static void decompressionBomb() {
        byte[] zeros = new byte[2_000_000];          // 2 MB → deflates to a few KB
        byte[] z = Deflate.deflate(zeros);
        Assert.isTrue(z.length < 10_000, "bomb payload is tiny (" + z.length + " B)");

        boolean capped = false;
        try { Inflate.inflate(z, 1_000_000, 0); }    // 1 MB cap < 2 MB output
        catch (Inflate.BombException e) { capped = true; }
        Assert.isTrue(capped, "decompression bomb capped");

        byte[] out = Inflate.inflate(z, 8_000_000, 0);
        Assert.equals(2_000_000L, out.length, "normal inflate correct under an ample cap");
    }
}
