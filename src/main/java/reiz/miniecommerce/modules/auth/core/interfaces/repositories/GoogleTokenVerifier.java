package reiz.miniecommerce.modules.auth.core.interfaces.repositories;

import reiz.miniecommerce.modules.auth.core.entities.GoogleProfile;

/**
 * Output port: turns a raw Google ID token into a verified profile, or fails.
 * The core does not know that verification means JWKS, RSA and Nimbus.
 */
public interface GoogleTokenVerifier {

    GoogleProfile verify(String idToken);
}
