package reiz.miniecommerce.modules.shipments.adapters.in.dtos;

import java.time.OffsetDateTime;

/** Omit {@code shippedAt} to use the current time. */
public record MarkShippedRequest(OffsetDateTime shippedAt) {
}
