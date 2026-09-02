package reiz.miniecommerce.modules.auth.core.interfaces.repositories;

import reiz.miniecommerce.modules.auth.core.entities.AuthenticatedPrincipal;
import reiz.miniecommerce.modules.auth.core.entities.IssuedToken;

/**
 * Output port: mints the access token this API accepts on later requests.
 */
public interface AccessTokenIssuer {

    IssuedToken issue(AuthenticatedPrincipal principal);
}
