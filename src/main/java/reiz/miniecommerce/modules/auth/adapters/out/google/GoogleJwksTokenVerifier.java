package reiz.miniecommerce.modules.auth.adapters.out.google;

import reiz.miniecommerce.modules.auth.core.entities.GoogleProfile;
import reiz.miniecommerce.modules.auth.core.exceptions.InvalidGoogleTokenException;
import reiz.miniecommerce.modules.auth.core.interfaces.repositories.GoogleTokenVerifier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Verifies Google ID tokens against Google's published keys (JWKS).
 *
 * <p>The decoder handed in is configured with the issuer <em>and</em> an audience check.
 * Signature alone is not enough: a token signed by Google but issued for someone else's
 * client id is a valid Google token that must not be accepted here.
 */
@Component
public class GoogleJwksTokenVerifier implements GoogleTokenVerifier {

    private final JwtDecoder googleJwtDecoder;

    public GoogleJwksTokenVerifier(@Qualifier("googleJwtDecoder") JwtDecoder googleJwtDecoder) {
        this.googleJwtDecoder = googleJwtDecoder;
    }

    @Override
    public GoogleProfile verify(String idToken) {
        try {
            Jwt jwt = googleJwtDecoder.decode(idToken);
            return GoogleProfile.builder()
                    .subject(jwt.getSubject())
                    .email(jwt.getClaimAsString("email"))
                    .emailVerified(Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")))
                    .name(jwt.getClaimAsString("name"))
                    .pictureUrl(jwt.getClaimAsString("picture"))
                    .build();
        } catch (JwtException e) {
            throw new InvalidGoogleTokenException("Google ID token rejected: " + e.getMessage(), e);
        }
    }
}
