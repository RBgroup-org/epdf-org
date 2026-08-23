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

import com.epdfengine.rb.org.common.Deflate;
import com.epdfengine.rb.org.kernel.object.PdfArray;
import com.epdfengine.rb.org.kernel.object.PdfDictionary;
import com.epdfengine.rb.org.kernel.object.PdfIndirectReference;
import com.epdfengine.rb.org.kernel.object.PdfName;
import com.epdfengine.rb.org.kernel.object.PdfNumber;
import com.epdfengine.rb.org.kernel.object.PdfObject;
import com.epdfengine.rb.org.kernel.object.PdfStream;
import com.epdfengine.rb.org.kernel.object.PdfString;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Streaming PDF writer. Emits the file header, each indirect object in order, a
 * classic cross-reference table, and the trailer — writing directly to the target
 * {@link OutputStream} while retaining only the byte offsets (not the whole file)
 * in memory.
 *
 * <p>M1 uses a classic xref table for maximum reader compatibility. Compressed
 * cross-reference streams and object streams ({@code /XRef} + {@code /ObjStm})
 * are added in the {@code optimize} milestone via an xref-format strategy.</p>
 */
public final class PdfWriter {

    private static final byte[] HEADER = {
            '%','P','D','F','-','1','.','7','\n',
            // Binary comment so tools treat the file as binary.
            '%',(byte)0xE2,(byte)0xE3,(byte)0xCF,(byte)0xD3,'\n'
    };

    /**
     * Writes {@code objects} (object number = index + 1, all generation 0) as a
     * complete PDF whose trailer {@code /Root} points at {@code rootObjectNumber}.
     *
     * @param fileId 16-byte file identifier for the trailer {@code /ID}
     */
    public void write(OutputStream target, List<PdfObject> objects,
                      int rootObjectNumber, int infoObjectNumber, byte[] fileId) throws IOException {
        CountingOutputStream out = new CountingOutputStream(target);
        out.write(HEADER);

        int count = objects.size();
        long[] offset = new long[count + 1]; // offset[objNumber]
        for (int i = 0; i < count; i++) {
            int number = i + 1;
            offset[number] = out.count();
            ObjectSerializer.writeIndirect(out, number, 0, objects.get(i));
        }

        long xrefStart = out.count();
        ascii(out, "xref\n");
        ascii(out, "0 " + (count + 1) + "\n");
        ascii(out, "0000000000 65535 f \n");
        for (int number = 1; number <= count; number++) {
            ascii(out, pad10(offset[number]) + " 00000 n \n");
        }

        StringBuilder trailer = new StringBuilder(160);
        trailer.append("trailer\n<< /Size ").append(count + 1)
               .append(" /Root ").append(rootObjectNumber).append(" 0 R");
        if (infoObjectNumber > 0) {
            trailer.append(" /Info ").append(infoObjectNumber).append(" 0 R");
        }
        String id = hex(fileId);
        trailer.append(" /ID [<").append(id).append("> <").append(id).append(">]");
        trailer.append(" >>\nstartxref\n").append(xrefStart).append("\n%%EOF\n");
        ascii(out, trailer.toString());
        out.flush();
    }

