package reiz.miniecommerce.modules.products.core.entities;

/**
 * One row of a product's spec sheet: "Taxa de atualização" / "144 Hz".
 *
 * <p>Order is meaningful and is the order the operator typed — the sheet reads top to bottom
 * the way a spec sheet does, and the comparison table lines products up by position.
 */
public record ProductSpec(String label, String value) {
}
