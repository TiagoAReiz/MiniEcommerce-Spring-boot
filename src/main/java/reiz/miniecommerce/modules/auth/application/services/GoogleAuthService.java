package reiz.miniecommerce.modules.auth.application.services;

import reiz.miniecommerce.modules.auth.core.entities.AuthenticatedPrincipal;
import reiz.miniecommerce.modules.auth.core.entities.GoogleProfile;
import reiz.miniecommerce.modules.auth.core.entities.IssuedToken;
import reiz.miniecommerce.modules.auth.core.interfaces.repositories.AccessTokenIssuer;
import reiz.miniecommerce.modules.auth.core.interfaces.repositories.GoogleTokenVerifier;
import reiz.miniecommerce.modules.owners.core.entities.Owner;
import reiz.miniecommerce.modules.owners.core.interfaces.repositories.OwnerRepository;
import reiz.miniecommerce.modules.users.core.entities.User;
import reiz.miniecommerce.modules.users.core.interfaces.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Sign-in use case: verify the Google ID token, resolve or create the account, and hand
 * back an access token for this API.
 *
 * <p>Owners are checked first: an account registered as an owner signs in as one. Everyone
 * else is a customer, created on first sign-in from whatever Google provided.
 */
@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final GoogleTokenVerifier googleTokenVerifier;
    private final AccessTokenIssuer accessTokenIssuer;
    private final UserRepository userRepository;
    private final OwnerRepository ownerRepository;

    @Transactional
    public IssuedToken signIn(String googleIdToken) {
        GoogleProfile profile = googleTokenVerifier.verify(googleIdToken);

        AuthenticatedPrincipal principal = ownerRepository.findByGoogleSub(profile.getSubject())
                .or(() -> claimProvisionedOwner(profile))
                .map(this::asPrincipal)
                .orElseGet(() -> asPrincipal(findOrCreateUser(profile)));

        return accessTokenIssuer.issue(principal);
    }

    /**
     * Links a pre-provisioned owner row to the Google account signing in.
     *
     * <p>Owners are inserted by the operator with an e-mail and no {@code google_sub}, since
     * that value only exists once the person signs in. The first sign-in from a matching
     * address claims the row.
     *
     * <p>Two guards make the match safe. The address must be one Google itself has verified,
     * otherwise an account could be created on someone else's address to take the store over.
     * And a row whose {@code google_sub} is already set is never re-pointed: the owner seat is
     * claimed once and stays claimed.
     */
    private Optional<Owner> claimProvisionedOwner(GoogleProfile profile) {
        if (!profile.isEmailVerified()) {
            return Optional.empty();
        }
        return ownerRepository.findByEmail(profile.getEmail())
                .filter(owner -> owner.getGoogleSub() == null)
                .map(owner -> {
                    owner.setGoogleSub(profile.getSubject());
                    return ownerRepository.save(owner);
                });
    }

    private User findOrCreateUser(GoogleProfile profile) {
        return userRepository.findByGoogleSub(profile.getSubject())
                .map(existing -> refresh(existing, profile))
                .orElseGet(() -> userRepository.save(User.fromGoogleProfile(
                        profile.getSubject(),
                        profile.getName(),
                        profile.getEmail(),
                        profile.getPictureUrl())));
    }

    /** Google is the source of truth for these fields, so pick up any change at sign-in. */
    private User refresh(User user, GoogleProfile profile) {
        user.setName(profile.getName());
        user.setEmail(profile.getEmail());
        user.setPhotoUrl(profile.getPictureUrl());
        return userRepository.save(user);
    }

    private AuthenticatedPrincipal asPrincipal(User user) {
        return AuthenticatedPrincipal.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(AuthenticatedPrincipal.Role.USER)
                .build();
    }

    private AuthenticatedPrincipal asPrincipal(Owner owner) {
        return AuthenticatedPrincipal.builder()
                .id(owner.getId())
                .email(owner.getEmail())
                .role(AuthenticatedPrincipal.Role.OWNER)
                .build();
    }
}
