package com.futbolix.courts.domain;

/**
 * Amount in whole Colombian pesos, the only currency of the MVP (_shared.yaml, schema Money).
 */
public record Money(long amount, String currency) {

    public static final String COP = "COP";

    public Money {
        if (!COP.equals(currency)) {
            throw new InvalidCourtException("hourlyPrice", "The currency must be COP");
        }
        if (amount <= 0) {
            throw new InvalidCourtException("hourlyPrice", "The price must be greater than 0");
        }
    }

    public static Money cop(long amount) {
        return new Money(amount, COP);
    }
}
