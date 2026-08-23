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
package com.epdfengine.rb.org.testkit;

/**
 * Minimal zero-dependency assertion harness. epdf-org uses no JUnit (or any other
 * third-party library) even in tests, so this tiny helper backs all test classes.
 * A test is any class with a {@code public static void main} that throws on
 * failure; a runner can invoke each and treat a thrown {@link AssertionError} as a
 * failed test.
 */
public final class Assert {

    private Assert() {}

    public static void isTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError("Expected true: " + message);
    }

    public static void equals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " — expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void equals(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " — expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void contains(String haystack, String needle, String message) {
        if (haystack == null || !haystack.contains(needle)) {
            throw new AssertionError(message + " — expected to contain <" + needle + ">");
        }
    }
}
