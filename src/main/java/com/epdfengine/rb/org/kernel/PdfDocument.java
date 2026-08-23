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
package com.epdfengine.rb.org.kernel;

import com.epdfengine.rb.org.kernel.object.PdfArray;
import com.epdfengine.rb.org.kernel.object.PdfBoolean;
import com.epdfengine.rb.org.kernel.object.PdfDictionary;
import com.epdfengine.rb.org.kernel.object.PdfIndirectReference;
import com.epdfengine.rb.org.kernel.object.PdfName;
import com.epdfengine.rb.org.kernel.object.PdfNumber;
import com.epdfengine.rb.org.kernel.object.PdfObject;
import com.epdfengine.rb.org.kernel.object.PdfStream;
import com.epdfengine.rb.org.kernel.object.PdfString;
import com.epdfengine.rb.org.kernel.write.PdfWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A writable PDF document: an object table plus the catalog/pages tree. This is
 * the M1 create-and-write surface; the reader ({@code kernel.read}) and richer
 * features layer on top of the same object model.
 *
 * <p>Object numbers are assigned in allocation order (number = index + 1, all
 * generation 0). Not thread-safe: build one document per job/thread, consistent
 * with the engine's per-job state model.</p>
 */
public final class PdfDocument {

    private final List<PdfObject> objects = new ArrayList<>();

    private final PdfDictionary catalog;
    private final PdfDictionary pagesNode;
    private final PdfArray pageKids = new PdfArray();

    private final int catalogNumber;
    private final int pagesNumber;

    // Standard-font registry: font -> resource name (F1, F2...) and object number.
    private final java.util.Map<StandardFont, String> fontResourceNames = new java.util.LinkedHashMap<>();
    private final java.util.Map<StandardFont, Integer> fontObjectNumbers = new java.util.LinkedHashMap<>();
    // Embedded (Type0) fonts built by higher layers: resource name -> Type0 object number.
    private final java.util.Map<String, Integer> embeddedFontResources = new java.util.LinkedHashMap<>();
    // Image XObjects: resource name (Im1..) -> object number.
    private final java.util.Map<String, Integer> imageResources = new java.util.LinkedHashMap<>();
    // Axial (linear-gradient) shadings: resource name (Sh1..) -> object number.
    private final java.util.Map<String, Integer> shadingResources = new java.util.LinkedHashMap<>();
    // Constant-alpha ExtGStates keyed by alpha value: alpha -> resource name; name -> object number.
    private final java.util.Map<Integer, String> alphaGStateNames = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, Integer> extGStateResources = new java.util.LinkedHashMap<>();
    private int nextGsIndex = 1;
    private int nextImageIndex = 1;
    private int nextFontIndex = 1;
    private int nextShadingIndex = 1;

    private PdfDocument() {
        // Reserve fixed numbers for catalog (1) and the page-tree root (2) so page
        // objects can reference the tree root before any page exists.
        this.catalog = new PdfDictionary().put(PdfName.TYPE, PdfName.CATALOG);
        this.catalogNumber = allocate(catalog);
        this.pagesNode = new PdfDictionary().put(PdfName.TYPE, PdfName.PAGES);
        this.pagesNumber = allocate(pagesNode);
        catalog.put(PdfName.PAGES, ref(pagesNumber));
    }

    /** Creates an empty document with a catalog and an empty page tree. */
    public static PdfDocument create() { return new PdfDocument(); }

    /**
     * Registers a standard base font (idempotent) and returns its page-resource
     * name (e.g. {@code F1}) for use in a content stream's {@code Tf} operator.
     */
    public String useStandardFont(StandardFont font) {
        String existing = fontResourceNames.get(font);
        if (existing != null) return existing;
        PdfDictionary fontDict = new PdfDictionary()
                .put(PdfName.TYPE, PdfName.FONT)
                .put(PdfName.SUBTYPE, PdfName.of("Type1"))
                .put(PdfName.of("BaseFont"), PdfName.of(font.baseFont()))
                .put(PdfName.of("Encoding"), PdfName.of("WinAnsiEncoding"));
        int obj = allocate(fontDict);
        String name = "F" + (nextFontIndex++);
        fontResourceNames.put(font, name);
        fontObjectNumbers.put(font, obj);
        return name;
    }

