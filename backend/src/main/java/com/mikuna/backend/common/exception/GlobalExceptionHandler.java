package com.mikuna.backend.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Traduce las excepciones de la API a ProblemDetail (RFC 9457) sin exponer detalles internos. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String INVALID_FORM = "Revisa los datos del formulario";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, INVALID_FORM);
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "Solicitud inválida"));
    }

    @ExceptionHandler(RegistrationValidationException.class)
    ResponseEntity<ProblemDetail> handleRegistrationValidation(RegistrationValidationException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, INVALID_FORM);
        problem.setProperty("errors", Map.of(ex.getField(), ex.getMessage()));
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ResponseEntity<ProblemDetail> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ProblemDetail> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem(HttpStatus.UNAUTHORIZED, ex.getMessage()));
    }

    @ExceptionHandler(AccountDisabledException.class)
    ResponseEntity<ProblemDetail> handleAccountDisabled(AccountDisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem(HttpStatus.FORBIDDEN, ex.getMessage()));
    }

    @ExceptionHandler(RestaurantNotFoundException.class)
    ResponseEntity<ProblemDetail> handleRestaurantNotFound(RestaurantNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(RestaurantStateException.class)
    ResponseEntity<ProblemDetail> handleRestaurantState(RestaurantStateException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem(HttpStatus.FORBIDDEN, ex.getMessage()));
    }

    @ExceptionHandler(RestaurantConflictException.class)
    ResponseEntity<ProblemDetail> handleRestaurantConflict(RestaurantConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Error no controlado en la API", ex);
        return ResponseEntity.internalServerError()
                .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo completar la operación"));
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }
}
