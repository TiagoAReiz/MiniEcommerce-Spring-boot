package reiz.miniecommerce.modules.shipments.adapters.out.cep;

import reiz.miniecommerce.modules.shipments.core.entities.Coordinates;
import reiz.miniecommerce.modules.shipments.core.interfaces.repositories.CepGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * Resolves a CEP through BrasilAPI's v2 endpoint, which aggregates several providers and,
 * unlike ViaCEP, returns coordinates.
 *
 * <p>Answers are cached: the coordinates of a postal code do not move, the store's own
 * origin is looked up on every single checkout, and customers repeat addresses. The cache
 * is what keeps a provider outage from being felt on most orders.
 */
@Component
@Slf4j
public class BrasilApiCepGateway implements CepGateway {

    /**
     * The cache is keyed by the CEP as {@link #digitsOf} leaves it, not by the string the
     * caller happened to type. "01001-000" and "01001000" are the same postal code, and
     * keying on the raw argument stores the same coordinates twice — for thirty days each —
     * halving the hit rate that is the whole reason this cache exists.
     *
     * <p>Normalizing in the key expression rather than in a second cached method is what
     * keeps this safe: the expression is evaluated by the proxy, before the body runs, so
     * there is no inner {@code @Cacheable} method left for this class to call on
     * {@code this}. Such a call would go around the proxy and quietly disable the cache
     * without anything failing.
     */
    private static final String NORMALIZED_CEP_KEY =
            "T(reiz.miniecommerce.modules.shipments.adapters.out.cep.BrasilApiCepGateway).digitsOf(#zipCode)";

    private final RestClient restClient;

    public BrasilApiCepGateway(ShippingProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.getCepBaseUrl())
                .requestFactory(timeBoundedFactory(properties))
                .build();
    }

    /**
     * Bounds both phases of the call. Without a read timeout a provider that accepts the
     * connection and then goes quiet holds the request thread until the container gives up.
     */
    private static SimpleClientHttpRequestFactory timeBoundedFactory(ShippingProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeout());
        factory.setReadTimeout(properties.getTimeout());
        return factory;
    }

    /**
     * Never throws: every failure is an empty answer, because the quote above already has a
     * contingency for "no distance" and a checkout must not fall over because a free public
     * CEP service is having a bad afternoon.
     *
     * <p>Which is also why nothing empty is ever stored. Spring unwraps the empty Optional to
     * null and {@code unless} refuses it, so an afternoon of provider errors — or a CEP that
     * was never usable to begin with — cannot leave an entry behind that answers "no
     * coordinates" for the next thirty days.
     */
    @Override
    @Cacheable(cacheNames = "cepCoordinates", key = NORMALIZED_CEP_KEY, unless = "#result == null")
    public Optional<Coordinates> locate(String zipCode) {
        String digits = digitsOf(zipCode);
        if (digits.length() != 8) {
            log.warn("Not a usable CEP, skipping the lookup: {}", zipCode);
            return Optional.empty();
        }

        try {
            CepPayload payload = restClient.get()
                    .uri("/api/cep/v2/{cep}", digits)
                    .retrieve()
                    .body(CepPayload.class);

            return coordinatesOf(payload, digits);

        } catch (Exception e) {
            // Includes the 404 for an unknown CEP: to a quote, "does not exist" and "could
            // not ask" are the same absence.
            log.warn("Could not resolve CEP {} at BrasilAPI: {}", digits, e.toString());
            return Optional.empty();
        }
    }

    /**
     * BrasilAPI documents the coordinates as optional, and they really do come back missing
     * for some postal codes — an empty object rather than an error.
     */
    private Optional<Coordinates> coordinatesOf(CepPayload payload, String cep) {
        if (payload == null || payload.location() == null || payload.location().coordinates() == null) {
            log.warn("BrasilAPI has no coordinates for CEP {}", cep);
            return Optional.empty();
        }

        CepPayload.Point point = payload.location().coordinates();
        if (point.latitude() == null || point.longitude() == null) {
            log.warn("BrasilAPI has no coordinates for CEP {}", cep);
            return Optional.empty();
        }

        try {
            return Optional.of(new Coordinates(
                    Double.parseDouble(point.latitude()),
                    Double.parseDouble(point.longitude())));

            // covers both an unparseable number and a latitude Coordinates rejects
        } catch (IllegalArgumentException e) {
            log.warn("BrasilAPI returned unusable coordinates for CEP {}: {}", cep, e.toString());
            return Optional.empty();
        }
    }

    /**
     * Public because the cache key expression calls it: the key and the CEP that is actually
     * looked up have to be produced by the same code, or a formatting difference would come
     * back as a cache miss again.
     */
    public static String digitsOf(String zipCode) {
        return zipCode == null ? "" : zipCode.replaceAll("[^0-9]", "");
    }

    /** Only the fields a quote needs; the payload carries plenty more. */
    private record CepPayload(Location location) {

        private record Location(Point coordinates) {
        }

        /** BrasilAPI sends these as strings, not numbers. */
        private record Point(String latitude, String longitude) {
        }
    }
}
