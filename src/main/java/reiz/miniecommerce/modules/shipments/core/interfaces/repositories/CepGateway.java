package reiz.miniecommerce.modules.shipments.core.interfaces.repositories;

import reiz.miniecommerce.modules.shipments.core.entities.Coordinates;

import java.util.Optional;

/**
 * Output port for turning a Brazilian postal code into a point on the map. The core owns
 * this contract; adapters implement it.
 */
public interface CepGateway {

    /**
     * Empty when the CEP is unknown, carries no coordinates, or the provider could not be
     * reached. The caller cannot tell those apart on purpose — all three mean the same thing
     * to a quote: there is nothing to measure, so price it by the contingency rate.
     */
    Optional<Coordinates> locate(String zipCode);
}