    /**
     * Appends a page of the given size (in PostScript points) with the supplied
     * raw content-stream bytes. Content is FlateDecode-compressed, and all fonts
     * registered so far are attached to the page's resources.
     *
     * @return the new page's object number
     */
    public int addPage(double widthPt, double heightPt, byte[] contentStreamBytes) {
        return addPage(widthPt, heightPt, contentStreamBytes, null);
    }

    /**
     * Adds a page. When {@code structParents} is non-null the page carries a
     * {@code /StructParents} index and {@code /Tabs /S} (structure tab order) —
     * required for tagged / PDF/UA documents.
     *
     * @return the new page's object number
     */
    public int addPage(double widthPt, double heightPt, byte[] contentStreamBytes, Integer structParents) {
        byte[] compressed = com.epdfengine.rb.org.common.Deflate.deflate(
                contentStreamBytes == null ? new byte[0] : contentStreamBytes);
        PdfDictionary streamDict = new PdfDictionary().put(PdfName.FILTER, PdfName.FLATE_DECODE);
        PdfStream content = new PdfStream(streamDict, compressed);
        int contentNumber = allocate(content);

        PdfArray mediaBox = new PdfArray(4);
        mediaBox.addNumber(0L).addNumber(0L).addNumber(widthPt).addNumber(heightPt);

        PdfDictionary resources = new PdfDictionary();
        if (!fontResourceNames.isEmpty() || !embeddedFontResources.isEmpty()) {
            PdfDictionary fonts = new PdfDictionary();
            for (java.util.Map.Entry<StandardFont, String> e : fontResourceNames.entrySet()) {
                fonts.put(PdfName.of(e.getValue()), ref(fontObjectNumbers.get(e.getKey())));
            }
            for (java.util.Map.Entry<String, Integer> e : embeddedFontResources.entrySet()) {
                fonts.put(PdfName.of(e.getKey()), ref(e.getValue()));
            }
            resources.put(PdfName.FONT, fonts);
        }
        if (!imageResources.isEmpty()) {
            PdfDictionary xobjects = new PdfDictionary();
            for (java.util.Map.Entry<String, Integer> e : imageResources.entrySet()) {
                xobjects.put(PdfName.of(e.getKey()), ref(e.getValue()));
            }
            resources.put(PdfName.of("XObject"), xobjects);
        }
        if (!shadingResources.isEmpty()) {
            PdfDictionary shadings = new PdfDictionary();
            for (java.util.Map.Entry<String, Integer> e : shadingResources.entrySet()) {
                shadings.put(PdfName.of(e.getKey()), ref(e.getValue()));
            }
            resources.put(PdfName.of("Shading"), shadings);
        }
        if (!extGStateResources.isEmpty()) {
            PdfDictionary gs = new PdfDictionary();
            for (java.util.Map.Entry<String, Integer> e : extGStateResources.entrySet()) {
                gs.put(PdfName.of(e.getKey()), ref(e.getValue()));
            }
            resources.put(PdfName.of("ExtGState"), gs);
        }

        PdfDictionary page = new PdfDictionary()
                .put(PdfName.TYPE, PdfName.PAGE)
                .put(PdfName.PARENT, ref(pagesNumber))
                .put(PdfName.MEDIA_BOX, mediaBox)
                .put(PdfName.RESOURCES, resources)
                .put(PdfName.CONTENTS, ref(contentNumber));
        if (structParents != null) {
            page.put(PdfName.of("StructParents"), PdfNumber.of(structParents));
            page.put(PdfName.of("Tabs"), PdfName.of("S"));
        }
        int pageNumber = allocate(page);
        pageDictByNum.put(pageNumber, page);
        pageKids.add(ref(pageNumber));
        return pageNumber;
    }

    /** Registers an object and returns its (1-based) object number. */
    public int allocate(PdfObject object) {
        objects.add(object);
        return objects.size();
    }

    /** An indirect reference to the object with the given number (generation 0). */
    public PdfIndirectReference ref(int objectNumber) {
        return PdfIndirectReference.of(objectNumber);
    }

    /** Allocates the next page-resource font name (e.g. {@code F3}); shared with standard fonts. */
    public String nextFontResourceName() {
        return "F" + (nextFontIndex++);
    }

    /**
     * Registers an already-built Type0 font object under a resource name so it is
     * attached to every page's {@code /Font} resources. Called by the font embedder
     * in the render layer (keeps the kernel free of any font-format dependency).
     */
    public void registerFontResource(String resourceName, int type0ObjectNumber) {
        embeddedFontResources.put(resourceName, type0ObjectNumber);
    }

