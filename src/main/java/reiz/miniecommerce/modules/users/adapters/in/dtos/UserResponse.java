package reiz.miniecommerce.modules.users.adapters.in.dtos;

import reiz.miniecommerce.modules.users.core.entities.User;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The user as the front end sees it.
 *
 * <p>{@code googleSub} is never exposed: it is the internal identifier accounts are matched
 * on, and nothing outside the sign-in flow has a reason to know it.
 */
public record UserResponse(
        UUID id,
        String name,
        String email,
        String cpf,
        String phone,
        String photoUrl,
        boolean canCheckout,
        OffsetDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getCpf(),
                user.getPhone(),
                user.getPhotoUrl(),
                user.canCheckout(),
                user.getCreatedAt());
    }
}
