package com.nuno.kafkalab.authservice.exceptions;

public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException(String email) {
        super("Email " + email + " is already registered");
    }
}
