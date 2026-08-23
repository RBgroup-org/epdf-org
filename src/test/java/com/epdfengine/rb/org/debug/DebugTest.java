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
package com.epdfengine.rb.org.debug;

import com.epdfengine.rb.org.runtime.Epdf;
import com.epdfengine.rb.org.runtime.EngineConfig;
import com.epdfengine.rb.org.runtime.RenderRequest;
import com.epdfengine.rb.org.testkit.Assert;

import java.nio.file.Files;
import java.nio.file.Path;

/** Verifies debug is off by default and enabled by an explicit function call. */
public final class DebugTest {

    private static final String HTML = "<html><body><h1>Debug</h1><p>content</p></body></html>";

    public static void main(String[] args) throws Exception {
        Assert.isTrue(!EngineConfig.defaults().debugEnabled(), "debug is OFF by default");

        Path log = Files.createTempFile("epdf-debug", ".log");
        EngineConfig cfg = Debug.enabled(log);                 // <-- enable debug by function call
        Assert.isTrue(cfg.debugEnabled(), "debug enabled by Debug.enabled(path)");

        try (Epdf engine = Epdf.create(cfg)) {
            engine.render(RenderRequest.of(HTML).build());
        }
        String written = Files.readString(log);
        Assert.contains(written, "layout", "debug trace written to the external log file");
        Assert.contains(written, "serialize", "trace has the serialize stage");
        Files.deleteIfExists(log);
        System.out.println("PASS — debug enable-by-function-call to external log");
    }
}
