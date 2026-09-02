package reiz.miniecommerce.modules.auth.adapters.in.dtos;

import jakarta.validation.constraints.NotBlank;

/**
 * The ID token the front end received from Google Sign-In.
 */
public record GoogleSignInRequest(@NotBlank String idToken) {
}
