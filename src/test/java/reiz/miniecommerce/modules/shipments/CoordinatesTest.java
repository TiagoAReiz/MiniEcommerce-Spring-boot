package reiz.miniecommerce.modules.shipments;

import reiz.miniecommerce.modules.shipments.core.entities.Coordinates;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * The distance rule, on its own — no Spring, no database, no network.
 *
 * <p>Every freight charge the store makes is this number times two configured multipliers,
 * so an error here is not a rounding nuisance: it is the whole price being wrong, silently,
 * on every order. The integration tests above cannot catch that — they exercise the
 * contingency rate, which never measures anything — so this is the only place the arithmetic
 * itself is checked.
 */
class CoordinatesTest {

    /** Avenida Paulista, São Paulo. */
    private static final Coordinates SAO_PAULO = new Coordinates(-23.5614, -46.6559);

    /** Copacabana, Rio de Janeiro. */
    private static final Coordinates RIO = new Coordinates(-22.9711, -43.1822);

    /**
     * One degree of arc is the tightest reference available, because it does not depend on
     * anyone's idea of where a city centre is: on a sphere of radius R it is exactly
     * {@code 2*pi*R/360}, which for the mean Earth radius is 111.195 km.
     *
     * <p>This is what pins the constant and the unit conversion. Radians mistaken for
     * degrees, or a radius in metres, misses this by orders of magnitude rather than by the
     * few kilometres a city-pair assertion would tolerate.
     */
    @Test
    void oneDegreeOfArcMatchesTheMeanEarthRadius() {
        assertThat(new Coordinates(0, 0).distanceKmTo(new Coordinates(0, 1)))
                .isEqualByComparingTo(new BigDecimal("111.20"));

        // and along a meridian it has to be the same number, or latitude and longitude are
        // being scaled differently somewhere
        assertThat(new Coordinates(0, 0).distanceKmTo(new Coordinates(1, 0)))
                .isEqualByComparingTo(new BigDecimal("111.20"));
    }

    /**
     * A real pair, against the published air distance between the two cities (~360 km). The
     * tolerance is honest about the input rather than about the formula: the endpoints are
     * one street each, not the cities, so a few kilometres of disagreement is the choice of
     * point and not a defect. It is still tight enough that a swapped latitude/longitude or
     * a sign error lands nowhere near it.
     */
    @Test
    void measuresAKnownDistanceBetweenTwoRealAddresses() {
        assertThat(SAO_PAULO.distanceKmTo(RIO).doubleValue())
                .isCloseTo(360.9, within(5.0));
    }

    /**
     * Guards the short-distance case the store will actually see most: two CEPs in the same
     * city. The haversine was chosen over the law of cosines precisely for this, and a
     * regression to the cheaper formula shows up as noise here rather than as an exception.
     */
    @Test
    void measuresShortDistancesInsteadOfCollapsingThemToZero() {
        // ~1.6 km apart along Avenida Paulista
        Coordinates nearby = new Coordinates(-23.5614, -46.6402);

        assertThat(SAO_PAULO.distanceKmTo(nearby).doubleValue())
                .isCloseTo(1.6, within(0.2));
    }

    /** The customer who lives at the store's own address must not be charged for distance. */
    @Test
    void thereIsNoDistanceBetweenAPointAndItself() {
        assertThat(SAO_PAULO.distanceKmTo(SAO_PAULO)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /**
     * Which end is the store and which is the customer cannot change the price. The service
     * always measures origin-to-destination, so an asymmetry would make the charge depend on
     * argument order — a bug that would never surface as an error, only as a wrong amount.
     */
    @Test
    void theDistanceIsTheSameInBothDirections() {
        assertThat(SAO_PAULO.distanceKmTo(RIO)).isEqualByComparingTo(RIO.distanceKmTo(SAO_PAULO));
    }

    /**
     * The coordinates arrive parsed from a third-party payload, so the range check is what
     * keeps a provider's bad answer from becoming a plausible-looking distance. Rejecting it
     * here means the gateway catches it and prices the order at the contingency rate, which
     * is the documented behaviour for "nothing to measure".
     */
    @Test
    void aLatitudeOutsideTheGlobeIsRejected() {
        assertThatThrownBy(() -> new Coordinates(90.1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Latitude");

        assertThatThrownBy(() -> new Coordinates(-90.1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Latitude");
    }

    @Test
    void aLongitudeOutsideTheGlobeIsRejected() {
        assertThatThrownBy(() -> new Coordinates(0, 180.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Longitude");

        assertThatThrownBy(() -> new Coordinates(0, -180.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Longitude");
    }

    /** The poles and the antimeridian are real places; the check is inclusive on purpose. */
    @Test
    void theEdgesOfTheRangeAreValidPlaces() {
        assertThat(new Coordinates(90, 180)).isNotNull();
        assertThat(new Coordinates(-90, -180)).isNotNull();
    }
}
