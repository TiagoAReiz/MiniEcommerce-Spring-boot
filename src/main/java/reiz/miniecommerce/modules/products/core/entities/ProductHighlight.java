package reiz.miniecommerce.modules.products.core.entities;

/**
 * One headline figure on a product card — "144" and "Hz", "2 TB" and "capacidade".
 *
 * <p>Value and unit are separate fields because they are typeset differently: the value large
 * and the unit small beside it. Storing "144 Hz" as one string would leave the front end
 * guessing where the number ends, and it guesses wrong on "2 TB" and "1 ms GtG".
 */
public record ProductHighlight(String value, String unit) {
}
