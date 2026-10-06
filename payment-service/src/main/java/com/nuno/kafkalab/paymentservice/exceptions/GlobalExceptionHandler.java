package com.nuno.kafkalab.paymentservice.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ProblemDetail handlePaymentNotFound(PaymentNotFoundException ex) {
        // ProblemDetail = formato standard para erros HTTP (RFC 9457).
        // O status que pões aqui é o status da resposta.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Payment not found");
        return problem;
    }

    @ExceptionHandler(InvalidWebhookException.class)
    public ProblemDetail handleInvalidWebhook(InvalidWebhookException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid webhook");
        return problem;
    }

    @ExceptionHandler(UnknownPaymentProviderException.class)
    public ProblemDetail handleUnknownProvider(UnknownPaymentProviderException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Unknown payment provider");
        return problem;
    }
}
