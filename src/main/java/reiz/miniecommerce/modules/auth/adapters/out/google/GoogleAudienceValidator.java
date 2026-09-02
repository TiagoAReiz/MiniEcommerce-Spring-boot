package reiz.miniecommerce.modules.auth.adapters.out.google;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Checks that a Google ID token was issued for this application.
 *
 * <p>Signature and issuer only prove the token came from Google; they say nothing about who
 * it was made for. Google signs a token for every application that asks, so accepting one
 * addressed to somebody else's client id would let its holder sign in here as that
 * {@code sub} — a real Google token, a valid signature, and the wrong account. This is the
 * check that stops it, and it is the one people forget: configuring the issuer alone gets
 * you signature and expiry validation, never audience.
 *
 * <p>An unset client id rejects everything. That is how a fresh deployment starts, and
 * refusing every sign-in is the safe direction to fail in.
 */
@RequiredArgsConstructor
public class GoogleAudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final AuthProperties properties;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String clientId = properties.getGoogleClientId();

        if (clientId == null || clientId.isBlank()) {
            return failure("This application has no Google client id configured");
        }

        List<String> audience = token.getAudience();
        if (audience == null || !audience.contains(clientId)) {
            return failure("The token was not issued for this application");
        }

        return OAuth2TokenValidatorResult.success();
    }

    private OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null));
    }
}
