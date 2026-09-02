package reiz.miniecommerce.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

/**
 * The net under every controller: turns anything the modules did not anticipate into the
 * error shape the API contract promises, instead of whatever Spring would emit by default.
 *
 * <p>Ordered last on purpose. Spring resolves an exception by walking the advices in order
 * and taking the first one with <em>any</em> matching method — not the most specific match
 * across advices. A catch-all sitting anywhere but the end would answer for
 * {@code OrderNotFoundException} and friends, collapsing every curated 404 and 409 into a
 * generic 500.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * The exception message never reaches the caller.
     *
     * <p>Messages from below carry table and column names, file paths, SQL fragments and
     * sometimes the offending value itself — a constraint violation happily quotes the e-mail
     * or CPF that collided. That is a map of the schema and a leak of somebody's data handed
     * to whoever sent a malformed request.
     *
     * <p>What the caller gets instead is a correlation id, also written to the log line that
     * does carry the stack trace, so a report of "I got an error, id 4f3a1c" can be matched to
     * the exact failure.
     */
    /**
     * Rethrown, not handled.
     *
     * <p>Authorisation failures have to reach Spring Security's own filter, which is what
     * turns them into 401 and 403. Catching them here — and a handler for {@code Exception}
     * catches everything — answers 500 instead, so every {@code @PreAuthorize} in the
     * application silently stops reporting "forbidden" and starts reporting "we crashed".
     */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void rethrowForSecurityFilter(Exception e) throws Exception {
        throw e;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception e) {
        String incident = newIncidentId();
        log.error("Unhandled exception [incident {}]", incident, e);

        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro inesperado", "INTERNAL_ERROR", incident);
    }

    /**
     * Reaches here only when no module claimed it. A unique index or a RESTRICT foreign key
     * fired somewhere that did not expect it, which is a conflict rather than a crash — and
     * the driver's message names the constraint, so it stays in the log.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail onIntegrityViolation(DataIntegrityViolationException e) {
        String incident = newIncidentId();
        log.error("Unhandled integrity violation [incident {}]", incident, e);

        return problem(HttpStatus.CONFLICT,
                "A operação conflita com dados já existentes", "DATA_CONFLICT", incident);
    }

    /** Body that is not the JSON the endpoint expects: broken syntax, wrong type, truncated. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail onUnreadableBody(HttpMessageNotReadableException e) {
        log.debug("Rejected an unreadable request body", e);
        return problem(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido", "MALFORMED_REQUEST", null);
    }

    /** Typically an id in the path that is not a UUID. The caller's mistake, not a failure. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail onBadParameter(MethodArgumentTypeMismatchException e) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST,
                "Parâmetro inválido", "INVALID_PARAMETER", null);

        // the parameter name is ours, not user data, so naming it helps without leaking
        detail.setProperty("parameter", e.getName());
        return detail;
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail onWrongMethod(HttpRequestMethodNotSupportedException e) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED,
                "Método não permitido nesta rota", "METHOD_NOT_ALLOWED", null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail onWrongMediaType(HttpMediaTypeNotSupportedException e) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Use application/json", "UNSUPPORTED_MEDIA_TYPE", null);
    }

    private ProblemDetail problem(HttpStatus status, String detail, String code, String incident) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        if (incident != null) {
            problem.setProperty("incident", incident);
        }
        return problem;
    }

    /** Short enough for someone to read it off a screen and quote it in a support message. */
    private String newIncidentId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
