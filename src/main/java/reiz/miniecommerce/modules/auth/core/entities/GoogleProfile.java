package reiz.miniecommerce.modules.auth.core.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The claims this API takes from a verified Google ID token.
 *
 * <p>{@code subject} is Google's {@code sub}: opaque, stable, and the field accounts are
 * matched on. Email is stored for display and lookup, never used to identify the account,
 * since a Google account can change its email.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "subject")
public class GoogleProfile {

    private String subject;
    private String email;

    /** Google's own verification of that address. Never claim an account on an unverified one. */
    private boolean emailVerified;
    private String name;
    private String pictureUrl;
}
