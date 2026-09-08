package reiz.miniecommerce.modules.shipments.core.exceptions;

/**
 * The store has no origin CEP, so there is nothing to measure freight from.
 *
 * <p>This refuses the sale instead of letting it through for the price of the goods alone.
 * Every order carries freight, and an unconfigured origin is the operator forgetting to set
 * one — not a decision to deliver for free. Shipping free by default would be the kind of
 * mistake nobody notices until the accounting does, because each individual order looks
 * perfectly normal.
 *
 * <p>Fails closed, in the same sense as the secrets listed in the README: absent
 * configuration makes the feature refuse rather than quietly permit.
 */
public class ShippingOriginNotConfiguredException extends RuntimeException {

    public ShippingOriginNotConfiguredException() {
        super("The store has no origin CEP configured; freight cannot be quoted");
    }
}
