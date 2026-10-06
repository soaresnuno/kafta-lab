package com.nuno.kafkalab.authservice.exceptions;

// A mesma mensagem para email inexistente e password errada, para não revelar que emails existem
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
