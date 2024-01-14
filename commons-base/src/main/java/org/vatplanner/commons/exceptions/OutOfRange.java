package org.vatplanner.commons.exceptions;

/**
 * Thrown if the given argument is not within a valid value range.
 */
public class OutOfRange extends IllegalArgumentException {
    OutOfRange(String subject, double actual, double expectedMin, double expectedMax) {
        super(String.format("%s is out of range: %f must be within [%f, %f]", subject, actual, expectedMin, expectedMax));
    }

    public static void throwIfNotWithinIncluding(String subject, double actual, double expectedMin, double expectedMax) {
        if (actual > expectedMax || actual < expectedMin) {
            throw new OutOfRange(subject, actual, expectedMin, expectedMax);
        }
    }

    public static void throwIfNotWithinIncluding(String subject, int actual, int expectedMin, int expectedMax) {
        if (actual > expectedMax || actual < expectedMin) {
            throw new OutOfRange(subject, actual, expectedMin, expectedMax);
        }
    }
}
