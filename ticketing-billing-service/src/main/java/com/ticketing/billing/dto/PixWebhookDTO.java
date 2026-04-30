package com.ticketing.billing.dto;

import java.math.BigDecimal;
import java.util.List;

public record PixWebhookDTO(List<PixItemDTO> pix) {

    public record PixItemDTO(String txid, String horario, BigDecimal valor) {
    }
}