    /** Embeds a JPEG image (DCTDecode, no re-encode) and returns its /XObject resource name. */
    public String addJpegImage(byte[] jpeg, int width, int height, int components) {
        ImgKey key = imageKey('j', width, height, jpeg);
        String cached = imageByHash.get(key);
        if (cached != null) { imageDedupHits++; return cached; }
        PdfDictionary d = imageDict(width, height, colorSpaceForComponents(components), null)
                .put(PdfName.FILTER, PdfName.of("DCTDecode"));
        String name = registerImage(allocate(new PdfStream(d, jpeg)));
        imageByHash.put(key, name);
        return name;
    }

    /** Embeds an 8-bit RGB image (FlateDecode) and returns its resource name. */
    public String addRgbImage(byte[] rgb, int width, int height) {
        ImgKey key = imageKey('r', width, height, rgb);
        String cached = imageByHash.get(key);
        if (cached != null) { imageDedupHits++; return cached; }
        PdfDictionary d = imageDict(width, height, "DeviceRGB", null)
                .put(PdfName.FILTER, PdfName.FLATE_DECODE);
        String name = registerImage(allocate(new PdfStream(d, com.epdfengine.rb.org.common.Deflate.deflate(rgb))));
        imageByHash.put(key, name);
        return name;
    }

    /** Embeds an 8-bit RGB image with an 8-bit alpha soft mask; returns its resource name. */
    public String addRgbaImage(byte[] rgb, byte[] alpha, int width, int height) {
        ImgKey key = imageKey('a', width, height, rgb, alpha);
        String cached = imageByHash.get(key);
        if (cached != null) { imageDedupHits++; return cached; }
        PdfDictionary sm = imageDict(width, height, "DeviceGray", null)
                .put(PdfName.FILTER, PdfName.FLATE_DECODE);
        int smaskNum = allocate(new PdfStream(sm, com.epdfengine.rb.org.common.Deflate.deflate(alpha)));
        PdfDictionary d = imageDict(width, height, "DeviceRGB", ref(smaskNum))
                .put(PdfName.FILTER, PdfName.FLATE_DECODE);
        String name = registerImage(allocate(new PdfStream(d, com.epdfengine.rb.org.common.Deflate.deflate(rgb))));
        imageByHash.put(key, name);
        return name;
    }

    // --- image content-hash dedup (identical source bytes embed once) ---

    private final java.util.Map<ImgKey, String> imageByHash = new java.util.HashMap<>();
    private int imageDedupHits;

    /** Number of embed calls satisfied from an already-embedded identical image. */
    public int imageDedupHits() { return imageDedupHits; }
    /** Number of distinct images actually embedded. */
    public int distinctImageCount() { return imageResources.size(); }

    private record ImgKey(long h1, long h2) {}

    /** A 128-bit content key (two independent hashes) over kind + dimensions + source bytes. */
    private static ImgKey imageKey(char kind, int width, int height, byte[]... parts) {
        long h1 = 0xcbf29ce484222325L;          // FNV-1a
        long h2 = 0x9e3779b97f4a7c15L;          // fmix-style, independent
        for (long v : new long[]{kind, width, height}) {
            for (int s = 0; s < 32; s += 8) { byte b = (byte) (v >>> s); h1 = fnv1a(h1, b); h2 = mix2(h2, b); }
        }
        for (byte[] p : parts) {
            if (p == null) continue;
            for (byte b : p) { h1 = fnv1a(h1, b); h2 = mix2(h2, b); }
        }
        return new ImgKey(h1, h2);
    }

    private static long fnv1a(long h, byte b) { return (h ^ (b & 0xFFL)) * 0x100000001b3L; }
    private static long mix2(long h, byte b) { long x = (h ^ (b & 0xFFL)) * 0xff51afd7ed558ccdL; return x ^ (x >>> 33); }

    private PdfDictionary imageDict(int width, int height, String colorSpace, PdfIndirectReference smask) {
        PdfDictionary d = new PdfDictionary()
                .put(PdfName.TYPE, PdfName.of("XObject"))
                .put(PdfName.SUBTYPE, PdfName.of("Image"))
                .put(PdfName.of("Width"), PdfNumber.of(width))
                .put(PdfName.of("Height"), PdfNumber.of(height))
                .put(PdfName.of("ColorSpace"), PdfName.of(colorSpace))
                .put(PdfName.of("BitsPerComponent"), PdfNumber.of(8));
        if (smask != null) d.put(PdfName.of("SMask"), smask);
        return d;
    }

