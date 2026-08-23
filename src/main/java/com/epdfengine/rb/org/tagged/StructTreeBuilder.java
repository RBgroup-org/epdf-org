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
package com.epdfengine.rb.org.tagged;

import com.epdfengine.rb.org.kernel.PdfDocument;
import com.epdfengine.rb.org.kernel.object.PdfArray;
import com.epdfengine.rb.org.kernel.object.PdfDictionary;
import com.epdfengine.rb.org.kernel.object.PdfName;
import com.epdfengine.rb.org.kernel.object.PdfNull;
import com.epdfengine.rb.org.kernel.object.PdfNumber;
import com.epdfengine.rb.org.kernel.object.PdfString;
import com.epdfengine.rb.org.layout.StructNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds a nested logical-structure tree ({@code /StructTreeRoot → Document → …})
 * from a {@link StructNode} registry plus the marked-content ids collected while
 * rendering. Container roles (Table→TR→TD, L→LI→LBody) nest their children;
 * leaf roles (P, H1–H6, TD, Figure) carry the MCID content. A {@code /ParentTree}
 * number tree maps each page's MCIDs back to their owning elements. Empty nodes
 * (no content and no non-empty children) are pruned. Only standard structure types
 * are used, so no {@code /RoleMap} is required.
 */
public final class StructTreeBuilder {

    /** A single marked-content reference: which page it is on and its MCID. */
    public static final class ContentRef {
        public final int pageIndex;
        public final int mcid;
        public ContentRef(int pageIndex, int mcid) { this.pageIndex = pageIndex; this.mcid = mcid; }
    }

    /** A Link annotation to attach to a Link element via an OBJR + a ParentTree entry. */
    public static final class LinkAnnot {
        public final int annotObjNum;
        public final int pageIndex;
        public final int structParentKey;
        public LinkAnnot(int annotObjNum, int pageIndex, int structParentKey) {
            this.annotObjNum = annotObjNum;
            this.pageIndex = pageIndex;
            this.structParentKey = structParentKey;
        }
    }

    private final PdfDocument doc;
    private final int[] pageObjByIndex;
    private final Map<Integer, List<StructNode>> childrenOf = new LinkedHashMap<>();
    private final Map<Integer, List<ContentRef>> contentByLeaf;
    private final Map<Integer, List<LinkAnnot>> linkAnnots;
    private final int parentTreeNextKey;
    private final Map<Integer, PdfDictionary> elemDictByNum = new HashMap<>();
    // page index -> (mcid -> owning element object number), for the ParentTree.
    private final Map<Integer, TreeMap<Integer, Integer>> pageMcidElem = new TreeMap<>();
    // annotation StructParent key -> owning Link element object number.
    private final TreeMap<Integer, Integer> annotParent = new TreeMap<>();

    private StructTreeBuilder(PdfDocument doc, int[] pageObjByIndex,
                              Map<Integer, List<ContentRef>> contentByLeaf,
                              Map<Integer, List<LinkAnnot>> linkAnnots, int parentTreeNextKey) {
        this.doc = doc;
        this.pageObjByIndex = pageObjByIndex;
        this.contentByLeaf = contentByLeaf;
        this.linkAnnots = (linkAnnots != null) ? linkAnnots : Map.of();
        this.parentTreeNextKey = parentTreeNextKey;
    }

    /**
     * Emits the structure tree and returns the {@code /StructTreeRoot} object number.
     *
     * @param doc            target document
     * @param nodes          structure-node registry (document order)
     * @param contentByLeaf  leaf node id → its marked-content references
     * @param pageObjByIndex page index → page object number
     * @param pageCount      total number of pages
     */
    public static int build(PdfDocument doc, List<StructNode> nodes,
                            Map<Integer, List<ContentRef>> contentByLeaf,
                            Map<Integer, List<LinkAnnot>> linkAnnots,
                            int[] pageObjByIndex, int pageCount, int parentTreeNextKey) {
        return new StructTreeBuilder(doc, pageObjByIndex, contentByLeaf, linkAnnots, parentTreeNextKey)
                .run(nodes, pageCount);
    }

