package reiz.miniecommerce.config;

import reiz.miniecommerce.modules.auth.adapters.out.google.AuthProperties;
import reiz.miniecommerce.modules.auth.adapters.out.google.GoogleAudienceValidator;
import reiz.miniecommerce.modules.auth.adapters.out.google.JwtAccessTokenIssuer;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Stateless Bearer-token security.
 *
 * <p>Two different JWTs are in play and they must not be confused. Google's ID token is
 * only ever accepted at {@code POST /auth/google}, verified there against Google's keys.
 * Every other endpoint accepts only tokens this API signed itself.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                                   CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // No cookie carries authentication, so a cross-site request cannot ride on
                // an ambient credential. Re-enable this the day sessions or cookies appear.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        // public because the gateway has no token from us;
                        // the signature check inside the handler is the guard
                        .requestMatchers("/webhooks/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // the API description is not a secret and the front end team needs it
                        // before they have any token to read it with
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/products/**").permitAll()
                        .requestMatchers("/order-items/**").hasAnyRole("USER", "OWNER")
                        .requestMatchers("/reviews/**").hasAnyRole("USER", "OWNER")
                        .requestMatchers("/users/**").hasAnyRole("USER", "OWNER")
                        .requestMatchers("/cart/**").hasAnyRole("USER", "OWNER")
                        .requestMatchers("/orders/**").hasAnyRole("USER", "OWNER")
                        .requestMatchers("/payments/**").hasAnyRole("USER", "OWNER")
                        .requestMatchers("/shipments/**").hasAnyRole("USER", "OWNER")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .build();
    }

    /**
     * Lets the browser call this API from the front end's origin.
     *
     * <p>Credentials stay off: authentication here is a Bearer header the front end sends
     * deliberately, not a cookie the browser attaches on its own. Allowing credentials would
     * also forbid ever widening the origin list, since the two cannot be combined.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));

        // Without this the front end cannot read the Location of what it just created: a
        // browser hides every response header that is not named here.
        config.setExposedHeaders(List.of("Location"));

        config.setAllowCredentials(false);
        config.setMaxAge(properties.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** Decoder for the tokens this API issues: same secret used to sign them. */
    @Bean
    public JwtDecoder jwtDecoder(AuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(secretKey(properties))
                .macAlgorithm(AuthProperties.ALGORITHM)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.getIssuer()));
        return decoder;
    }

    @Bean
    public JwtEncoder jwtEncoder(AuthProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    /**
     * Decoder for Google's ID tokens. Keys come from Google's JWKS, discovered from the
     * issuer and cached.
     *
     * <p>The audience validator is the part that must not be skipped: without it any token
     * signed by Google is accepted, including one issued for a different application, which
     * lets its holder sign in as that account here.
     */
    @Bean
    public JwtDecoder googleJwtDecoder(AuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withIssuerLocation(properties.getGoogleIssuer())
                .build();

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.getGoogleIssuer()),
                new GoogleAudienceValidator(properties)));
        return decoder;
    }

    /** Maps the {@code role} claim onto a Spring authority, so {@code hasRole('OWNER')} works. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthorityPrefix("ROLE_");
        authorities.setAuthoritiesClaimName(JwtAccessTokenIssuer.ROLE_CLAIM);

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private SecretKeySpec secretKey(AuthProperties properties) {
        return new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
