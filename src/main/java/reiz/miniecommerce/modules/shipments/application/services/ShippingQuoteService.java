package reiz.miniecommerce.modules.shipments.application.services;

import reiz.miniecommerce.modules.address.core.entities.Address;
import reiz.miniecommerce.modules.address.core.exceptions.AddressNotFoundException;
import reiz.miniecommerce.modules.address.core.interfaces.repositories.AddressRepository;
import reiz.miniecommerce.modules.owners.core.entities.Owner;
import reiz.miniecommerce.modules.owners.core.interfaces.repositories.OwnerRepository;
import reiz.miniecommerce.modules.shipments.adapters.out.cep.ShippingProperties;
import reiz.miniecommerce.modules.shipments.core.entities.Coordinates;
import reiz.miniecommerce.modules.shipments.core.entities.ShippingQuote;
import reiz.miniecommerce.modules.shipments.core.exceptions.ShippingOriginNotConfiguredException;
import reiz.miniecommerce.modules.shipments.core.interfaces.repositories.CepGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

/**
 * Prices the delivery of an order: distance from the store to the customer, times a rate.
 *
 * <p>Deliberately not transactional and deliberately not called from inside one. It makes
 * two network calls, and the connection pool is five wide — holding a database connection
 * across a third-party lookup would let a slow provider stall customers who are only
 * browsing the catalogue. {@code CheckoutService} quotes first and opens its transaction
 * afterwards.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShippingQuoteService {

    private final OwnerRepository ownerRepository;
    private final AddressRepository addressRepository;
    private final CepGateway cepGateway;
    private final ShippingProperties properties;

    /**
     * Two outcomes, told apart afterwards by whether a distance was recorded: the distance
     * was measured, or the lookup failed and the contingency rate applied. Both carry a
     * charge — every order pays freight.
     *
     * <p>A store with no origin gets neither: it throws, and the checkout stops. See
     * {@link ShippingOriginNotConfiguredException} for why that is better than shipping free.
     */
    public ShippingQuote quoteFor(UUID addressId) {
        String origin = originZipCode();

        Address destination = addressRepository.findById(addressId)
                .orElseThrow(() -> new AddressNotFoundException(addressId));

        // Short-circuited rather than resolving both and testing afterwards. Each lookup is
        // bounded by its own timeout, so asking for a destination whose answer cannot be used
        // would double what the customer waits on the checkout button during an outage — the
        // one case where both lookups are certain to fail.
        Optional<Coordinates> from = cepGateway.locate(origin);
        if (from.isEmpty()) {
            return contingencyFor(addressId, "the store origin");
        }

        Optional<Coordinates> to = cepGateway.locate(destination.getZipCode());
        if (to.isEmpty()) {
            return contingencyFor(addressId, "the destination CEP");
        }

        BigDecimal straightLineKm = from.get().distanceKmTo(to.get());
        return ShippingQuote.measured(costOf(straightLineKm), straightLineKm);
    }

    private ShippingQuote contingencyFor(UUID addressId, String whatWasMissing) {
        log.warn("Pricing address {} at the contingency rate: no coordinates for {}",
                addressId, whatWasMissing);
        return ShippingQuote.fallback(properties.getFallbackCost());
    }

    /**
     * Straight line corrected to road distance, then charged per kilometre. Rounded once, at
     * the end, so the cent the customer pays is the cent that was calculated.
     */
    private BigDecimal costOf(BigDecimal straightLineKm) {
        return straightLineKm
                .multiply(properties.getRoadFactor())
                .multiply(properties.getPricePerKm())
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * A blank origin is treated exactly like a missing one. The column is nullable and the
     * seed leaves it empty, so "not configured" is the state every new store starts in — and
     * it has to stop the sale rather than silently become a discount.
     */
    private String originZipCode() {
        return ownerRepository.findStore()
                .map(Owner::getOriginZipCode)
                .filter(zip -> !zip.isBlank())
                .orElseThrow(ShippingOriginNotConfiguredException::new);
    }
}
