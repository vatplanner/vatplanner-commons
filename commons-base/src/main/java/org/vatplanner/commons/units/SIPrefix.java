package org.vatplanner.commons.units;

/**
 * Represents SI prefixes (orders of magnitude as power of 10) and offers conversion between them.
 */
public enum SIPrefix {
    MEGA(6, "M"),
    KILO(3, "k"),
    BASE(0, "");

    private final int exp10;
    private final String prefix;

    SIPrefix(int exp10, String prefix) {
        this.exp10 = exp10;
        this.prefix = prefix;
    }

    /**
     * Returns the short symbol used for the SI prefix; may use Unicode.
     * Note that {@link #BASE} returns an empty string as it represents "no prefix".
     *
     * @return short symbol; may use Unicode, can be empty
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Converts the given integer value associated with this prefix into another prefix, i.e. multiplying or dividing it
     * by the difference of powers of 10. Loss of precision may occur due to integer arithmetic.
     *
     * @param value value associated with this prefix
     * @param other wanted prefix to convert to
     * @return value in wanted prefix; calculated using integer arithmetic
     * @see #convertTo(double, SIPrefix)
     */
    public long convertTo(long value, SIPrefix other) {
        int diffExp10 = (this.exp10 - other.exp10);

        if (diffExp10 > 0) {
            return value * longPow10(diffExp10);
        } else if (diffExp10 < 0) {
            return value / longPow10(-diffExp10);
        } else {
            return value;
        }
    }

    /**
     * Converts the given value associated with this prefix into another prefix, i.e. multiplying or dividing it
     * by the difference of powers of 10.
     *
     * @param value value associated with this prefix
     * @param other wanted prefix to convert to
     * @return value in wanted prefix; calculated using floating-point arithmetic
     * @see #convertTo(long, SIPrefix)
     */
    public double convertTo(double value, SIPrefix other) {
        int diffExp10 = (this.exp10 - other.exp10);

        if (diffExp10 != 0) {
            return value * Math.pow(10, diffExp10);
        } else {
            return value;
        }
    }

    private static long longPow10(int exponent) {
        long out = 1;

        for (int i = 0; i < exponent; i++) {
            out *= 10;
        }

        return out;
    }
}
