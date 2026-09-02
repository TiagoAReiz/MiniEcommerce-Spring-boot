package reiz.miniecommerce.modules.users.adapters.in.validation;

import br.com.caelum.stella.validation.CPFValidator;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Checks the shape here and the check digits with Caelum Stella.
 *
 * <p>Both halves are needed. A regex alone accepts {@code 00000000000} and
 * {@code 12345678901}, neither of which is a CPF anyone can hold. Stella alone accepts
 * {@code 123.456.789-09}, because it tolerates punctuation even when configured for the
 * unformatted form — and we store eleven bare digits, since {@code users.cpf} is
 * {@code VARCHAR(11)} and a formatted value would be truncated on its way in.
 */
public class CpfConstraintValidator implements ConstraintValidator<ValidCpf, String> {

    private static final Pattern ELEVEN_DIGITS = Pattern.compile("\\d{11}");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        if (!ELEVEN_DIGITS.matcher(value).matches()) {
            return reject(context, "deve ter 11 dígitos, sem pontuação");
        }
        // built per call rather than shared: Stella does not document the validator as
        // thread-safe, and a bean-validation instance is used from every request thread
        if (!new CPFValidator().invalidMessagesFor(value).isEmpty()) {
            return reject(context, "CPF inválido");
        }
        return true;
    }

    /** Replaces the default message so the caller learns which of the two rules it broke. */
    private boolean reject(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