    private static String colorSpaceForComponents(int components) {
        return switch (components) {
            case 1 -> "DeviceGray";
            case 4 -> "DeviceCMYK";
            default -> "DeviceRGB";
        };
    }

    private String registerImage(int objectNumber) {
        String name = "Im" + (nextImageIndex++);
        imageResources.put(name, objectNumber);
        return name;
    }

    /**
     * Registers (and caches) a constant-alpha graphics state and returns its resource
     * name for the {@code gs} operator. Alpha applies to both fills ({@code /ca}) and
     * strokes ({@code /CA}); used for real transparency (e.g. drop shadows).
     */
    public String gStateForAlpha(double alpha) {
        double a = Math.max(0, Math.min(1, alpha));
        int key = (int) Math.round(a * 1000);
        String existing = alphaGStateNames.get(key);
        if (existing != null) return existing;
        double av = key / 1000.0;
        PdfDictionary gs = new PdfDictionary()
                .put(PdfName.TYPE, PdfName.of("ExtGState"))
                .put(PdfName.of("ca"), PdfNumber.of(av))
                .put(PdfName.of("CA"), PdfNumber.of(av));
        int num = allocate(gs);
        String name = "GS" + (nextGsIndex++);
        alphaGStateNames.put(key, name);
        extGStateResources.put(name, num);
        return name;
    }

    /**
     * Builds a Type-2 (axial) shading between two device-space points and registers
     * it as a page {@code /Shading} resource; returns its name (e.g. {@code Sh1})
     * for use with the {@code sh} operator inside a clipped region.
     *
     * <p>Each stop is {@code {position, r, g, b}} with {@code position} in 0..1 and
     * colour components in 0..1. Two stops emit a single exponential (Type-2)
     * colour function; more stops emit a Type-3 stitching function so multi-stop
     * CSS gradients reproduce faithfully. Both ends extend past the gradient line
     * so the whole clipped box is painted.</p>
     */
    public String addAxialShading(double x0, double y0, double x1, double y1, double[][] stops) {
        int fn = buildGradientFunction(stops);
        PdfArray coords = new PdfArray(4);
        coords.addNumber(x0).addNumber(y0).addNumber(x1).addNumber(y1);
        PdfArray extend = new PdfArray(2);
        extend.add(PdfBoolean.TRUE).add(PdfBoolean.TRUE);
        PdfArray domain = new PdfArray(2);
        domain.addNumber(0L).addNumber(1L);
        PdfDictionary shading = new PdfDictionary()
                .put(PdfName.of("ShadingType"), PdfNumber.of(2))
                .put(PdfName.of("ColorSpace"), PdfName.of("DeviceRGB"))
                .put(PdfName.of("Coords"), coords)
                .put(PdfName.of("Domain"), domain)
                .put(PdfName.of("Function"), ref(fn))
                .put(PdfName.of("Extend"), extend);
        int num = allocate(shading);
        String name = "Sh" + (nextShadingIndex++);
        shadingResources.put(name, num);
        return name;
    }

    /** Registers a Type-3 (radial) shading centred at (cx,cy) fading out to {@code radius}. */
    public String addRadialShading(double cx, double cy, double radius, double[][] stops) {
        int fn = buildGradientFunction(stops);
        PdfArray coords = new PdfArray(6);
        coords.addNumber(cx).addNumber(cy).addNumber(0L).addNumber(cx).addNumber(cy).addNumber(radius);
        PdfArray extend = new PdfArray(2);
        extend.add(PdfBoolean.TRUE).add(PdfBoolean.TRUE);
        PdfArray domain = new PdfArray(2);
        domain.addNumber(0L).addNumber(1L);
        PdfDictionary shading = new PdfDictionary()
                .put(PdfName.of("ShadingType"), PdfNumber.of(3))
                .put(PdfName.of("ColorSpace"), PdfName.of("DeviceRGB"))
                .put(PdfName.of("Coords"), coords)
                .put(PdfName.of("Domain"), domain)
                .put(PdfName.of("Function"), ref(fn))
                .put(PdfName.of("Extend"), extend);
        int num = allocate(shading);
        String name = "Sh" + (nextShadingIndex++);
        shadingResources.put(name, num);
        return name;
    }

