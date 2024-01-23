package org.vatplanner.commons.units;

/**
 * Represents a length or distance, linked to a specific {@link Unit} used for measurement.
 * Value and unit are stored; conversion only happens on request, if necessary.
 */
public class Length {
    private final double value; // TODO: keep exact int/long if provided
    private final Unit unit;

    private Length(double value, Unit unit) {
        this.value = value;
        this.unit = unit;
    }

    public double getValue() {
        return value;
    }

    public Unit getUnit() {
        return unit;
    }

    /**
     * Returns the value in the given unit. If the unit is different from the original value, the value will be returned
     * converted.
     *
     * @param wantedUnit unit to retrieve value in
     * @return value in given unit; converted if necessary
     */
    public double getValueAs(Unit wantedUnit) {
        return unit.convertTo(value, wantedUnit);
    }

    /**
     * Creates a new {@link Length} of given value and {@link Unit} of measurement.
     *
     * @param value measured value
     * @param unit  unit of measurement
     * @return specified {@link Length}
     */
    public static Length of(double value, Unit unit) {
        return new Length(value, unit);
    }

    @Override
    public String toString() {
        return "Length(" + value + unit.shortName + ")";
    }

    /**
     * Units of measurement and conversions between them.
     */
    public enum Unit {
        METERS("m"),
        FEET("ft");

        private static final double FACTOR_FEET_TO_METERS = 0.3048;
        private static final double FACTOR_METERS_TO_FEET = 1.0 / FACTOR_FEET_TO_METERS;

        private final String shortName;

        Unit(String shortName) {
            this.shortName = shortName;
        }

        /**
         * Returns the short name the unit is commonly known by.
         *
         * @return commonly known short name
         */
        public String getShortName() {
            return shortName;
        }

        /**
         * Converts the given length/distance value from this unit to the requested one.
         * In case units are equal, no conversion will happen. Not all conversions may be supported.
         *
         * @param value length/distance value to convert
         * @param other wanted unit of measurement
         * @return value in wanted unit of measurement
         */
        public double convertTo(double value, Unit other) {
            if (this == other) {
                return value;
            }

            if (this == FEET && other == METERS) {
                return value * FACTOR_FEET_TO_METERS;
            } else if (this == METERS && other == FEET) {
                return value * FACTOR_METERS_TO_FEET;
            }

            throw new UnsupportedConversion(this, other);
        }

        private static class UnsupportedConversion extends IllegalArgumentException {
            UnsupportedConversion(Unit from, Unit to) {
                super("Conversion from " + from + " to " + to + " is not supported");
            }
        }
    }
}
