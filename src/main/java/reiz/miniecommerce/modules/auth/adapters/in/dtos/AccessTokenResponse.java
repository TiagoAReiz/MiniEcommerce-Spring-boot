package reiz.miniecommerce.modules.auth.adapters.in.dtos;

import reiz.miniecommerce.modules.auth.core.entities.IssuedToken;

/**
 * The access token this API issues, in the shape a Bearer client expects.
 */
public record AccessTokenResponse(String accessToken, String tokenType, long expiresIn) {

    public static AccessTokenResponse from(IssuedToken issued) {
        return new AccessTokenResponse(issued.getToken(), "Bearer", issued.getExpiresInSeconds());
    }
}
