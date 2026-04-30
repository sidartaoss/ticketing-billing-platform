package com.ticketing.billing.integration;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CheckoutValidationClient {

    public Map<String, String> validarCheckout(String transactionId, String cavv, String xid, String eci) {
        return Map.of(
                "authorizationCode", UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
                "status", "APPROVED"
        );
    }
}
