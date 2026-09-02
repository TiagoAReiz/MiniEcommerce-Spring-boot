package reiz.miniecommerce.modules.auth.adapters.out.google;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

import java.time.Duration;

/**
 * Settings for Google verification and for the tokens this API issues.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    public static final MacAlgorithm ALGORITHM = MacAlgorithm.HS256;

    /** Google issuer, used to locate the JWKS endpoint. */
    private String googleIssuer = "https://accounts.google.com";

    /** OAuth client id of this application. Google tokens issued for anything else are rejected. */
    private String googleClientId;

    /** Issuer claim written into the tokens this API mints. */
    private String issuer = "miniecommerce";

    /** How long an issued token stays valid. */
    private Duration tokenTtl = Duration.ofHours(1);

    /** HMAC signing secret. Must be at least 32 bytes for HS256. */
    private String secret;
}
