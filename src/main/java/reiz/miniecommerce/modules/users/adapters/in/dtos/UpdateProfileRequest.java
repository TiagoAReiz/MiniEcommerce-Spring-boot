package reiz.miniecommerce.modules.users.adapters.in.dtos;

import reiz.miniecommerce.modules.users.adapters.in.validation.ValidCpf;
import jakarta.validation.constraints.Pattern;

/**
 * Both fields are optional: the front end may collect the CPF at checkout and the phone
 * later. A null field means "leave as is", not "clear it".
 */
public record UpdateProfileRequest(
        @ValidCpf String cpf,

        @Pattern(regexp = "\\+?\\d{10,15}", message = "deve ter entre 10 e 15 dígitos")
        String phone) {
}
