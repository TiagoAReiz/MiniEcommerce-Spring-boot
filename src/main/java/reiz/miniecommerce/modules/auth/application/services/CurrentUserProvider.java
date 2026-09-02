package reiz.miniecommerce.modules.auth.application.services;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Reads who is calling from the validated token.
 *
 * <p>Controllers never take an id from the path or the body to decide whose data to touch —
 * that would let a caller name someone else. Identity comes from here, and only from here.
 */
@Component
public class CurrentUserProvider {

    public UUID requireId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("No authenticated principal on a secured endpoint");
        }
        return UUID.fromString(jwt.getSubject());
    }
}
