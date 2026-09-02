package reiz.miniecommerce.modules.auth.core.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A freshly minted access token for this API, plus how long it is good for.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssuedToken {

    private String token;
    private long expiresInSeconds;
}
