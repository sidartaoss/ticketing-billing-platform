package com.ticketing.billing.integration;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PagamentoGatewayClient {

    public Map<String, String> gerarPix(String cpf, java.math.BigDecimal valor) {
        return Map.of(
                "txid", UUID.randomUUID().toString().replace("-", "").substring(0, 26),
                "copiaECola", "00020126580014br.gov.bcb.pix0136" + UUID.randomUUID()
        );
    }

    public Map<String, String> criarTransacaoCartao(String cpf, java.math.BigDecimal valor) {
        return Map.of(
                "transactionId", UUID.randomUUID().toString()
        );
    }
}
