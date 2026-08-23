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

import com.epdfengine.rb.org.testkit.Assert;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Debug-mode pipeline trace: stage timing, failure capture, and external-file sink. */
public final class TraceTest {

    private static final String HTML = "<html><body><h1>Trace</h1><p>content</p></body></html>";

    public static void main(String[] args) throws Exception {
        unitTrace();
        integrationTrace();
        fileSink();
        System.out.println("PASS — debug pipeline trace (stages + failure capture + external file)");
    }

    private static void unitTrace() {
        PipelineTrace t = new PipelineTrace("job-x");
        t.enter("layout"); t.exit();
        t.enter("serialize"); t.fail(new IllegalStateException("boom"));
        Assert.isTrue(t.failed(), "trace records failure");
        Assert.equals(2, t.stages().size(), "two stages recorded");
        Assert.isTrue(t.stages().get(0).ok() && !t.stages().get(1).ok(), "layout ok, serialize failed");
        Assert.contains(t.toString(), "boom", "error message captured");
        Assert.contains(t.toString(), "FAIL serialize", "failing stage pinpointed");
    }

    private static void integrationTrace() {
        List<PipelineTrace> captured = new CopyOnWriteArrayList<>();
        TraceSink sink = captured::add;
        try (Epdf engine = Epdf.create(EngineConfig.defaults().withTraceSink(sink))) {
            engine.render(RenderRequest.of(HTML).build());
        }
        Assert.equals(1, captured.size(), "one trace captured for one render");
        PipelineTrace t = captured.get(0);
        Assert.isTrue(!t.failed(), "successful render is not marked failed");
        List<String> names = new ArrayList<>();
        for (PipelineTrace.Stage s : t.stages()) names.add(s.name());
        Assert.isTrue(names.contains("layout") && names.contains("serialize"),
                "layout + serialize stages traced: " + names);
    }

    private static void fileSink() throws Exception {
        Path p = Files.createTempFile("epdf-trace", ".log");
        try (Epdf engine = Epdf.create(EngineConfig.defaults().withTraceSink(TraceSink.toFile(p)))) {
            engine.render(RenderRequest.of(HTML).build());
        }
        String log = Files.readString(p);
        Assert.contains(log, "layout", "external log has the layout stage");
        Assert.contains(log, "serialize", "external log has the serialize stage");
        Files.deleteIfExists(p);
    }
}
