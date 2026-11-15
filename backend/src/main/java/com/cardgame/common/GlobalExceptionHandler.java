package com.cardgame.common;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into an RFC 9457 {@link ProblemDetail} with French {@code title}/{@code detail}
 * (D21: the UI shows them as they arrive). Never exposes stack traces or SQL messages.
 * Also handles security exceptions forwarded by the entry point / access-denied handler.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** One invalid field, returned in {@code properties.errors}. */
    public record FieldViolation(String field, String message) {
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomain(DomainException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        problem.setTitle(e.title());
        return problem;
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail handleBadCredentials(BadCredentialsException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Nom d'utilisateur ou mot de passe invalide.");
        problem.setTitle("Échec de la connexion");
        return problem;
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Vous devez être connecté.");
        problem.setTitle("Non authentifié");
        return problem;
    }

    @ExceptionHandler(CsrfException.class)
    ProblemDetail handleCsrf(CsrfException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Jeton CSRF manquant ou invalide. Rechargez la page et réessayez.");
        problem.setTitle("Jeton CSRF invalide");
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Accès refusé.");
        problem.setTitle("Accès interdit");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Une erreur inattendue est survenue.");
        problem.setTitle("Erreur interne");
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Certains champs sont invalides.");
        problem.setTitle("Requête invalide");
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toViolation)
                .toList();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Malformed JSON or an unknown enum value (e.g. gender). */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Le corps de la requête est illisible ou contient une valeur inconnue.");
        problem.setTitle("Requête invalide");
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    private static FieldViolation toViolation(FieldError error) {
        return new FieldViolation(error.getField(), error.getDefaultMessage());
    }
}
