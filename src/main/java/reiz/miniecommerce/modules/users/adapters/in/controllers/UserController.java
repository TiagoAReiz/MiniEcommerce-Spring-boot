package reiz.miniecommerce.modules.users.adapters.in.controllers;

import reiz.miniecommerce.modules.auth.application.services.CurrentUserProvider;
import reiz.miniecommerce.modules.users.adapters.in.dtos.UpdateProfileRequest;
import reiz.miniecommerce.modules.users.adapters.in.dtos.UserResponse;
import reiz.miniecommerce.modules.users.application.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CurrentUserProvider currentUser;

    @GetMapping
    public UserResponse profile() {
        return UserResponse.from(userService.profileOf(currentUser.requireId()));
    }

    @PatchMapping
    public UserResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return UserResponse.from(userService.completeProfile(
                currentUser.requireId(), request.cpf(), request.phone()));
    }
}
