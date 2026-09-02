package reiz.miniecommerce.modules.auth.adapters.in.controllers;

import reiz.miniecommerce.modules.auth.adapters.in.dtos.AccessTokenResponse;
import reiz.miniecommerce.modules.auth.adapters.in.dtos.GoogleSignInRequest;
import reiz.miniecommerce.modules.auth.application.services.GoogleAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final GoogleAuthService googleAuthService;

    @PostMapping("/google")
    public AccessTokenResponse signInWithGoogle(@Valid @RequestBody GoogleSignInRequest request) {
        return AccessTokenResponse.from(googleAuthService.signIn(request.idToken()));
    }
}
