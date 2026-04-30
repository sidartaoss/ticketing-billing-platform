package com.ticketing.billing.dto;

import com.ticketing.billing.domain.CobrancaMetodoEnum;
import com.ticketing.billing.domain.CobrancaTipoEnum;
import java.math.BigDecimal;

public record CobrancaRequestDTO(
        BigDecimal valor,
        CobrancaTipoEnum tipo,
        CobrancaMetodoEnum metodo
) {
}
