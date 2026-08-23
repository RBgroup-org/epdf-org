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
package com.epdfengine.rb.org.kernel.write;

import com.epdfengine.rb.org.kernel.object.PdfArray;
import com.epdfengine.rb.org.kernel.object.PdfBoolean;
import com.epdfengine.rb.org.kernel.object.PdfDictionary;
import com.epdfengine.rb.org.kernel.object.PdfIndirectReference;
import com.epdfengine.rb.org.kernel.object.PdfName;
import com.epdfengine.rb.org.kernel.object.PdfNull;
import com.epdfengine.rb.org.kernel.object.PdfNumber;
import com.epdfengine.rb.org.kernel.object.PdfObject;
import com.epdfengine.rb.org.kernel.object.PdfStream;
import com.epdfengine.rb.org.kernel.object.PdfString;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Serializes {@link PdfObject}s to their ISO 32000-1 textual syntax. Stateless
 * and side-effect free (apart from setting {@code /Length} on a stream it writes),
 * so it is safe to share across threads. Streams must always be indirect objects,
 * hence {@link #writeIndirect} handles them and {@link #writeValue} rejects them.
 */
public final class ObjectSerializer {

    private static final byte[] STREAM_KW    = "\nstream\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ENDSTREAM_KW = "\nendstream".getBytes(StandardCharsets.US_ASCII);

    private ObjectSerializer() {}

    /** Writes a full indirect object: {@code N G obj ... endobj}. */
    public static void writeIndirect(OutputStream out, int number, int generation, PdfObject obj)
            throws IOException {
        ascii(out, number + " " + generation + " obj\n");
        if (obj instanceof PdfStream s) {
            writeStream(out, s);
        } else {
            writeValue(out, obj);
        }
        ascii(out, "\nendobj\n");
    }

    /** Writes the direct (in-line) syntax of a value. Rejects streams. */
    public static void writeValue(OutputStream out, PdfObject obj) throws IOException {
        switch (obj) {
            case PdfNull ignored               -> ascii(out, "null");
            case PdfBoolean b                  -> ascii(out, b.value() ? "true" : "false");
            case PdfNumber n                   -> ascii(out, formatNumber(n));
            case PdfName name                  -> writeName(out, name);
            case PdfString s                   -> writeString(out, s);
            case PdfIndirectReference r        -> ascii(out, r.objectNumber() + " " + r.generation() + " R");
            case PdfArray a                    -> writeArray(out, a);
            case PdfDictionary d               -> writeDictionary(out, d);
            case PdfStream ignored             ->
                    throw new IllegalStateException("A PDF stream must be written as an indirect object");
        }
    }

    private static void writeArray(OutputStream out, PdfArray a) throws IOException {
        out.write('[');
        boolean first = true;
        for (PdfObject item : a) {
            if (!first) out.write(' ');
            writeValue(out, item);
            first = false;
        }
        out.write(']');
    }

    private static void writeDictionary(OutputStream out, PdfDictionary d) throws IOException {
        ascii(out, "<< ");
        for (Map.Entry<PdfName, PdfObject> e : d.entries()) {
            writeName(out, e.getKey());
            out.write(' ');
            writeValue(out, e.getValue());
            out.write(' ');
        }
        ascii(out, ">>");
    }

    private static void writeStream(OutputStream out, PdfStream s) throws IOException {
        byte[] data = s.rawBytes();
        s.dict().put(PdfName.LENGTH, PdfNumber.of(data.length));
        writeDictionary(out, s.dict());
        out.write(STREAM_KW);
        out.write(data);
        out.write(ENDSTREAM_KW);
    }

    private static void writeName(OutputStream out, PdfName name) throws IOException {
        out.write('/');
        byte[] bytes = name.value().getBytes(StandardCharsets.UTF_8);
        for (byte value : bytes) {
            int c = value & 0xFF;
            if (isRegularNameChar(c)) {
                out.write(c);
            } else {
                out.write('#');
                out.write(hexDigit(c >> 4));
                out.write(hexDigit(c & 0x0F));
            }
        }
    }

    private static void writeString(OutputStream out, PdfString s) throws IOException {
        byte[] bytes = s.bytes();
        if (s.isHex()) {
            out.write('<');
            for (byte value : bytes) {
                int c = value & 0xFF;
                out.write(hexDigit(c >> 4));
                out.write(hexDigit(c & 0x0F));
            }
            out.write('>');
        } else {
            out.write('(');
            for (byte value : bytes) {
                int c = value & 0xFF;
                switch (c) {
                    case '\\' -> { out.write('\\'); out.write('\\'); }
                    case '('  -> { out.write('\\'); out.write('('); }
                    case ')'  -> { out.write('\\'); out.write(')'); }
                    case '\n' -> { out.write('\\'); out.write('n'); }
                    case '\r' -> { out.write('\\'); out.write('r'); }
                    case '\t' -> { out.write('\\'); out.write('t'); }
                    default -> {
                        if (c < 0x20 || c > 0x7E) {
                            out.write('\\');
                            out.write('0' + ((c >> 6) & 0x7));
                            out.write('0' + ((c >> 3) & 0x7));
                            out.write('0' + (c & 0x7));
                        } else {
                            out.write(c);
                        }
                    }
                }
            }
            out.write(')');
        }
    }

    /** Formats a number without scientific notation (PDF forbids exponents). */
    static String formatNumber(PdfNumber n) {
        if (n.isInteger()) return Long.toString(n.longValue());
        double v = n.doubleValue();
        if (v == 0.0) return "0";
        BigDecimal bd = BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
        return bd.toPlainString();
    }

    private static boolean isRegularNameChar(int c) {
        if (c < 0x21 || c > 0x7E) return false;
        return switch (c) {
            case '(', ')', '<', '>', '[', ']', '{', '}', '/', '%', '#' -> false;
            default -> true;
        };
    }

    private static int hexDigit(int nibble) {
        return nibble < 10 ? ('0' + nibble) : ('A' + nibble - 10);
    }

    private static void ascii(OutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.US_ASCII));
    }
}
