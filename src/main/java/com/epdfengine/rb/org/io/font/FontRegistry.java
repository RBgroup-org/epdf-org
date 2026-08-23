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
package com.epdfengine.rb.org.io.font;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A per-document (or per-engine) registry of user-supplied fonts. epdf-org bundles
 * no fonts; callers register their own. Fonts are keyed by family + bold + italic;
 * lookup falls back within the family, then to the first registered font. For a
 * given codepoint, {@link #fontForCodepoint} scans registered fonts by {@code cmap}
 * coverage to enable mixed-font (multilingual) runs.
 *
 * <p>Not thread-safe for concurrent registration; register up front, then read.</p>
 */
public final class FontRegistry {

    private record Key(String family, boolean bold, boolean italic) {}

    private final Map<Key, TrueTypeFont> byKey = new LinkedHashMap<>();
    private final List<TrueTypeFont> all = new ArrayList<>();
    private final Map<String, TrueTypeFont> firstOfFamily = new LinkedHashMap<>();

    /** Registers a parsed font under a family name and style flags. */
    public FontRegistry register(String family, boolean bold, boolean italic, TrueTypeFont font) {
        if (font == null) return this;
        Key key = new Key(norm(family), bold, italic);
        byKey.put(key, font);
        all.add(font);
        firstOfFamily.putIfAbsent(norm(family), font);
        return this;
    }

    /** Parses and registers raw font bytes. */
    public FontRegistry register(String family, boolean bold, boolean italic, byte[] fontBytes) {
        return register(family, bold, italic, TrueTypeFont.parse(fontBytes));
    }

    public boolean isEmpty() { return all.isEmpty(); }

    /** The default font (first registered), or {@code null} if none. */
    public TrueTypeFont defaultFont() { return all.isEmpty() ? null : all.get(0); }

    /**
     * Resolves a font for the requested family/style, degrading gracefully: exact
     * match → any style in the family → default font → {@code null}.
     */
    public TrueTypeFont resolve(String family, boolean bold, boolean italic) {
        String fam = norm(family);
        TrueTypeFont f = byKey.get(new Key(fam, bold, italic));
        if (f != null) return f;
        f = firstOfFamily.get(fam);
        if (f != null) return f;
        // Unknown family (e.g. "Helvetica" when only "Open Sans" is registered): keep the
        // requested weight/style by matching any registered face with the same flags, so
        // bold/italic runs don't silently degrade to the default regular font.
        for (Map.Entry<Key, TrueTypeFont> e : byKey.entrySet()) {
            if (e.getKey().bold() == bold && e.getKey().italic() == italic) return e.getValue();
        }
        return defaultFont();
    }

    /**
     * Chooses a font that actually covers {@code codepoint}: the preferred font if
     * it covers it, otherwise the first registered font that does, else the
     * preferred/default (which will render {@code .notdef}).
     */
    public TrueTypeFont fontForCodepoint(int codepoint, String family, boolean bold, boolean italic) {
        TrueTypeFont preferred = resolve(family, bold, italic);
        if (preferred != null && preferred.covers(codepoint)) return preferred;
        for (TrueTypeFont f : all) {
            if (f.covers(codepoint)) return f;
        }
        return preferred;
    }

    private static String norm(String family) {
        return family == null ? "" : family.trim().toLowerCase(Locale.ROOT);
    }
}
