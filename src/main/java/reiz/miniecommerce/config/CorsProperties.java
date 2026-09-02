package reiz.miniecommerce.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /**
     * Origins allowed to call this API from a browser. Listed explicitly rather than
     * wildcarded: a wildcard lets any page on the internet drive the API with a token it
     * managed to obtain.
     */
    private List<String> allowedOrigins = List.of("http://localhost:3000");

    /** How long a browser may cache the preflight answer, saving an OPTIONS per request. */
    private Duration maxAge = Duration.ofHours(1);
}