    /**
     * Writes a <b>compressed</b> PDF (ISO 32000-1 §7.5.7/§7.5.8): non-stream objects are packed
     * into a Flate-compressed object stream ({@code /ObjStm}) and the cross-reference is emitted
     * as a compressed {@code /XRef} stream instead of a classic table + trailer. This is smaller
     * and faster to parse; it requires PDF 1.5+ readers (all modern ones). Stream objects stay as
     * regular indirect objects (they cannot live inside an object stream).
     */
    public void writeCompressed(OutputStream target, List<PdfObject> objects,
                                int rootObjectNumber, int infoObjectNumber, byte[] fileId) throws IOException {
        CountingOutputStream out = new CountingOutputStream(target);
        out.write(HEADER);

        int n = objects.size();                          // existing objects: numbers 1..n
        List<Integer> packable = new ArrayList<>();      // non-stream object numbers → into /ObjStm
        for (int i = 0; i < n; i++) {
            if (!(objects.get(i) instanceof PdfStream)) packable.add(i + 1);
        }
        boolean hasObjStm = !packable.isEmpty();
        int objStmNum = hasObjStm ? n + 1 : -1;
        int xrefNum   = hasObjStm ? n + 2 : n + 1;
        int maxNum    = xrefNum;

        long[] offset      = new long[maxNum + 1];       // type-1 byte offsets
        int[]  xtype       = new int[maxNum + 1];        // 0 free / 1 uncompressed / 2 in ObjStm
        int[]  objStmIndex = new int[maxNum + 1];        // type-2 index within the ObjStm

        // 1) Regular (stream) objects written uncompressed.
        for (int i = 0; i < n; i++) {
            if (objects.get(i) instanceof PdfStream) {
                int number = i + 1;
                offset[number] = out.count();
                xtype[number] = 1;
                ObjectSerializer.writeIndirect(out, number, 0, objects.get(i));
            }
        }

        // 2) Pack the non-stream objects into one Flate-compressed /ObjStm.
        if (hasObjStm) {
            ByteArrayOutputStream data = new ByteArrayOutputStream(4096);
            StringBuilder head = new StringBuilder(packable.size() * 8);
            for (int k = 0; k < packable.size(); k++) {
                int number = packable.get(k);
                head.append(number).append(' ').append(data.size()).append(' ');
                ObjectSerializer.writeValue(data, objects.get(number - 1));
                data.write('\n');
                xtype[number] = 2;
                objStmIndex[number] = k;
            }
            byte[] headBytes = head.toString().getBytes(StandardCharsets.US_ASCII);
            ByteArrayOutputStream content = new ByteArrayOutputStream(headBytes.length + data.size());
            content.write(headBytes);
            data.writeTo(content);
            byte[] compressed = Deflate.deflate(content.toByteArray());

            PdfDictionary dict = new PdfDictionary();
            dict.put(PdfName.TYPE, PdfName.OBJ_STM);
            dict.put(PdfName.N, PdfNumber.of(packable.size()));
            dict.put(PdfName.FIRST, PdfNumber.of(headBytes.length));
            dict.put(PdfName.FILTER, PdfName.FLATE_DECODE);

            offset[objStmNum] = out.count();
            xtype[objStmNum] = 1;
            ObjectSerializer.writeIndirect(out, objStmNum, 0, new PdfStream(dict, compressed));
        }

        // 3) The /XRef stream is the last object and references its own offset.
        long xrefStart = out.count();
        offset[xrefNum] = xrefStart;
        xtype[xrefNum] = 1;

        final int w1 = 1, w2 = 4, w3 = 2;                // type, field2, field3 widths
        ByteArrayOutputStream body = new ByteArrayOutputStream((maxNum + 1) * (w1 + w2 + w3));
        for (int number = 0; number <= maxNum; number++) {
            if (number == 0) {                            // free list head
                writeField(body, 0, w1); writeField(body, 0, w2); writeField(body, 65535, w3);
            } else if (xtype[number] == 2) {
                writeField(body, 2, w1); writeField(body, objStmNum, w2); writeField(body, objStmIndex[number], w3);
            } else {                                      // type 1 (uncompressed)
                writeField(body, 1, w1); writeField(body, offset[number], w2); writeField(body, 0, w3);
            }
        }
        byte[] xrefCompressed = Deflate.deflate(body.toByteArray());

        PdfDictionary xd = new PdfDictionary();
        xd.put(PdfName.TYPE, PdfName.XREF);
        xd.put(PdfName.SIZE, PdfNumber.of(maxNum + 1));
        xd.put(PdfName.W, new PdfArray().addNumber((long) w1).addNumber((long) w2).addNumber((long) w3));
        xd.put(PdfName.INDEX, new PdfArray().addNumber(0L).addNumber((long) (maxNum + 1)));
        xd.put(PdfName.ROOT, new PdfIndirectReference(rootObjectNumber, 0));
        if (infoObjectNumber > 0) xd.put(PdfName.INFO, new PdfIndirectReference(infoObjectNumber, 0));
        xd.put(PdfName.ID, new PdfArray().add(PdfString.ofHex(fileId)).add(PdfString.ofHex(fileId)));
        xd.put(PdfName.FILTER, PdfName.FLATE_DECODE);
        ObjectSerializer.writeIndirect(out, xrefNum, 0, new PdfStream(xd, xrefCompressed));

        ascii(out, "startxref\n" + xrefStart + "\n%%EOF\n");
        out.flush();
    }

    /** Appends {@code value} as a big-endian field of {@code width} bytes (xref-stream entry). */
    private static void writeField(ByteArrayOutputStream out, long value, int width) {
        for (int i = width - 1; i >= 0; i--) out.write((int) ((value >>> (8 * i)) & 0xFF));
    }

    private static String pad10(long v) {
        char[] c = new char[10];
        for (int i = 9; i >= 0; i--) { c[i] = (char) ('0' + (int) (v % 10)); v /= 10; }
        return new String(c);
    }

    private static void ascii(OutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.US_ASCII));
    }

    private static String hex(byte[] data) {
        char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(hex[(b >> 4) & 0xF]).append(hex[b & 0xF]);
        }
        return sb.toString();
    }

    /** Wraps an output stream and counts bytes written, for xref offsets. */
    private static final class CountingOutputStream extends FilterOutputStream {
        private long count;

        CountingOutputStream(OutputStream out) { super(out); }

        long count() { return count; }

        @Override public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }
    }
}
