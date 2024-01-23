package org.vatplanner.commons.units;

/**
 * Represents a physical frequency in Hertz ("cycles per second").
 */
public class Frequency {
    private final long value;
    private final SIPrefix orderOfMagnitude;

    private Frequency(long value, SIPrefix orderOfMagnitude) {
        this.value = value;
        this.orderOfMagnitude = orderOfMagnitude;
    }

    /**
     * Returns the exact frequency in Hertz (without {@link SIPrefix}).
     *
     * @return exact frequency in Hertz
     */
    public long getHertz() {
        return orderOfMagnitude.convertTo(this.value, SIPrefix.BASE);
    }

    @Override
    public String toString() {
        return "Frequency(" + value + orderOfMagnitude.getPrefix() + "Hz)";
    }

    /**
     * Gets the frequency value read at given magnitude.
     * <p>
     * Example: 123400 Hz requested in {@link SIPrefix#KILO} will return 123.4.
     * </p>
     *
     * @param orderOfMagnitude magnitude to return value in
     * @return frequency value read at given magnitude
     */
    public double getHertz(SIPrefix orderOfMagnitude) {
        return this.orderOfMagnitude.convertTo((double) value, orderOfMagnitude);
    }

    /**
     * Gets the frequency value read at given magnitude, rounded to the nearest integer value.
     * <p>
     * Examples:
     * </p>
     * <ul>
     * <li>123400 Hz requested in {@link SIPrefix#KILO} will return 123 (rounded down).</li>
     * <li>123500 Hz requested in {@link SIPrefix#KILO} will return 124 (rounded up).</li>
     * </ul>
     *
     * @param orderOfMagnitude magnitude to return value in
     * @return frequency value read at given magnitude, rounded to nearest integer
     */
    public long getRoundedHertz(SIPrefix orderOfMagnitude) {
        return Math.round(getHertz(orderOfMagnitude));
    }

    /**
     * Creates a new {@link Frequency} of given value and magnitude.
     *
     * @param value            value, may have any SI prefix; must not be negative
     * @param orderOfMagnitude magnitude/{@link SIPrefix}; e.g. {@link SIPrefix#KILO} if value is in kHz
     * @return specified {@link Frequency}
     */
    public static Frequency of(long value, SIPrefix orderOfMagnitude) {
        if (value < 0) {
            throw new IllegalArgumentException("frequencies must not be negative; got: " + value);
        }

        return new Frequency(value, orderOfMagnitude);
    }
}
