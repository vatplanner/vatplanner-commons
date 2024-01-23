package org.vatplanner.commons.geo;

import java.util.Objects;

import org.vatplanner.commons.exceptions.OutOfRange;
import org.vatplanner.commons.units.Length;

/**
 * Represents a 3-dimensional geographic point/coordinate.
 * <p>
 * Note that the reference for elevations may vary and currently does not get recorded.
 * </p>
 */
public class GeoPoint3D extends GeoPoint2D {
    private final Length elevationMSL;

    /**
     * Creates a new 3-dimensional geographic point.
     * <p>
     * Values must be in normal range, out-of-range values need to be normalized first: see {@link GeoPoint2D} and use
     * {@link #from(GeoPoint2D, Length)} in that case.
     * </p>
     *
     * @param latitude     latitude (north/south coordinate, &plusmn;90&deg;)
     * @param longitude    longitude (east/west coordinate, &plusmn;180&deg;)
     * @param elevationMSL elevation above mean sea level (MSL)
     * @throws OutOfRange if longitude exceeds &plusmn;180&deg; or latitude exceeds &plusmn;90&deg;
     */
    public GeoPoint3D(double latitude, double longitude, Length elevationMSL) {
        super(latitude, longitude);
        this.elevationMSL = elevationMSL;
    }

    /**
     * Creates a new 3-dimensional geographic point "extending" the given {@link GeoPoint2D} by an elevation.
     *
     * @param point        point to copy lateral coordinates from
     * @param elevationMSL elevation above mean sea level (MSL); exact reference may vary
     * @return 3-dimensional geographic point at same lateral position
     */
    public static GeoPoint3D from(GeoPoint2D point, Length elevationMSL) {
        return new GeoPoint3D(point.getLatitude(), point.getLongitude(), elevationMSL);
    }

    @Override
    public String toString() {
        return String.format("GeoPoint3D[lat=%f, lon=%f, elev=%f%s]", getLatitude(), getLongitude(), elevationMSL.getValue(), elevationMSL.getUnit().getShortName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), elevationMSL);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GeoPoint3D)) {
            return false;
        }

        GeoPoint3D other = (GeoPoint3D) obj;

        return (other.elevationMSL == this.elevationMSL) && super.equals(other);
    }
}
