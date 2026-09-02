package reiz.miniecommerce.modules.auth;

import reiz.miniecommerce.modules.auth.adapters.out.google.AuthProperties;
import reiz.miniecommerce.modules.auth.adapters.out.google.GoogleAudienceValidator;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The audience check is what separates "a real Google token" from "a token meant for us".
 * Everything here is built by hand: no signature is needed, because this validator only
 * reads claims, and a token genuinely signed by Google for another application is exactly
 * the input that must be rejected.
 */
class GoogleAudienceValidatorTest {

    private static final String OUR_CLIENT_ID = "564752801639-abc.apps.googleusercontent.com";
    private static final String SOMEONE_ELSES = "999999999999-xyz.apps.googleusercontent.com";

    @Test
    void acceptsATokenIssuedForThisApplication() {
        assertThat(validate(OUR_CLIENT_ID, List.of(OUR_CLIENT_ID)).hasErrors()).isFalse();
    }

    @Test
    void rejectsATokenIssuedForAnotherApplication() {
        // Signed by Google, unexpired, issuer correct — and still an impersonation: whoever
        // holds it would arrive here as that account's sub if the audience went unchecked.
        OAuth2TokenValidatorResult result = validate(OUR_CLIENT_ID, List.of(SOMEONE_ELSES));

        assertThat(result.hasErrors()).isTrue();

        String description = result.getErrors().iterator().next().getDescription();
        assertThat(description).contains("not issued for this application");
    }

    @Test
    void rejectsATokenWithNoAudienceAtAll() {
        assertThat(validate(OUR_CLIENT_ID, null).hasErrors()).isTrue();
    }

    @Test
    void acceptsAnAudienceListThatIncludesUs() {
        // Google may address a token to several audiences; ours being among them is enough.
        assertThat(validate(OUR_CLIENT_ID, List.of(SOMEONE_ELSES, OUR_CLIENT_ID)).hasErrors())
                .isFalse();
    }

    @Test
    void rejectsEverythingWhileTheClientIdIsUnconfigured() {
        // The state a fresh deployment boots in. Refusing every sign-in is the safe half of
        // that: the alternative, an empty client id matching anything, would accept tokens
        // from every application on earth.
        assertThat(validate("", List.of(OUR_CLIENT_ID)).hasErrors()).isTrue();
        assertThat(validate("   ", List.of(OUR_CLIENT_ID)).hasErrors()).isTrue();
        assertThat(validate(null, List.of(OUR_CLIENT_ID)).hasErrors()).isTrue();
    }

    private OAuth2TokenValidatorResult validate(String configuredClientId, List<String> audience) {
        AuthProperties properties = new AuthProperties();
        properties.setGoogleClientId(configuredClientId);

        return new GoogleAudienceValidator(properties).validate(tokenWith(audience));
    }

    private Jwt tokenWith(List<String> audience) {
        Jwt.Builder token = Jwt.withTokenValue("does-not-matter")
                .header("alg", "RS256")
                .claim(JwtClaimNames.ISS, "https://accounts.google.com")
                .claim(JwtClaimNames.SUB, "1234567890");

        if (audience != null) {
            token.claim(JwtClaimNames.AUD, audience);
        }
        return token.build();
    }
}
