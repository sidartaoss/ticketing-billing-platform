package com.ticketing.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CobrancaCriadaEvent(
        Long id,
        String idUsuario,
        String tipo,
        String metodo,
        String status,
        BigDecimal valor,
        LocalDateTime dataCriacao
) {
}
