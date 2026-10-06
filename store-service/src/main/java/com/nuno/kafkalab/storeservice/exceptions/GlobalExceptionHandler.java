package com.nuno.kafkalab.storeservice.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(StoreNotFoundException.class)
    public ProblemDetail handleStoreNotFound(StoreNotFoundException ex) {
        // ProblemDetail = formato standard para erros HTTP (RFC 9457).
        // O status que pões aqui é o status da resposta.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Store not found");
        return problem;
    }

    @ExceptionHandler(StoreInactiveException.class)
    public ProblemDetail handleStoreInactive(StoreInactiveException ex) {
        // 409 Conflict: o pedido é válido, mas não faz sentido no estado atual da loja
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Store inactive");
        return problem;
    }
}
