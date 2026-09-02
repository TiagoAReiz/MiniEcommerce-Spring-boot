package reiz.miniecommerce.modules.users.core.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Domain model for User. Free of persistence concerns: no JPA annotations,
 * no framework types. Other aggregates are referenced by id.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class User {

    private UUID id;
    private String googleSub;
    private String name;
    private String email;
    private String cpf;
    private String phone;
    private String photoUrl;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /** Builds an unsaved user from the claims of a Google ID token. */
    public static User fromGoogleProfile(String googleSub, String name, String email, String photoUrl) {
        return User.builder()
                .googleSub(googleSub)
                .name(name)
                .email(email)
                .photoUrl(photoUrl)
                .build();
    }

    /** Checkout requires the fields Google does not provide at sign-in. */
    public boolean canCheckout() {
        return cpf != null && phone != null;
    }
}
