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

import com.epdfengine.rb.org.testkit.Assert;

import java.util.Map;

/** Verifies DITA map key space + {@code keyref} resolution (href + variable text). */
public final class DitaKeyrefTest {

    public static void main(String[] args) {
        String map = "<map><title>Guide</title>"
                + "<keydef keys='pname'><topicmeta><keywords><keyword>ACME PDF</keyword></keywords></topicmeta></keydef>"
                + "<topicref keys='refkey' href='ref.dita'/>"
                + "<topicref href='c.dita'/>"
                + "</map>";
        Map<String, String> topics = Map.of(
                "ref.dita", "<reference id='r'><title>Reference</title><refbody><p>ref body</p></refbody></reference>",
                "c.dita", "<concept id='c'><title>Concept</title><conbody>"
                        + "<p>Product <keyword keyref='pname'/> - see <xref keyref='refkey'/>.</p></conbody></concept>");

        String html = DitaMap.mapToHtml(map, topics::get);

        Assert.contains(html, "ACME PDF", "keyword keyref substituted the key's text");
        Assert.contains(html, "Reference", "referenced topic rendered");
        Assert.contains(html, "href=\"#t1\"", "xref keyref resolved to the key's topic anchor (#t1)");
        System.out.println("PASS — DITA keyref/keys (variable text + key-based links)");
    }
}
