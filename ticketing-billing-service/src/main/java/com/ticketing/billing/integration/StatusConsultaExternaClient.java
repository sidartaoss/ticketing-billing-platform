package com.ticketing.billing.integration;

import com.ticketing.billing.domain.CobrancaStatusEnum;
import org.springframework.stereotype.Component;

@Component
public class StatusConsultaExternaClient {

    public CobrancaStatusEnum consultarStatus(String txid) {
        // Mock: retorna null indicando que nao houve mudanca de status
        return null;
    }
}