    private int run(List<StructNode> nodes, int pageCount) {
        List<StructNode> roots = new ArrayList<>();
        for (StructNode n : nodes) {
            if (n.parentId() < 0) roots.add(n);
            else childrenOf.computeIfAbsent(n.parentId(), k -> new ArrayList<>()).add(n);
        }

        PdfDictionary str = new PdfDictionary();
        int strNum = doc.addObject(str);
        PdfDictionary documentElem = new PdfDictionary();
        int docNum = doc.addObject(documentElem);

        PdfArray docKids = new PdfArray();
        for (StructNode root : roots) {
            int e = buildNode(root);
            if (e > 0) {
                docKids.add(doc.ref(e));
                elemDictByNum.get(e).put(PdfName.of("P"), doc.ref(docNum));
            }
        }
        documentElem.put(PdfName.TYPE, PdfName.of("StructElem"))
                .put(PdfName.of("S"), PdfName.of("Document"))
                .put(PdfName.of("P"), doc.ref(strNum))
                .put(PdfName.of("K"), docKids);

        PdfDictionary parentTree = new PdfDictionary();
        int ptNum = doc.addObject(parentTree);
        PdfArray nums = new PdfArray();
        for (Map.Entry<Integer, TreeMap<Integer, Integer>> e : pageMcidElem.entrySet()) {
            TreeMap<Integer, Integer> m = e.getValue();
            nums.addNumber(e.getKey());
            PdfArray arr = new PdfArray();
            int maxMcid = m.lastKey();
            for (int i = 0; i <= maxMcid; i++) {
                Integer owner = m.get(i);
                arr.add(owner != null ? doc.ref(owner) : PdfNull.INSTANCE);
            }
            nums.add(arr);
        }
        parentTree.put(PdfName.of("Nums"), nums);

        // Annotation entries: each Link annotation's StructParent key maps to its Link element.
        for (Map.Entry<Integer, Integer> e : annotParent.entrySet()) {
            nums.addNumber(e.getKey());
            nums.add(doc.ref(e.getValue()));
        }

        str.put(PdfName.TYPE, PdfName.of("StructTreeRoot"))
                .put(PdfName.of("K"), doc.ref(docNum))
                .put(PdfName.of("ParentTree"), doc.ref(ptNum))
                .put(PdfName.of("ParentTreeNextKey"), PdfNumber.of(Math.max(parentTreeNextKey, pageCount)));
        return strNum;
    }

    /** Post-order build: returns the element object number, or -1 when the node is empty (pruned). */
    private int buildNode(StructNode n) {
        List<StructNode> kids = childrenOf.get(n.id());
        List<ContentRef> content = contentByLeaf.get(n.id());
        List<LinkAnnot> annots = linkAnnots.get(n.id());
        boolean hasContent = content != null && !content.isEmpty();
        boolean hasAnnots = annots != null && !annots.isEmpty();

        List<Integer> childNums = new ArrayList<>();
        if (kids != null) {
            for (StructNode k : kids) {
                int e = buildNode(k);
                if (e > 0) childNums.add(e);
            }
        }
        if (!hasContent && !hasAnnots && childNums.isEmpty()) return -1;

        PdfDictionary elem = new PdfDictionary();
        int eNum = doc.addObject(elem);
        elem.put(PdfName.TYPE, PdfName.of("StructElem")).put(PdfName.of("S"), PdfName.of(n.role()));
        if (n.alt() != null && !n.alt().isBlank()) elem.put(PdfName.of("Alt"), PdfString.ofText(n.alt()));

        PdfArray k = new PdfArray();
        for (int cNum : childNums) {
            k.add(doc.ref(cNum));
            elemDictByNum.get(cNum).put(PdfName.of("P"), doc.ref(eNum));
        }
        if (hasContent) {
            int firstPage = content.get(0).pageIndex;
            elem.put(PdfName.of("Pg"), doc.ref(pageObjByIndex[firstPage]));
            for (ContentRef r : content) {
                if (r.pageIndex == firstPage) {
                    k.add(PdfNumber.of(r.mcid));
                } else {
                    k.add(new PdfDictionary()
                            .put(PdfName.TYPE, PdfName.of("MCR"))
                            .put(PdfName.of("Pg"), doc.ref(pageObjByIndex[r.pageIndex]))
                            .put(PdfName.of("MCID"), PdfNumber.of(r.mcid)));
                }
                pageMcidElem.computeIfAbsent(r.pageIndex, key -> new TreeMap<>()).put(r.mcid, eNum);
            }
        }
        if (hasAnnots) {
            if (!elem.has(PdfName.of("Pg"))) {
                elem.put(PdfName.of("Pg"), doc.ref(pageObjByIndex[annots.get(0).pageIndex]));
            }
            for (LinkAnnot la : annots) {
                k.add(new PdfDictionary()
                        .put(PdfName.TYPE, PdfName.of("OBJR"))
                        .put(PdfName.of("Obj"), doc.ref(la.annotObjNum))
                        .put(PdfName.of("Pg"), doc.ref(pageObjByIndex[la.pageIndex])));
                annotParent.put(la.structParentKey, eNum);
            }
        }
        elem.put(PdfName.of("K"), k);
        elemDictByNum.put(eNum, elem);
        return eNum;
    }
}
