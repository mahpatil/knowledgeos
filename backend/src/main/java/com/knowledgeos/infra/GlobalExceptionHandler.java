package com.knowledgeos.infra;

import com.knowledgeos.dto.ErrorResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Produces
public class GlobalExceptionHandler
    implements ExceptionHandler<Exception, HttpResponse<ErrorResponse>> {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    public HttpResponse<ErrorResponse> handle(HttpRequest request, Exception e) {
        if (e instanceof HttpStatusException hse) {
            return HttpResponse
                .status(hse.getStatus())
                .body(new ErrorResponse(hse.getMessage(), hse.getStatus().name(), null));
        }

        if (e instanceof ConstraintViolationException cve) {
            String msg = cve.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation error");
            return HttpResponse
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(msg, "VALIDATION_ERROR", null));
        }

        log.error("Unhandled exception: {}", e.getMessage(), e);
        return HttpResponse
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("Internal server error", "INTERNAL_ERROR", null));
    }
}
