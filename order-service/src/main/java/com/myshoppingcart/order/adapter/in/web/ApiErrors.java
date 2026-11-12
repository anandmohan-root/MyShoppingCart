package com.myshoppingcart.order.adapter.in.web;

import com.myshoppingcart.order.application.ConcurrentOrderChangeException;
import com.myshoppingcart.order.application.EmptyCartException;
import com.myshoppingcart.order.application.IdempotencyKeyReusedException;
import com.myshoppingcart.order.application.UnknownProductException;
import com.myshoppingcart.order.domain.OrderNotCancellableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

/** HTTP statuses for the order and cart APIs. */
@RestControllerAdvice
class ApiErrors {

    @ExceptionHandler({OrderNotCancellableException.class, ConcurrentOrderChangeException.class})
    ProblemDetail conflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler({UnknownProductException.class, EmptyCartException.class, IllegalArgumentException.class})
    ProblemDetail badRequest(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail keyReused(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    /** Catalog is down, slow, or its circuit breaker is open. */
    @ExceptionHandler({RestClientException.class, CallNotPermittedException.class})
    ProblemDetail catalogUnavailable(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "catalog is unavailable, try again later");
    }
}
