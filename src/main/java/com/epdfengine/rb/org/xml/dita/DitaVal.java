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
package com.epdfengine.rb.org.xml.dita;

import com.epdfengine.rb.org.xml.XmlNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Conditional-processing (profiling) rules for a DITA transform — a clean-room analogue of a
 * DITAVAL filter file. Elements carrying profiling attributes ({@code audience}, {@code platform},
 * {@code product}, {@code otherprops}, {@code props}, {@code rev}, {@code deliveryTarget}) can be
 * excluded from the output. Following the DITA rule, an element is excluded when — for any single
 * profiling attribute — every one of that attribute's values is marked for exclusion.
 *
 * <pre>{@code
 * DitaVal filter = new DitaVal().exclude("audience", "admin").exclude("platform", "linux");
 * String html = DitaTransformer.topicToHtml(xml, DitaTransformer.defaultCss(), filter);
 * }</pre>
 */
public final class DitaVal {

    /** Profiling attributes recognised for conditional filtering (DITA base). */
    static final String[] PROFILE_ATTRS = {
        "audience", "platform", "product", "otherprops", "props", "rev", "deliveryTarget"
    };

    private final Map<String, Set<String>> excluded = new HashMap<>();

    /** Marks a single value of a profiling attribute for exclusion (fluent). */
    public DitaVal exclude(String attr, String value) {
        if (attr != null && value != null && !value.isBlank()) {
            excluded.computeIfAbsent(attr, k -> new HashSet<>()).add(value.trim());
        }
        return this;
    }

    public boolean isEmpty() { return excluded.isEmpty(); }

    /**
     * Whether {@code e} should be filtered out: true if, for any profiling attribute it carries,
     * every space-separated value of that attribute is in this filter's exclude set.
     */
    public boolean isExcluded(XmlNode e) {
        if (excluded.isEmpty() || e == null || !e.isElement()) return false;
        for (String attr : PROFILE_ATTRS) {
            String v = e.attr(attr);
            if (v == null || v.isBlank()) continue;
            Set<String> ex = excluded.get(attr);
            if (ex == null || ex.isEmpty()) continue;
            boolean allExcluded = true;
            for (String tok : v.trim().split("\\s+")) {
                if (!ex.contains(tok)) { allExcluded = false; break; }
            }
            if (allExcluded) return true;
        }
        return false;
    }
}