    /**
     * Registers a Type-4 free-form Gouraud triangle-mesh shading. The vertex data
     * stream packs, per vertex: 1-byte edge flag, 16-bit big-endian X and Y, then
     * 8-bit R, G, B — with coordinates mapped from 0..65535 by the given bounds.
     */
    public String addMeshShading(double xMin, double xMax, double yMin, double yMax, byte[] vertexData) {
        PdfArray decode = new PdfArray();
        decode.addNumber(xMin).addNumber(xMax).addNumber(yMin).addNumber(yMax)
              .addNumber(0L).addNumber(1L).addNumber(0L).addNumber(1L).addNumber(0L).addNumber(1L);
        PdfDictionary d = new PdfDictionary()
                .put(PdfName.of("ShadingType"), PdfNumber.of(4))
                .put(PdfName.of("ColorSpace"), PdfName.of("DeviceRGB"))
                .put(PdfName.of("BitsPerCoordinate"), PdfNumber.of(16))
                .put(PdfName.of("BitsPerComponent"), PdfNumber.of(8))
                .put(PdfName.of("BitsPerFlag"), PdfNumber.of(8))
                .put(PdfName.of("Decode"), decode)
                .put(PdfName.FILTER, PdfName.FLATE_DECODE);
        int num = allocate(new PdfStream(d, com.epdfengine.rb.org.common.Deflate.deflate(vertexData)));
        String name = "Sh" + (nextShadingIndex++);
        shadingResources.put(name, num);
        return name;
    }

    private int buildGradientFunction(double[][] stops) {
        if (stops == null || stops.length == 0) {
            stops = new double[][]{ {0, 0, 0, 0}, {1, 0, 0, 0} };
        } else if (stops.length == 1) {
            stops = new double[][]{ stops[0].clone(), stops[0].clone() };
        }
        if (stops.length == 2) {
            return allocate(exponentialFunction(stops[0], stops[1]));
        }
        PdfArray functions = new PdfArray(stops.length - 1);
        PdfArray bounds = new PdfArray(stops.length - 2);
        PdfArray encode = new PdfArray((stops.length - 1) * 2);
        for (int i = 0; i < stops.length - 1; i++) {
            functions.add(ref(allocate(exponentialFunction(stops[i], stops[i + 1]))));
            if (i > 0) bounds.addNumber(clamp01(stops[i][0]));
            encode.addNumber(0L).addNumber(1L);
        }
        PdfArray domain = new PdfArray(2);
        domain.addNumber(0L).addNumber(1L);
        PdfDictionary stitch = new PdfDictionary()
                .put(PdfName.of("FunctionType"), PdfNumber.of(3))
                .put(PdfName.of("Domain"), domain)
                .put(PdfName.of("Functions"), functions)
                .put(PdfName.of("Bounds"), bounds)
                .put(PdfName.of("Encode"), encode);
        return allocate(stitch);
    }

