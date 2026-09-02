package reiz.miniecommerce.modules.auth;

import reiz.miniecommerce.modules.auth.application.services.GoogleAuthService;
import reiz.miniecommerce.modules.auth.core.entities.GoogleProfile;
import reiz.miniecommerce.modules.auth.core.interfaces.repositories.GoogleTokenVerifier;
import reiz.miniecommerce.modules.owners.core.entities.Owner;
import reiz.miniecommerce.modules.owners.core.interfaces.repositories.OwnerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers how a pre-provisioned owner row gets bound to a Google account.
 *
 * <p>Owners are inserted by migration with an e-mail and no {@code google_sub}, because that
 * value only exists once the person signs in. The first matching sign-in claims the row — and
 * that claim is what grants control of the store, so the two guards around it are the subject
 * of this test rather than the happy path alone.
 */
@SpringBootTest
class OwnerClaimTest {

    /**
     * Stands in for Google. Each test sets the profile the next sign-in will see, which is the
     * only way to exercise an unverified address or a mismatched subject.
     */
    @TestConfiguration
    static class StubTokenVerifier {

        static final AtomicReference<GoogleProfile> NEXT_PROFILE = new AtomicReference<>();

        @Bean
        @Primary
        GoogleTokenVerifier googleTokenVerifier() {
            return idToken -> NEXT_PROFILE.get();
        }
    }

    @Autowired private GoogleAuthService googleAuthService;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private JwtDecoder jwtDecoder;

    @Test
    void aVerifiedEmailClaimsTheWaitingOwnerSeat() {
        String email = uniqueEmail();
        Owner seat = provisionOwner(email, null);
        String googleSub = "google-sub-" + UUID.randomUUID();

        Jwt token = signInAs(profile(googleSub, email, true));

        assertThat(token.getClaimAsString("role")).isEqualTo("OWNER");
        assertThat(token.getSubject()).isEqualTo(seat.getId().toString());

        Owner claimed = ownerRepository.findById(seat.getId()).orElseThrow();
        assertThat(claimed.getGoogleSub()).isEqualTo(googleSub);
    }

    @Test
    void anUnverifiedEmailNeverClaimsTheOwnerSeat() {
        String email = uniqueEmail();
        Owner seat = provisionOwner(email, null);

        Jwt token = signInAs(profile("attacker-sub-" + UUID.randomUUID(), email, false));

        // Anyone controlling a domain can create a Google account on any address under it.
        // Without this guard, doing that on the store owner's address would hand over the
        // store to whoever got there first.
        assertThat(token.getClaimAsString("role")).isEqualTo("USER");
        assertThat(token.getSubject()).isNotEqualTo(seat.getId().toString());

        Owner untouched = ownerRepository.findById(seat.getId()).orElseThrow();
        assertThat(untouched.getGoogleSub()).isNull();
    }

    @Test
    void anAlreadyClaimedSeatIsNeverRepointedToAnotherAccount() {
        String email = uniqueEmail();
        String originalSub = "original-sub-" + UUID.randomUUID();
        Owner seat = provisionOwner(email, originalSub);

        Jwt token = signInAs(profile("newcomer-sub-" + UUID.randomUUID(), email, true));

        // A Google account can change its e-mail address. If the seat followed the address,
        // moving that address onto another account would move the store along with it.
        assertThat(token.getClaimAsString("role")).isEqualTo("USER");

        Owner untouched = ownerRepository.findById(seat.getId()).orElseThrow();
        assertThat(untouched.getGoogleSub()).isEqualTo(originalSub);
    }

    @Test
    void aClaimedOwnerStaysOwnerEvenAfterChangingTheirGoogleEmail() {
        Owner seat = provisionOwner(uniqueEmail(), null);
        String googleSub = "google-sub-" + UUID.randomUUID();

        signInAs(profile(googleSub, seat.getEmail(), true));

        // Signing in from a different address proves the second resolution came from the
        // stable subject rather than an e-mail match: no owner row carries this address.
        Jwt token = signInAs(profile(googleSub, uniqueEmail(), true));

        assertThat(token.getClaimAsString("role")).isEqualTo("OWNER");
        assertThat(token.getSubject()).isEqualTo(seat.getId().toString());
    }

    // ---------- helpers ----------

    private Jwt signInAs(GoogleProfile profile) {
        StubTokenVerifier.NEXT_PROFILE.set(profile);
        return jwtDecoder.decode(googleAuthService.signIn("stub-token").getToken());
    }

    private GoogleProfile profile(String subject, String email, boolean emailVerified) {
        return GoogleProfile.builder()
                .subject(subject)
                .email(email)
                .emailVerified(emailVerified)
                .name("Dono da Loja")
                .pictureUrl("https://exemplo.com/foto.jpg")
                .build();
    }

    private Owner provisionOwner(String email, String googleSub) {
        return ownerRepository.save(Owner.builder()
                .email(email)
                .googleSub(googleSub)
                .build());
    }

    /** {@code owners.email} is unique and the database outlives the run, so no fixed value. */
    private String uniqueEmail() {
        return "owner-" + UUID.randomUUID() + "@exemplo.com";
    }
}
