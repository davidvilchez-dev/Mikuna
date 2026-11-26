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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
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

    /** Falta la parte JSON «product» o el archivo «image» del multipart del catálogo. */
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(
            MissingServletRequestPartException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String field = "image".equals(ex.getRequestPartName()) ? "image" : "product";
        String message = "image".equals(field) ? "La imagen es obligatoria" : "Completa los datos del producto";
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, INVALID_FORM);
        problem.setProperty("errors", Map.of(field, message));
        return ResponseEntity.badRequest().body(problem);
    }

    /** El límite de 2 MB del multipart lo aplica el contenedor antes del servicio. */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(imageTooLarge(new ImageTooLargeException()).getBody());
    }

    @ExceptionHandler(ImageTooLargeException.class)
    ResponseEntity<ProblemDetail> imageTooLarge(ImageTooLargeException ex) {
        ProblemDetail problem = problem(HttpStatus.CONTENT_TOO_LARGE, ex.getMessage());
        problem.setProperty("errors", Map.of("image", ex.getMessage()));
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(problem);
    }

    @ExceptionHandler(ProductNotFoundException.class)
    ResponseEntity<ProblemDetail> handleProductNotFound(ProductNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(ProductConflictException.class)
    ResponseEntity<ProblemDetail> handleProductConflict(ProductConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler({AddressNotFoundException.class, CartLineNotFoundException.class})
    ResponseEntity<ProblemDetail> handleCartNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    /** 409 con el {@code code} que la interfaz usa para distinguir el caso (p. ej. otro restaurante). */
    @ExceptionHandler(CartConflictException.class)
    ResponseEntity<ProblemDetail> handleCartConflict(CartConflictException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("code", ex.getCode());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ProblemDetail> handleOrderNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotificationNotFound(NotificationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(OrderConflictException.class)
    ResponseEntity<ProblemDetail> handleOrderConflict(OrderConflictException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("code", ex.getCode());
        if (ex.getCheckout() != null) {
            problem.setProperty("checkout", ex.getCheckout());
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(RatingConflictException.class)
    ResponseEntity<ProblemDetail> handleRatingConflict(RatingConflictException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("code", ex.getCode());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(AdminAccountProtectedException.class)
    ResponseEntity<ProblemDetail> handleAdminProtected(AdminAccountProtectedException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("code", AdminAccountProtectedException.CODE);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    ResponseEntity<ProblemDetail> handleAccountNotFound(AccountNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(LocationRejectedException.class)
    ResponseEntity<ProblemDetail> handleLocationRejected(LocationRejectedException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("code", ex.getCode());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(DeliveryConflictException.class)
    ResponseEntity<ProblemDetail> handleDeliveryConflict(DeliveryConflictException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("code", ex.getCode());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(PaymentStateException.class)
    ResponseEntity<ProblemDetail> handlePaymentState(PaymentStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(HttpStatus.CONFLICT, ex.getMessage()));
    }

    /** 402: autorización rechazada o con verificación adicional; sin detalles internos de la pasarela. */
    @ExceptionHandler(PaymentDeclinedException.class)
    ResponseEntity<ProblemDetail> handlePaymentDeclined(PaymentDeclinedException ex) {
        ProblemDetail problem = problem(HttpStatus.PAYMENT_REQUIRED, ex.getMessage());
        problem.setProperty("code", ex.getCode());
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(problem);
    }

    @ExceptionHandler(PaymentUnavailableException.class)
    ResponseEntity<ProblemDetail> handlePaymentUnavailable(PaymentUnavailableException ex) {
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setProperty("code", PaymentUnavailableException.CODE);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    /** 429 con Retry-After: el cliente superó su límite de mensajes al asistente (spec 013). */
    @ExceptionHandler(AssistantRateLimitedException.class)
    ResponseEntity<ProblemDetail> handleAssistantRateLimited(AssistantRateLimitedException ex) {
        ProblemDetail problem = problem(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problem.setProperty("code", AssistantRateLimitedException.CODE);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(problem);
    }

    /** 503: la pasarela no reembolsó; el pedido conserva su estado y se puede reintentar (spec 014). */
    @ExceptionHandler(RefundFailedException.class)
    ResponseEntity<ProblemDetail> handleRefundFailed(RefundFailedException ex) {
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setProperty("code", RefundFailedException.CODE);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    /** 429 con Retry-After: demasiados intentos fallidos de inicio de sesión. */
    @ExceptionHandler(LoginRateLimitedException.class)
    ResponseEntity<ProblemDetail> handleLoginRateLimited(LoginRateLimitedException ex) {
        ProblemDetail problem = problem(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problem.setProperty("code", LoginRateLimitedException.CODE);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(problem);
    }

    @ExceptionHandler(AssistantUnavailableException.class)
    ResponseEntity<ProblemDetail> handleAssistantUnavailable(AssistantUnavailableException ex) {
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setProperty("code", AssistantUnavailableException.CODE);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    @ExceptionHandler(AssistantBusyException.class)
    ResponseEntity<ProblemDetail> handleAssistantBusy(AssistantBusyException ex) {
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setProperty("code", AssistantBusyException.CODE);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    @ExceptionHandler(AssistantMessageInvalidException.class)
    ResponseEntity<ProblemDetail> handleAssistantMessageInvalid(AssistantMessageInvalidException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, INVALID_FORM);
        problem.setProperty("errors", Map.of("messages", ex.getMessage()));
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(problem(HttpStatus.CONFLICT, ProductConflictException.STALE));
    }

    @ExceptionHandler(ImageUnavailableException.class)
    ResponseEntity<ProblemDetail> handleImageUnavailable(ImageUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage()));
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

    @ExceptionHandler(RestaurantNotEnabledException.class)
    ResponseEntity<ProblemDetail> handleRestaurantNotEnabled(RestaurantNotEnabledException ex) {
        ProblemDetail problem = problem(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setProperty("code", RestaurantNotEnabledException.CODE);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
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
