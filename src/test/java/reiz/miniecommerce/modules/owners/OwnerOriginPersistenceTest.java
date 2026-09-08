package reiz.miniecommerce.modules.owners;

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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the owner row against partial writes, which is the one way the shipping origin can
 * disappear without anybody touching it.
 *
 * <p>{@code OwnerRepositoryAdapter.save} hands Hibernate a detached entity built by
 * {@code OwnerJpaMapper.toEntity}, and a merge writes the <em>whole</em> row. A column the
 * mapper forgets is therefore not left alone — it is overwritten with null. Both directions
 * of that hazard are live here: claiming the seat writes {@code google_sub} and would erase
 * an origin the operator had already configured, and setting the origin writes
 * {@code origin_zip_code} and would erase the claim that makes the account an owner at all.
 *
 * <p>Neither failure raises anything. The request succeeds, the response looks right, and
 * the loss is only noticed when freight silently goes free or the owner is demoted to a
 * customer on their next sign-in. That is why this test exists as its own file.
 */
@SpringBootTest
class OwnerOriginPersistenceTest {

    /** Stands in for Google so a first sign-in can be driven from the test. */
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

    /**
     * The regression this file was written for: an operator configures the shipping origin
     * on a seat that has not been signed into yet, and the first sign-in claims it.
     *
     * <p>If {@code toEntity} stops carrying {@code originZipCode}, the claim wipes the CEP
     * and the store quietly reverts to charging no freight at all — every subsequent order
     * ships free and nothing anywhere reports a problem.
     */
    @Test
    void claimingTheSeatAtFirstSignInDoesNotWipeTheConfiguredOrigin() {
        String email = uniqueEmail();
        Owner seat = ownerRepository.save(Owner.builder()
                .email(email)
                .originZipCode("01310100")
                .build());

        String googleSub = "google-sub-" + UUID.randomUUID();
        signInAs(profile(googleSub, email));

        Owner claimed = ownerRepository.findById(seat.getId()).orElseThrow();
        assertThat(claimed.getGoogleSub()).as("the seat was actually claimed").isEqualTo(googleSub);
        assertThat(claimed.getOriginZipCode()).isEqualTo("01310100");
    }

    /**
     * The same hazard from the other side: a save that carries the origin but drops
     * {@code googleSub} unclaims the seat. The operator would be handed a customer token at
     * their next sign-in and lose the store — right after successfully saving a setting.
     */
    @Test
    void changingTheOriginDoesNotUnclaimTheSeat() {
        String googleSub = "google-sub-" + UUID.randomUUID();
        Owner seat = ownerRepository.save(Owner.builder()
                .email(uniqueEmail())
                .googleSub(googleSub)
                .build());

        Owner loaded = ownerRepository.findById(seat.getId()).orElseThrow();
        loaded.setOriginZipCode("22071900");
        ownerRepository.save(loaded);

        Owner reloaded = ownerRepository.findById(seat.getId()).orElseThrow();
        assertThat(reloaded.getOriginZipCode()).isEqualTo("22071900");
        assertThat(reloaded.getGoogleSub()).isEqualTo(googleSub);
        assertThat(ownerRepository.findByGoogleSub(googleSub)).isPresent();
    }

    /** A round trip through the mapper has to bring the column back, not just accept it. */
    @Test
    void theOriginSurvivesTheTripThroughPersistence() {
        Owner seat = ownerRepository.save(Owner.builder()
                .email(uniqueEmail())
                .originZipCode("30140071")
                .build());

        assertThat(ownerRepository.findById(seat.getId()).orElseThrow().getOriginZipCode())
                .isEqualTo("30140071");
    }

    // ---------- helpers ----------

    private void signInAs(GoogleProfile profile) {
        StubTokenVerifier.NEXT_PROFILE.set(profile);
        googleAuthService.signIn("stub-token");
    }

    private GoogleProfile profile(String subject, String email) {
        return GoogleProfile.builder()
                .subject(subject)
                .email(email)
                .emailVerified(true)
                .name("Dono da Loja")
                .pictureUrl("https://exemplo.com/foto.jpg")
                .build();
    }

    /** {@code owners.email} is unique and the database outlives the run, so no fixed value. */
    private String uniqueEmail() {
        return "owner-" + UUID.randomUUID() + "@exemplo.com";
    }
}
