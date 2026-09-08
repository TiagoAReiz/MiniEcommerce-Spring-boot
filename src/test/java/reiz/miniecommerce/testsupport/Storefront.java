package reiz.miniecommerce.testsupport;

import reiz.miniecommerce.modules.owners.core.entities.Owner;
import reiz.miniecommerce.modules.owners.core.interfaces.repositories.OwnerRepository;

/**
 * The shop every test that sells something needs to exist first.
 *
 * <p>Since V5 an order cannot be placed without an origin CEP to measure freight from: a shop
 * that has not configured one refuses the checkout instead of delivering for free. That turns
 * a configured store into a precondition of every test that reaches {@code POST /orders}, not
 * a detail of the freight tests — which is why this lives here and not in one of them.
 *
 * <p>Callers pair it with two properties, and both matter:
 *
 * <pre>
 * "app.shipping.cep-base-url=http://127.0.0.1:1"
 * "app.shipping.fallback-cost=0.00"
 * </pre>
 *
 * <p>The dead base URL is the important one. Without it every checkout in the suite would
 * resolve two postal codes against a live public API — turning unrelated tests into something
 * that fails on someone else's outage, and hammering a free service on every run. The zero
 * contingency rate then keeps freight out of the totals, so tests that are about stock,
 * payment or expiry can go on asserting the price of the goods and nothing else.
 */
public final class Storefront {

    /** Avenida Paulista. Any real CEP does, since no test that uses this ever resolves it. */
    public static final String ORIGIN_CEP = "01310100";

    private Storefront() {
    }

    /**
     * Idempotent, and deliberately only writes when the origin is missing. There is a single
     * owner row and it outlives the run, so overwriting unconditionally would fight the
     * freight tests, which set an origin of their own and put the previous one back.
     */
    public static void sellsWithFreight(OwnerRepository owners) {
        Owner store = owners.findStore().orElseThrow(
                () -> new IllegalStateException("No owner row: the V2 seed did not run"));

        if (store.getOriginZipCode() == null || store.getOriginZipCode().isBlank()) {
            store.setOriginZipCode(ORIGIN_CEP);
            owners.save(store);
        }
    }
}
