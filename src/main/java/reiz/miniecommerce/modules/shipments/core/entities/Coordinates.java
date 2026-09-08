package reiz.miniecommerce.modules.shipments.core.entities;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A point on the globe, and the only place that knows how far two of them are.
 *
 * <p>Kept as a domain value object rather than a helper in the service so the distance rule
 * can be tested without Spring, without a database and without the network.
 */
public record Coordinates(double latitude, double longitude) {

    /** Mean Earth radius, the usual choice for a spherical approximation. */
    private static final double EARTH_RADIUS_KM = 6371.0088;

    public Coordinates {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude out of range: " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude out of range: " + longitude);
        }
    }

    /**
     * Great-circle distance — the straight line over the surface, not the road.
     *
     * <p>Haversine rather than the simpler spherical law of cosines: the latter loses its
     * precision on short distances, which is exactly the case that shows up most here, two
     * CEPs in the same city.
     */
    public BigDecimal distanceKmTo(Coordinates other) {
        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(other.latitude);
        double deltaLat = Math.toRadians(other.latitude - latitude);
        double deltaLon = Math.toRadians(other.longitude - longitude);

        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(deltaLon / 2), 2);

        double km = 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
        return BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP);
    }
}