    private static PdfDictionary exponentialFunction(double[] a, double[] b) {
        PdfArray c0 = new PdfArray(3);
        c0.addNumber(clamp01(a[1])).addNumber(clamp01(a[2])).addNumber(clamp01(a[3]));
        PdfArray c1 = new PdfArray(3);
        c1.addNumber(clamp01(b[1])).addNumber(clamp01(b[2])).addNumber(clamp01(b[3]));
        PdfArray domain = new PdfArray(2);
        domain.addNumber(0L).addNumber(1L);
        return new PdfDictionary()
                .put(PdfName.of("FunctionType"), PdfNumber.of(2))
                .put(PdfName.of("Domain"), domain)
                .put(PdfName.of("C0"), c0)
                .put(PdfName.of("C1"), c1)
                .put(PdfName.of("N"), PdfNumber.of(1));
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    // --- conformance (PDF/A, PDF/UA): metadata, output intent, structure ---
    private Integer infoNumber;
    private final PdfArray outputIntents = new PdfArray();
    private boolean hasMetadata, isMarked, hasLanguage, hasStructTree, displayDocTitle;

    /** Document language (BCP-47, e.g. {@code en-US}) — required by PDF/UA. */
    public void setLanguage(String lang) {
        if (lang != null && !lang.isBlank()) { catalog.put(PdfName.of("Lang"), PdfString.ofText(lang)); hasLanguage = true; }
    }

    /**
     * Sets {@code /ViewerPreferences << /DisplayDocTitle true >>} so readers show the
     * document title (not the file name) — required by PDF/UA (ISO 14289, ISO 32000 Table 150).
     */
    public void setDisplayDocTitle(boolean on) {
        if (!on) return;
        PdfDictionary vp = new PdfDictionary().put(PdfName.of("DisplayDocTitle"), PdfBoolean.TRUE);
        catalog.put(PdfName.of("ViewerPreferences"), vp);
        displayDocTitle = true;
    }

    /** Marks the document as tagged ({@code /MarkInfo << /Marked true >>}) — required by PDF/UA. */
    public void setMarked(boolean marked) {
        if (marked) {
            catalog.put(PdfName.of("MarkInfo"), new PdfDictionary().put(PdfName.of("Marked"), PdfBoolean.TRUE));
            isMarked = true;
        }
    }

    /** Attaches an XMP metadata packet (uncompressed XML) — required by PDF/A and PDF/UA. */
    public void setXmpMetadata(byte[] xml) {
        if (xml == null) return;
        PdfDictionary d = new PdfDictionary()
                .put(PdfName.TYPE, PdfName.of("Metadata"))
                .put(PdfName.SUBTYPE, PdfName.of("XML"));
        int n = allocate(new PdfStream(d, xml));   // must stay uncompressed/unencrypted for PDF/A
        catalog.put(PdfName.of("Metadata"), ref(n));
        hasMetadata = true;
    }

    /** Adds an sRGB output intent with an embedded ICC profile — required by PDF/A. */
    public void addSrgbOutputIntent(byte[] iccProfile, int components, String conditionId) {
        if (iccProfile == null) return;
        PdfDictionary iccDict = new PdfDictionary()
                .put(PdfName.of("N"), PdfNumber.of(components))
                .put(PdfName.FILTER, PdfName.FLATE_DECODE);
        int iccNum = allocate(new PdfStream(iccDict,
                com.epdfengine.rb.org.common.Deflate.deflate(iccProfile)));
        PdfDictionary oi = new PdfDictionary()
                .put(PdfName.TYPE, PdfName.of("OutputIntent"))
                .put(PdfName.of("S"), PdfName.of("GTS_PDFA1"))
                .put(PdfName.of("OutputConditionIdentifier"), PdfString.ofText(conditionId))
                .put(PdfName.of("Info"), PdfString.ofText(conditionId))
                .put(PdfName.of("DestOutputProfile"), ref(iccNum));
        outputIntents.add(oi);
        catalog.put(PdfName.of("OutputIntents"), outputIntents);
    }

    /** Sets the Info dictionary; PDF/A requires it to be consistent with the XMP metadata. */
    public void setDocumentInfo(String title, String author, String producer, String creationDate) {
        PdfDictionary info = new PdfDictionary();
        if (title != null)  info.put(PdfName.of("Title"),  PdfString.ofText(title));
        if (author != null) info.put(PdfName.of("Author"), PdfString.ofText(author));
        info.put(PdfName.of("Producer"), PdfString.ofText(producer != null ? producer : "ePDF Engine (epdf-org)"));
        if (creationDate != null) {
            info.put(PdfName.of("CreationDate"), PdfString.ofText(creationDate));
            info.put(PdfName.of("ModDate"), PdfString.ofText(creationDate));
        }
        infoNumber = allocate(info);
    }

    /** Points the catalog at a {@code /StructTreeRoot} object — required by PDF/UA. */
    public void setStructTreeRoot(int structTreeRootObjectNumber) {
        catalog.put(PdfName.of("StructTreeRoot"), ref(structTreeRootObjectNumber));
        hasStructTree = true;
    }

    /** Points the catalog at an {@code /AcroForm} object (interactive form). */
    public void setAcroForm(int acroFormObjectNumber) {
        catalog.put(PdfName.of("AcroForm"), ref(acroFormObjectNumber));
    }

    /** One document-outline (bookmark) node: a title, a page destination, and optional children. */
    public static final class OutlineItem {
        public final String title;
        public final int pageObjectNumber;
        public final double topY;          // destination top, in PDF (bottom-up) coordinates
        public final java.util.List<OutlineItem> children = new java.util.ArrayList<>();
        public OutlineItem(String title, int pageObjectNumber, double topY) {
            this.title = title; this.pageObjectNumber = pageObjectNumber; this.topY = topY;
        }
    }

    /** Builds a {@code /Outlines} bookmark tree from {@code roots} and points the catalog at it. */
    public void setOutline(java.util.List<OutlineItem> roots) {
        if (roots == null || roots.isEmpty()) return;
        PdfDictionary outlines = new PdfDictionary().put(PdfName.TYPE, PdfName.of("Outlines"));
        int outlinesNum = allocate(outlines);
        int[] fl = buildOutlineLevel(roots, ref(outlinesNum));
        outlines.put(PdfName.of("First"), ref(fl[0]));
        outlines.put(PdfName.of("Last"), ref(fl[1]));
        outlines.put(PdfName.of("Count"), PdfNumber.of(fl[2]));
        catalog.put(PdfName.of("Outlines"), ref(outlinesNum));
    }

    /** Allocates and links one sibling level; returns {@code {firstNum, lastNum, openCount}}. */
    private int[] buildOutlineLevel(java.util.List<OutlineItem> items, PdfIndirectReference parentRef) {
        int n = items.size();
        int[] nums = new int[n];
        PdfDictionary[] dicts = new PdfDictionary[n];
        for (int i = 0; i < n; i++) { dicts[i] = new PdfDictionary(); nums[i] = allocate(dicts[i]); }
        int openCount = 0;
        for (int i = 0; i < n; i++) {
            OutlineItem it = items.get(i);
            PdfDictionary d = dicts[i];
            d.put(PdfName.of("Title"), PdfString.ofText(it.title));
            d.put(PdfName.of("Parent"), parentRef);
            if (i > 0)     d.put(PdfName.of("Prev"), ref(nums[i - 1]));
            if (i < n - 1) d.put(PdfName.of("Next"), ref(nums[i + 1]));
            PdfArray dest = new PdfArray();
            dest.add(ref(it.pageObjectNumber));
            dest.add(PdfName.of("FitH"));
            dest.addNumber(it.topY);
            d.put(PdfName.of("Dest"), dest);
            openCount++;
            if (!it.children.isEmpty()) {
                int[] cfl = buildOutlineLevel(it.children, ref(nums[i]));
                d.put(PdfName.of("First"), ref(cfl[0]));
                d.put(PdfName.of("Last"), ref(cfl[1]));
                d.put(PdfName.of("Count"), PdfNumber.of(cfl[2]));   // positive = shown expanded
                openCount += cfl[2];
            }
        }
        return new int[]{ nums[0], nums[n - 1], openCount };
    }

    // --- Optional Content (layers) + document JavaScript: animation support ---

    private final java.util.List<Integer> ocgNumbers = new java.util.ArrayList<>();

    /** Creates an Optional Content Group (a layer) and returns its object number. */
    public int addOptionalContentGroup(String name) {
        PdfDictionary ocg = new PdfDictionary()
                .put(PdfName.TYPE, PdfName.of("OCG"))
                .put(PdfName.of("Name"), PdfString.ofText(name));
        int num = allocate(ocg);
        ocgNumbers.add(num);
        return num;
    }

    /** Maps a content-stream resource name (used by {@code /name BDC}) to an OCG on the given page. */
    public void addPageOcgProperty(int pageObjectNumber, String resourceName, int ocgNumber) {
        PdfDictionary page = pageDictByNum.get(pageObjectNumber);
        if (page == null) return;
        PdfObject resObj = page.get(PdfName.RESOURCES);
        PdfDictionary res;
        if (resObj instanceof PdfDictionary d) res = d;
        else { res = new PdfDictionary(); page.put(PdfName.RESOURCES, res); }
        PdfObject propsObj = res.get(PdfName.of("Properties"));
        PdfDictionary props;
        if (propsObj instanceof PdfDictionary d) props = d;
        else { props = new PdfDictionary(); res.put(PdfName.of("Properties"), props); }
        props.put(PdfName.of(resourceName), ref(ocgNumber));
    }

    /**
     * Writes the {@code /OCProperties} default configuration. The groups in
     * {@code onOcgs} are visible by default; the rest are hidden — so a static
     * viewer (no JavaScript) shows exactly the {@code onOcgs} layers.
     */
    public void setOptionalContentConfig(java.util.List<Integer> onOcgs) {
        if (ocgNumbers.isEmpty()) return;
        PdfArray all = new PdfArray(), order = new PdfArray(), on = new PdfArray(), off = new PdfArray();
        for (int n : ocgNumbers) {
            all.add(ref(n));
            order.add(ref(n));
            if (onOcgs.contains(n)) on.add(ref(n)); else off.add(ref(n));
        }
        PdfDictionary d = new PdfDictionary()
                .put(PdfName.of("Name"), PdfString.ofText("Default"))
                .put(PdfName.of("Order"), order)
                .put(PdfName.of("ON"), on)
                .put(PdfName.of("OFF"), off);
        catalog.put(PdfName.of("OCProperties"), new PdfDictionary()
                .put(PdfName.of("OCGs"), all)
                .put(PdfName.of("D"), d));
    }

    /** Sets a document {@code /OpenAction} JavaScript (runs on open in Acrobat) — used for timers. */
    public void setOpenActionJavaScript(String js) {
        PdfDictionary action = new PdfDictionary()
                .put(PdfName.of("S"), PdfName.of("JavaScript"))
                .put(PdfName.of("JS"), PdfString.ofText(js));
        catalog.put(PdfName.of("OpenAction"), ref(allocate(action)));
    }

    /** Registers a new indirect object and returns its number (for building structure elements). */
    public int addObject(PdfObject obj) { return allocate(obj); }

    private final java.util.Map<Integer, PdfDictionary> pageDictByNum = new java.util.HashMap<>();

    /**
     * Adds an annotation dictionary to the page (creating its {@code /Annots} array
     * if needed) and returns the annotation's object number.
     */
    public int addAnnotation(int pageObjectNumber, PdfDictionary annotation) {
        int annotNum = allocate(annotation);
        PdfDictionary page = pageDictByNum.get(pageObjectNumber);
        if (page != null) {
            PdfObject existing = page.get(PdfName.of("Annots"));
            PdfArray annots;
            if (existing instanceof PdfArray a) {
                annots = a;
            } else {
                annots = new PdfArray();
                page.put(PdfName.of("Annots"), annots);
            }
            annots.add(ref(annotNum));
        }
        return annotNum;
    }

    // --- conformance queries (used by the validator) ---
    /** True when any non-embedded standard-14 font is in use (disallowed by PDF/A). */
    public boolean usesStandardFonts() { return !fontResourceNames.isEmpty(); }
    public boolean hasEmbeddedFonts()  { return !embeddedFontResources.isEmpty(); }
    public boolean hasXmpMetadata()    { return hasMetadata; }
    public boolean hasOutputIntent()   { return outputIntents.size() > 0; }
    public boolean isTaggedMarked()    { return isMarked; }
    public boolean hasLanguage()       { return hasLanguage; }
    public boolean hasStructTreeRoot() { return hasStructTree; }
    public boolean hasDisplayDocTitle(){ return displayDocTitle; }

    /** Serializes the document to {@code out}. */
    public void writeTo(OutputStream out) throws IOException {
        writeTo(out, false);
    }

    /**
     * Serializes the document to {@code out}. When {@code compressed} is true, non-stream
     * objects are packed into a Flate {@code /ObjStm} and the cross-reference is a compressed
     * {@code /XRef} stream (smaller output; requires PDF 1.5+ readers).
     */
    public void writeTo(OutputStream out, boolean compressed) throws IOException {
        pagesNode.put(PdfName.KIDS, pageKids);
        pagesNode.put(PdfName.COUNT, PdfNumber.of(pageKids.size()));
        int info = (infoNumber != null ? infoNumber : 0);
        if (compressed) new PdfWriter().writeCompressed(out, objects, catalogNumber, info, fileId());
        else            new PdfWriter().write(out, objects, catalogNumber, info, fileId());
    }

    /**
     * A 16-byte file identifier for the trailer {@code /ID}. This is NOT a security
     * value, so a plain (non-cryptographic) SplitMix64 mixer is used — epdf-org
     * carries no cryptographic code by policy.
     */
    private static byte[] fileId() {
        long seed = System.nanoTime() ^ (System.identityHashCode(new Object()) * 0x9E3779B97F4A7C15L);
        byte[] id = new byte[16];
        long z = seed;
        for (int i = 0; i < 16; i += 8) {
            z += 0x9E3779B97F4A7C15L;
            long x = z;
            x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
            x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
            x = x ^ (x >>> 31);
            for (int b = 0; b < 8 && i + b < 16; b++) {
                id[i + b] = (byte) (x >>> (8 * b));
            }
        }
        return id;
    }
}
