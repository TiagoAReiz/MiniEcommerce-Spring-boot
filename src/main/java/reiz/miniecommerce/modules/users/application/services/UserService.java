package reiz.miniecommerce.modules.users.application.services;

import reiz.miniecommerce.modules.users.core.entities.User;
import reiz.miniecommerce.modules.users.core.exceptions.CpfAlreadyUsedException;
import reiz.miniecommerce.modules.users.core.exceptions.UserNotFoundException;
import reiz.miniecommerce.modules.users.core.interfaces.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User profileOf(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    /**
     * Fills in what Google does not provide. Name, e-mail and photo are deliberately not
     * editable: they are refreshed from the ID token on every sign-in, so any edit here would
     * be silently reverted at the next login.
     */
    @Transactional
    public User completeProfile(UUID userId, String cpf, String phone) {
        User user = profileOf(userId);

        if (cpf != null && !cpf.equals(user.getCpf()) && userRepository.existsByCpf(cpf)) {
            throw new CpfAlreadyUsedException();
        }
        if (cpf != null) {
            user.setCpf(cpf);
        }
        if (phone != null) {
            user.setPhone(phone);
        }
        return userRepository.save(user);
    }
}
