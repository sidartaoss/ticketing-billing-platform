package com.ticketing.billing.dto;

import java.time.LocalDateTime;

public record CobrancaBasicoResponseDTO(
        Long id,
        String txid,
        String copiaECola,
        LocalDateTime dataExpiracao,
        String transactionId,
        String acsUrl,
        String threeDsPayload
) {
}
