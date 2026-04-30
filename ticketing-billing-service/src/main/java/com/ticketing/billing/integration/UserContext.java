package com.ticketing.billing.integration;

import org.springframework.stereotype.Component;

@Component
public class UserContext {

    public String getIdUsuario() {
        return "user-001";
    }

    public String getGivenName() {
        return "Joao";
    }

    public String getFamilyName() {
        return "Silva";
    }

    public String getCpf() {
        return "12345678900";
    }
}
