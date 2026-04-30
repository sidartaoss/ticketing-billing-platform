package com.ticketing.billing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CobrancaCompletoResponseDTO(
        Long id,
        String txid,
        String idUsuario,
        String tipo,
        String metodo,
        String status,
        BigDecimal valorSolicitado,
        BigDecimal valorPago,
        LocalDateTime dataCriacao,
        LocalDateTime dataExpiracao,
        LocalDateTime dataFinalizada
) {
}
