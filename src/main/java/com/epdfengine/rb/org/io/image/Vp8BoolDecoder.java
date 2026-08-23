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
package com.epdfengine.rb.org.io.image;

/**
 * The VP8 boolean entropy decoder (RFC 6386 §7). A binary arithmetic decoder that
 * reads bits whose probability of being zero is given per call; literals and tree
 * codings are built on top of it. Clean-room implementation of the published
 * algorithm.
 */
final class Vp8BoolDecoder {

    private final byte[] buf;
    private int pos;
    private final int end;
    private long value;
    private int range;
    private int bitCount;

    Vp8BoolDecoder(byte[] buf, int start, int end) {
        this.buf = buf;
        this.end = end;
        this.pos = start;
        this.range = 255;
        this.bitCount = 0;
        long v = 0;
        v = (v << 8) | nextByte();
        v = (v << 8) | nextByte();
        this.value = v;
    }

    private int nextByte() {
        return (pos < end) ? (buf[pos++] & 0xFF) : 0;
    }

    /** Decodes one boolean with the given probability of being 0 (0..255). */
    int readBool(int prob) {
        int split = 1 + (((range - 1) * prob) >> 8);
        long bigSplit = ((long) split) << 8;
        int ret;
        if (value >= bigSplit) {
            ret = 1;
            range -= split;
            value -= bigSplit;
        } else {
            ret = 0;
            range = split;
        }
        while (range < 128) {
            value <<= 1;
            range <<= 1;
            if (++bitCount == 8) {
                bitCount = 0;
                value |= nextByte();
            }
        }
        return ret;
    }

    /** Decodes one bit with uniform probability. */
    int readBit() {
        return readBool(128);
    }

    /** Decodes an unsigned literal of {@code numBits} bits (MSB first). */
    int readLiteral(int numBits) {
        int v = 0;
        while (numBits-- > 0) v = (v << 1) | readBool(128);
        return v;
    }

    /** Decodes a magnitude of {@code numBits} bits followed by a sign flag. */
    int readSignedLiteral(int numBits) {
        int v = readLiteral(numBits);
        return (readBool(128) != 0) ? -v : v;
    }

    /** Reads a flag then, if set, a signed value of {@code numBits} bits; else 0. */
    int readOptionalSigned(int numBits) {
        return (readBool(128) != 0) ? readSignedLiteral(numBits) : 0;
    }

    /** Walks a VP8 token tree using per-node probabilities, returning the leaf value. */
    int readTree(int[] tree, int[] probs) {
        int i = 0;
        while ((i = tree[i + readBool(probs[i >> 1])]) > 0) {
            // descend
        }
        return -i;
    }

    /** Walks a token tree starting at a given node index (for coefficient decoding). */
    int readTree(int[] tree, int[] probs, int start) {
        int i = start;
        while ((i = tree[i + readBool(probs[i >> 1])]) > 0) {
            // descend
        }
        return -i;
    }
}
