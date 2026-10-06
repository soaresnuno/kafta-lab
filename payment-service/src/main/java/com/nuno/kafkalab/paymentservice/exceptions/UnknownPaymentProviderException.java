package com.nuno.kafkalab.paymentservice.exceptions;

public class UnknownPaymentProviderException extends RuntimeException {
    public UnknownPaymentProviderException(String provider) {
        super("Unknown payment provider " + provider);
    }
}
