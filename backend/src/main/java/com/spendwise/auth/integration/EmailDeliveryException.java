package com.spendwise.auth.integration;

public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException() {
        super("Email delivery failed.");
    }
}
