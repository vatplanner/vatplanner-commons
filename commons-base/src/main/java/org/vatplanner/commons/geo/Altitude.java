package org.vatplanner.commons.geo;

import java.util.Objects;

import org.vatplanner.commons.exceptions.OutOfRange;

/**
 * An altitude, usually referenced to be measured above Mean Sea Level (MSL), as commonly used in aviation.
 * <p>
 * The actual vertical position of an altitude is subject to the local barometric pressure at a specific location and
 * time (QNH). {@link Altitude} instances can be defined generically, suitable e.g. for planning purposes or use at
 * standard pressure (1013.25hPa/29.92inHg) according to International Standard Atmosphere (ISA), or they can be
 * required to be referenced to local barometric pressure (QNH).
 * </p>
 * <p>
 * An altitude intended to be referenced to standard pressure is generally called a "flight level" and counted in
 * hundreds of feet.
 * </p>
 */
public class Altitude implements Comparable<Altitude> {
    private final int feet;
    private final boolean barometric;

    private Altitude(int feet, boolean barometric) {
        this.feet = feet;
        this.barometric = barometric;
    }

    /**
     * Defines an {@link Altitude} that is required to be referenced to local barometric pressure (QNH).
     *
     * @param feetBarometric altitude measured in feet
     * @return altitude, required to be referenced to barometric pressure (QNH)
     */
    public static Altitude feetBarometric(int feetBarometric) {
        return new Altitude(feetBarometric, true);
    }

    /**
     * Defines a generic {@link Altitude} not restricted to barometric pressure, either for planning purposes or use in
     * reference to standard QNH.
     *
     * @param feet altitude measured in feet
     * @return altitude, not required to be referenced to barometric pressure
     */
    public static Altitude feet(int feet) {
        return new Altitude(feet, false);
    }

    /**
     * Defines a generic {@link Altitude} not restricted to barometric pressure from a flight level.
     * <p>
     * Flight levels are ISA/standard pressure referenced altitudes counted in hundreds of feet and <i>usually</i>
     * counted in steps of 5 (500 feet) although this is not guaranteed and thus will not be enforced by this method.
     * </p>
     *
     * @param flightLevel 3-digit flight level (hundreds of feet)
     * @return altitude, intended to be referenced to standard pressure
     * @see #feet(int)
     */
    public static Altitude flightLevel(int flightLevel) {
        OutOfRange.throwIfNotWithinIncluding("flight level", flightLevel, 0, 999);
        return feet(flightLevel * 100);
    }

    /**
     * Indicates whether this {@link Altitude} is supposed to be referenced to local barometric pressure (QNH).
     *
     * @return {@code true} if the {@link Altitude} should be referenced to QNH, {@code false} if not
     */
    public boolean isBarometric() {
        return barometric;
    }

    /**
     * Returns the altitude in feet.
     *
     * @return altitude in feet
     */
    public int getFeet() {
        return feet;
    }

    @Override
    public int compareTo(Altitude other) {
        if (this.barometric != other.barometric) {
            throw new IllegalArgumentException("Barometric altitudes can only be compared to each other, got mixed " + this + " and " + other);
        }

        return Integer.compare(this.feet, other.feet);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Altitude)) {
            return false;
        }

        Altitude other = (Altitude) obj;

        return this.feet == other.feet
            && this.barometric == other.barometric;
    }

    @Override
    public int hashCode() {
        return Objects.hash(feet, barometric);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Altitude(");
        sb.append(feet);

        if (barometric) {
            sb.append("@QNH");
        }

        sb.append(")");

        return sb.toString();
    }

    // TODO: split into separate classes?
    // TODO: extend by unit/support metric definition?
    // TODO: remember if the altitude was specifically defined as a flight level
}
