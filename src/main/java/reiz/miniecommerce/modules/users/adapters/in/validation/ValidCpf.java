package reiz.miniecommerce.modules.users.adapters.in.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;

/**
 * A real CPF: eleven digits with no punctuation, and check digits that actually add up.
 *
 * <p>Null passes, because every field carrying this is optional — the customer supplies a CPF
 * at checkout, not at sign-up, and null means "leave as is" rather than "clear it".
 */
@Documented
@Constraint(validatedBy = CpfConstraintValidator.class)
@Target({FIELD, PARAMETER, ANNOTATION_TYPE, RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCpf {

    String message() default "CPF inválido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
