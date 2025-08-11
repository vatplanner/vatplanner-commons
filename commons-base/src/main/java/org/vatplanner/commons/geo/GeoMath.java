package org.vatplanner.commons.geo;

import java.util.Collection;

import org.vatplanner.commons.units.Length;

/**
 * Helper methods to perform geographic calculations.
 */
public class GeoMath {
    private static final double DEGREES_TO_RADIANS_FACTOR = Math.PI / 180.0;
    private static final double RADIANS_TO_DEGREES_FACTOR = 180.0 / Math.PI;

    private static final int MEAN_RADIUS_EARTH_METERS = 6371009;

    /**
     * Calculates distances for the given type of geo-points.
     *
     * @param <T> exact type required for this calculator
     */
    public interface DistanceCalculator<T extends GeoPoint2D> {
        /**
         * Calculates the distance between the two given points.
         * Method, accuracy and used/ignored data is implementation-specific.
         * The calculated distance is required to be positive (or zero) and equal for swapped input parameters.
         *
         * @param pointA first point
         * @param pointB second point
         * @return distance between both points
         */
        Length calculateDistanceBetween(T pointA, T pointB);
    }

    /**
     * Different methods to calculate the
     * <a href="https://en.wikipedia.org/wiki/Great-circle_distance">great-circle distance</a>.
     */
    public enum GreatCircleMethod implements DistanceCalculator<GeoPoint2D> {
        // TODO: add Vincenty method
        // TODO: add "smart" method to decide most efficient sufficiently close method (Haversine or Vincenty)

        /**
         * The Haversine method for calculating great-circle distances provides an
         * accuracy of 0.5% which is okay for small distances only.
         */
        HAVERSINE(GeoMath::greatCircleDistanceByHaversine);

        private final DistanceCalculator<GeoPoint2D> calculator;

        GreatCircleMethod(DistanceCalculator<GeoPoint2D> calculator) {
            this.calculator = calculator;
        }

        @Override
        public Length calculateDistanceBetween(GeoPoint2D pointA, GeoPoint2D pointB) {
            return calculator.calculateDistanceBetween(pointA, pointB);
        }
    }

    /**
     * Different methods to calculate the total distance between two {@link GeoPoint3D} (incl. elevation).
     */
    public enum TotalDistanceMethod implements DistanceCalculator<GeoPoint3D> {
        /**
         * Uses {@link GreatCircleMethod#HAVERSINE} for lateral distance, adding vertical distance via a
         * "flat-earth model": Earth is treated as a flat surface, so vertical difference simply is the mathematical
         * difference of both points' elevations, allowing to simply calculate total distance using Pythagoras' theorem.
         * This simplification should be correct for distances of two objects that are required to follow the earth's
         * curvature, such as calculating the distance between two aircraft in standard flight.
         */
        HAVERSINE_FLAT_VERTICAL((a, b) -> addFlatVerticalDistance(a, b, GeoMath::greatCircleDistanceByHaversine));

        private final DistanceCalculator<GeoPoint3D> calculator;

        TotalDistanceMethod(DistanceCalculator<GeoPoint3D> calculator) {
            this.calculator = calculator;
        }

        @Override
        public Length calculateDistanceBetween(GeoPoint3D pointA, GeoPoint3D pointB) {
            return calculator.calculateDistanceBetween(pointA, pointB);
        }
    }

    private GeoMath() {
        // utility class, hide constructor
    }

    /**
     * Calculates the average center of given points. Implemented using the formula described on
     * <a href="https://web.archive.org/web/20221205184246/https://carto.com/blog/center-of-points/">
     * https://carto.com/blog/center-of-points/ [archive.org, retrieved 5 Dec 2022]
     * </a>.
     *
     * @param points points to calculate average center for, must not be empty
     * @return center point calculated by average
     */
    public static GeoPoint2D average(Collection<GeoPoint2D> points) {
        int numPoints = points.size();

        if (numPoints == 1) {
            return points.iterator().next();
        } else if (numPoints == 0) {
            throw new IllegalArgumentException("No points given to calculate center for.");
        }

        double sumLatitudes = 0.0;
        double sumZeta = 0.0;
        double sumXi = 0.0;

        for (GeoPoint2D point : points) {
            double latitude = point.getLatitude();
            sumLatitudes += latitude;

            double longitudeRad = point.getLongitude() * DEGREES_TO_RADIANS_FACTOR;
            sumZeta += Math.sin(longitudeRad);
            sumXi += Math.cos(longitudeRad);
        }

        double centerLatitude = sumLatitudes / numPoints;
        double centerLongitude = Math.atan2(sumZeta / numPoints, sumXi / numPoints) * RADIANS_TO_DEGREES_FACTOR;

        return new GeoPoint2D(centerLatitude, centerLongitude);
    }

    /**
     * Calculates the distance between both coordinates using the
     * Haversine method. This method provides an accuracy of 0.5% which is okay
     * for small distances only.
     *
     * @param pointA first coordinate pair
     * @param pointB second coordinate pair
     * @return metric distance with an accuracy of 0.5%
     */
    private static Length greatCircleDistanceByHaversine(GeoPoint2D pointA, GeoPoint2D pointB) {
        // formula: https://en.wikipedia.org/wiki/Great-circle_distance

        // convert coordinates given in degrees to radians needed for calculation
        double latitude1Radians = Math.toRadians(pointA.getLatitude());
        double longitude1Radians = Math.toRadians(pointA.getLongitude());
        double latitude2Radians = Math.toRadians(pointB.getLatitude());
        double longitude2Radians = Math.toRadians(pointB.getLongitude());

        double deltaLatitude = Math.abs(latitude1Radians - latitude2Radians); // delta phi
        double deltaLongitude = Math.abs(longitude1Radians - longitude2Radians); // delta lambda

        double singleSineHalfDeltaLatitude = Math.sin(deltaLatitude / 2.0);
        double haversineDeltaLatitude = singleSineHalfDeltaLatitude * singleSineHalfDeltaLatitude;

        double singleSineHalfDeltaLongitude = Math.sin(deltaLongitude / 2.0);
        double haversineDeltaLongitude = singleSineHalfDeltaLongitude * singleSineHalfDeltaLongitude;

        double centralAngle = 2.0 * Math.asin(Math.sqrt(haversineDeltaLatitude + Math.cos(latitude1Radians) * Math.cos(latitude2Radians) * haversineDeltaLongitude));
        double distanceMeters = MEAN_RADIUS_EARTH_METERS * centralAngle;

        return Length.of(distanceMeters, Length.Unit.METERS);
    }

    private static Length addFlatVerticalDistance(GeoPoint3D pointA, GeoPoint3D pointB, DistanceCalculator<GeoPoint2D> lateralCalculator) {
        Length lateralDistance = lateralCalculator.calculateDistanceBetween(pointA, pointB);
        Length.Unit unit = lateralDistance.getUnit();
        double lateralDistanceValue = lateralDistance.getValue();
        double lateralDistanceSquared = lateralDistanceValue * lateralDistanceValue;

        double elevationA = pointA.getElevationMSL()
                                  .getValueAs(unit);

        double elevationB = pointB.getElevationMSL()
                                  .getValueAs(unit);

        double elevationDiff = elevationB - elevationA;
        double elevationDiffSquared = elevationDiff * elevationDiff;

        return Length.of(Math.sqrt(lateralDistanceSquared + elevationDiffSquared), unit);
    }
}
