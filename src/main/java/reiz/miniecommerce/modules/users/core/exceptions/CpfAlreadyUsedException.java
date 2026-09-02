package reiz.miniecommerce.modules.users.core.exceptions;

/**
 * The CPF is already on another account. Enforced by {@code uq_users_cpf}; checked here so
 * the caller gets a clear 409 instead of a constraint violation.
 */
public class CpfAlreadyUsedException extends RuntimeException {

    public CpfAlreadyUsedException() {
        super("CPF already belongs to another account");
    }
}
