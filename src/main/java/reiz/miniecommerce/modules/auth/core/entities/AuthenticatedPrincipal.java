package reiz.miniecommerce.modules.auth.core.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Who is logged in, as far as this API is concerned. Built after the Google ID token
 * has been verified, and carried inside the JWT this API issues.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class AuthenticatedPrincipal {

    private UUID id;
    private String email;
    private Role role;

    public enum Role {
        USER,
        OWNER
    }
}
