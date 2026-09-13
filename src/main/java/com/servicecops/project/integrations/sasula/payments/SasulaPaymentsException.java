package com.servicecops.project.integrations.sasula.payments;

public class SasulaPaymentsException extends RuntimeException {
    public SasulaPaymentsException(String message) {
        super(message);
    }

    public SasulaPaymentsException(String message, Throwable cause) {
        super(message, cause);
    }
}
