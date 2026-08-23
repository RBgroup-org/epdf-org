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
package com.epdfengine.rb.org.kernel.read;

/**
 * A low-level tokenizer for the PDF object syntax (ISO 32000-1 §7.2-7.3): numbers,
 * names, literal/hex strings, the array and dictionary delimiters, and bare
 * keywords ({@code obj}, {@code endobj}, {@code stream}, {@code R}, {@code true}
 * …). The parser drives it token-by-token and can seek to a byte offset.
 */
final class Lexer {

    enum Kind { NUMBER, NAME, STRING, KEYWORD, ARRAY_OPEN, ARRAY_CLOSE, DICT_OPEN, DICT_CLOSE, EOF }

    static final class Token {
        final Kind kind;
        final String text;      // NUMBER / NAME / KEYWORD
        final byte[] bytes;     // STRING (raw, already unescaped)
        Token(Kind kind, String text, byte[] bytes) { this.kind = kind; this.text = text; this.bytes = bytes; }
    }

    private final byte[] b;
    private int pos;

    Lexer(byte[] b) { this.b = b; }

    int pos() { return pos; }
    void seek(int p) { this.pos = p; }
    byte[] data() { return b; }

    private boolean ws(int c) { return c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\f' || c == 0; }
    private boolean delim(int c) {
        return c == '(' || c == ')' || c == '<' || c == '>' || c == '[' || c == ']'
                || c == '{' || c == '}' || c == '/' || c == '%';
    }

    private void skipWsAndComments() {
        while (pos < b.length) {
            int c = b[pos] & 0xFF;
            if (ws(c)) { pos++; }
            else if (c == '%') { while (pos < b.length && b[pos] != '\n' && b[pos] != '\r') pos++; }
            else break;
        }
    }

    Token next() {
        skipWsAndComments();
        if (pos >= b.length) return new Token(Kind.EOF, null, null);
        int c = b[pos] & 0xFF;
        switch (c) {
            case '[': pos++; return new Token(Kind.ARRAY_OPEN, "[", null);
            case ']': pos++; return new Token(Kind.ARRAY_CLOSE, "]", null);
            case '<':
                if (pos + 1 < b.length && b[pos + 1] == '<') { pos += 2; return new Token(Kind.DICT_OPEN, "<<", null); }
                return hexString();
            case '>':
                if (pos + 1 < b.length && b[pos + 1] == '>') { pos += 2; return new Token(Kind.DICT_CLOSE, ">>", null); }
                pos++; return new Token(Kind.KEYWORD, ">", null);
            case '(': return literalString();
            case '/': return name();
            case '{': pos++; return new Token(Kind.KEYWORD, "{", null);
            case '}': pos++; return new Token(Kind.KEYWORD, "}", null);
            default:
                if (c == '+' || c == '-' || c == '.' || (c >= '0' && c <= '9')) return number();
                return keyword();
        }
    }

    private Token number() {
        int start = pos;
        while (pos < b.length) {
            int c = b[pos] & 0xFF;
            if ((c >= '0' && c <= '9') || c == '+' || c == '-' || c == '.' || c == 'e' || c == 'E') pos++;
            else break;
        }
        return new Token(Kind.NUMBER, new String(b, start, pos - start, java.nio.charset.StandardCharsets.ISO_8859_1), null);
    }

    private Token keyword() {
        int start = pos;
        while (pos < b.length) {
            int c = b[pos] & 0xFF;
            if (ws(c) || delim(c)) break;
            pos++;
        }
        if (pos == start) pos++;   // lone delimiter safety
        return new Token(Kind.KEYWORD, new String(b, start, pos - start, java.nio.charset.StandardCharsets.ISO_8859_1), null);
    }

    private Token name() {
        pos++;                     // skip '/'
        StringBuilder sb = new StringBuilder();
        while (pos < b.length) {
            int c = b[pos] & 0xFF;
            if (ws(c) || delim(c)) break;
            if (c == '#' && pos + 2 < b.length) {
                int hi = hex(b[pos + 1]), lo = hex(b[pos + 2]);
                if (hi >= 0 && lo >= 0) { sb.append((char) (hi * 16 + lo)); pos += 3; continue; }
            }
            sb.append((char) c); pos++;
        }
        return new Token(Kind.NAME, sb.toString(), null);
    }

    private Token literalString() {
        pos++;                     // skip '('
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int depth = 1;
        while (pos < b.length) {
            int c = b[pos++] & 0xFF;
            if (c == '\\') {
                if (pos >= b.length) break;
                int e = b[pos++] & 0xFF;
                switch (e) {
                    case 'n': out.write('\n'); break;
                    case 'r': out.write('\r'); break;
                    case 't': out.write('\t'); break;
                    case 'b': out.write('\b'); break;
                    case 'f': out.write('\f'); break;
                    case '(': out.write('('); break;
                    case ')': out.write(')'); break;
                    case '\\': out.write('\\'); break;
                    case '\r': if (pos < b.length && b[pos] == '\n') pos++; break;  // line continuation
                    case '\n': break;
                    default:
                        if (e >= '0' && e <= '7') {
                            int v = e - '0';
                            for (int i = 0; i < 2 && pos < b.length && b[pos] >= '0' && b[pos] <= '7'; i++)
                                v = v * 8 + (b[pos++] - '0');
                            out.write(v & 0xFF);
                        } else out.write(e);
                }
            } else if (c == '(') { depth++; out.write(c); }
            else if (c == ')') { if (--depth == 0) break; out.write(c); }
            else out.write(c);
        }
        return new Token(Kind.STRING, null, out.toByteArray());
    }

    private Token hexString() {
        pos++;                     // skip '<'
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int hi = -1;
        while (pos < b.length) {
            int c = b[pos++] & 0xFF;
            if (c == '>') break;
            int h = hex((byte) c);
            if (h < 0) continue;   // ignore whitespace inside hex string
            if (hi < 0) hi = h;
            else { out.write(hi * 16 + h); hi = -1; }
        }
        if (hi >= 0) out.write(hi * 16);   // odd digit -> low nibble 0
        return new Token(Kind.STRING, null, out.toByteArray());
    }

    private static int hex(byte c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }
}
