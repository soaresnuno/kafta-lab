package com.nuno.kafkalab.productservice.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleProductNotFound(ProductNotFoundException ex) {
        // ProblemDetail = formato standard para erros HTTP (RFC 9457).
        // O status que pões aqui é o status da resposta.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Product not found");
        return problem;
    }

    @ExceptionHandler(StoreNotFoundException.class)
    public ProblemDetail handleStoreNotFound(StoreNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Store not found");
        return problem;
    }

    @ExceptionHandler(StoreInactiveException.class)
    public ProblemDetail handleStoreInactive(StoreInactiveException ex) {
        // 409 Conflict: o pedido é válido, mas a loja já não pode receber produtos novos
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Store inactive");
        return problem;
    }

    @ExceptionHandler(StoreAccessDeniedException.class)
    public ProblemDetail handleStoreAccessDenied(StoreAccessDeniedException ex) {
        // 403 Forbidden: sabemos quem és (token válido), mas não podes gerir os produtos desta loja
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Access denied");
        return problem;
    }
}
