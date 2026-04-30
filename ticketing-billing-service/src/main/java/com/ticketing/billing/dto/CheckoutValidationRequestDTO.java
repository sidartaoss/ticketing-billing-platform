package com.ticketing.billing.dto;

public record CheckoutValidationRequestDTO(
        String cavv,
        String xid,
        String eci
) {
}
