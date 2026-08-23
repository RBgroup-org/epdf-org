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
package com.epdfengine.rb.org.pdfa;

/** Builds the XMP metadata packet (with the pdfaid / pdfuaid identification schemas). */
final class XmpMetadata {

    private XmpMetadata() {}

    static String build(Conformance c, DocumentMetadata m, String xmpDate) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n");
        sb.append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n");
        sb.append(" <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n");
        sb.append("  <rdf:Description rdf:about=\"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n");
        sb.append("   <dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">").append(esc(m.title())).append("</rdf:li></rdf:Alt></dc:title>\n");
        sb.append("   <dc:creator><rdf:Seq><rdf:li>").append(esc(m.author())).append("</rdf:li></rdf:Seq></dc:creator>\n");
        sb.append("   <dc:description><rdf:Alt><rdf:li xml:lang=\"x-default\">").append(esc(m.subject())).append("</rdf:li></rdf:Alt></dc:description>\n");
        sb.append("  </rdf:Description>\n");
        sb.append("  <rdf:Description rdf:about=\"\" xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\">\n");
        sb.append("   <xmp:CreatorTool>").append(esc(m.producer())).append("</xmp:CreatorTool>\n");
        sb.append("   <xmp:CreateDate>").append(xmpDate).append("</xmp:CreateDate>\n");
        sb.append("   <xmp:ModifyDate>").append(xmpDate).append("</xmp:ModifyDate>\n");
        sb.append("  </rdf:Description>\n");
        sb.append("  <rdf:Description rdf:about=\"\" xmlns:pdf=\"http://ns.adobe.com/pdf/1.3/\">\n");
        sb.append("   <pdf:Producer>").append(esc(m.producer())).append("</pdf:Producer>\n");
        sb.append("  </rdf:Description>\n");
        if (c.isPdfA()) {
            sb.append("  <rdf:Description rdf:about=\"\" xmlns:pdfaid=\"http://www.aiim.org/pdfa/ns/id/\">\n");
            sb.append("   <pdfaid:part>").append(c.pdfaPart()).append("</pdfaid:part>\n");
            sb.append("   <pdfaid:conformance>").append(c.pdfaConformanceLevel()).append("</pdfaid:conformance>\n");
            sb.append("  </rdf:Description>\n");
        }
        if (c.isPdfUa()) {
            sb.append("  <rdf:Description rdf:about=\"\" xmlns:pdfuaid=\"http://www.aiim.org/pdfua/ns/id/\">\n");
            sb.append("   <pdfuaid:part>1</pdfuaid:part>\n");
            sb.append("  </rdf:Description>\n");
        }
        sb.append(" </rdf:RDF>\n</x:xmpmeta>\n");
        sb.append(" ".repeat(200)).append('\n');       // padding whitespace (recommended for in-place edits)
        sb.append("<?xpacket end=\"w\"?>");
        return sb.toString();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
