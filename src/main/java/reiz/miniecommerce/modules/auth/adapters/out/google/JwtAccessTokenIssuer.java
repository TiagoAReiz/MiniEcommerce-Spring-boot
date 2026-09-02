package reiz.miniecommerce.modules.auth.adapters.out.google;

import reiz.miniecommerce.modules.auth.core.entities.AuthenticatedPrincipal;
import reiz.miniecommerce.modules.auth.core.entities.IssuedToken;
import reiz.miniecommerce.modules.auth.core.interfaces.repositories.AccessTokenIssuer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Mints this API's own access token, signed with the application secret.
 *
 * <p>The token carries the domain user id as {@code sub} and the role as a claim, so
 * authorising a request costs no database round trip.
 */
@Component
@RequiredArgsConstructor
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    public static final String ROLE_CLAIM = "role";

    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;

    @Override
    public IssuedToken issue(AuthenticatedPrincipal principal) {
        Instant now = Instant.now();
        long ttl = properties.getTokenTtl().toSeconds();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttl))
                .subject(principal.getId().toString())
                .claim("email", principal.getEmail())
                .claim(ROLE_CLAIM, principal.getRole().name())
                .build();

        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(AuthProperties.ALGORITHM).build(), claims))
                .getTokenValue();

        return IssuedToken.builder()
                .token(token)
                .expiresInSeconds(ttl)
                .build();
    }
}
