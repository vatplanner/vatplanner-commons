package org.vatplanner.commons.geo;

import java.util.Optional;

/**
 * Defines a range of valid {@link Altitude}s as encountered in flight planning. An {@link AltitudeConstraint} can be
 * left open to one side ("at or above/below") or it can be closed ("exactly"/"between"). Limits are included (valid).
 * <p>
 * In aviation, altitudes above the transition altitude (subject to local pressure/QNH) are supposed to be referenced to
 * standard pressure (1013.25hPa/29.92inHg) according to International Standard Atmosphere (ISA). Altitudes below the
 * transition level (referenced to standard pressure) however are expected to be referenced to local pressure (QNH).
 * Therefore, constraints using a standard pressure {@link Altitude} as the lower but a locally (QNH) referenced
 * {@link Altitude} as the upper limit are invalid. Either both limits need to be referenced to the same pressure or
 * the upper limit needs to be referenced to standard pressure (referred to as a flight level).
 * </p>
 */
public class AltitudeConstraint {
    private final Altitude min;
    private final Altitude max;

    private static final Altitude UNRESTRICTED_MIN = Altitude.feet(Integer.MIN_VALUE);
    private static final Altitude UNRESTRICTED_MAX = Altitude.feet(Integer.MAX_VALUE);

    /**
     * A fully open, thus unrestricted, placeholder intended to indicate a basically non-existing
     * {@link AltitudeConstraint} in a way that could help to simplify implementation (calculation/checks).
     */
    public static final AltitudeConstraint UNRESTRICTED = new AltitudeConstraint(UNRESTRICTED_MIN, UNRESTRICTED_MAX);

    private AltitudeConstraint(Altitude min, Altitude max) {
        if (!min.isBarometric() && max.isBarometric()) {
            throw new IllegalArgumentException("Standard pressure is expected to always be applied above barometric altitude; got: min=" + min + ", max=" + max);
        }

        this.min = min;
        this.max = max;
    }

    public Optional<Altitude> getMin() {
        if (min == UNRESTRICTED_MIN) {
            return Optional.empty();
        }

        return Optional.of(min);
    }

    public Optional<Altitude> getMax() {
        if (max == UNRESTRICTED_MAX) {
            return Optional.empty();
        }

        return Optional.of(max);
    }

    public boolean isUnrestricted() {
        return (min == UNRESTRICTED_MIN) && (max == UNRESTRICTED_MAX);
    }

    @Override
    public String toString() {
        if (min == UNRESTRICTED_MIN && max == UNRESTRICTED_MAX) {
            return "AltitudeConstraint(unrestricted)";
        } else if (min == UNRESTRICTED_MIN) {
            return "AltitudeConstraint(max=" + max + ")";
        } else if (max == UNRESTRICTED_MAX) {
            return "AltitudeConstraint(min=" + min + ")";
        } else if (min == max) {
            return "AltitudeConstraint(exactly " + min + ")";
        } else {
            return "AltitudeConstraint(min=" + min + ", max=" + max + ")";
        }
    }

    /**
     * Creates a constraint requiring exactly the specified {@link Altitude}.
     *
     * @param altitude exact altitude to be required
     * @return constraint requiring exactly the specified {@link Altitude}
     */
    public static AltitudeConstraint exactly(Altitude altitude) {
        return new AltitudeConstraint(altitude, altitude);
    }

    /**
     * Creates a constraint requiring either the specified or any lower {@link Altitude}.
     *
     * @param altitude highest/maximum valid altitude (incl.)
     * @return constraint requiring either the specified or any lower {@link Altitude}
     */
    public static AltitudeConstraint atOrBelow(Altitude altitude) {
        return new AltitudeConstraint(UNRESTRICTED_MIN, altitude);
    }

    /**
     * Creates a constraint requiring either the specified or any higher {@link Altitude}.
     *
     * @param altitude lowest/minimum valid altitude (incl.)
     * @return constraint requiring either the specified or any higher {@link Altitude}
     */
    public static AltitudeConstraint atOrAbove(Altitude altitude) {
        return new AltitudeConstraint(altitude, UNRESTRICTED_MAX);
    }

    /**
     * Creates a constraint requiring either specified {@link Altitude} or any in between.
     * <p>
     * The {@link Altitude}s can be given in any order (lowest/highest, highest/lowest or even same/same) and will be
     * swapped for standardized minimum/maximum constraints. The only limitation is that if an {@link Altitude} is
     * required to be referenced to local barometric pressure (QNH) then the other {@link Altitude} either needs to be
     * also subject to QNH or it needs to be higher. A constraint with a standard altitude/flight level as the lower
     * and a QNH-referenced altitude as upper limit is implausible as such constraints do not make sense in aviation
     * (see class JavaDoc).
     * </p>
     *
     * @param altitude1 limiting {@link Altitude} (incl.)
     * @param altitude2 limiting {@link Altitude} (incl.)
     * @return constraint requiring either specified {@link Altitude} or any in between
     */
    public static AltitudeConstraint between(Altitude altitude1, Altitude altitude2) {
        if (altitude1.equals(altitude2)) {
            return exactly(altitude1);
        }

        boolean reversed = false;

        if (altitude1.isBarometric() == altitude2.isBarometric()) {
            // altitudes can be compared directly
            reversed = altitude1.compareTo(altitude2) > 0;
        } else if (altitude2.isBarometric() && !altitude1.isBarometric()) {
            // baro value needs to go below standard value but seems reversed
            int feet1 = altitude1.getFeet();
            int feet2 = altitude2.getFeet();
            reversed = feet1 > feet2;
        }

        Altitude min = reversed ? altitude2 : altitude1;
        Altitude max = reversed ? altitude1 : altitude2;

        return new AltitudeConstraint(min, max);
    }
}
