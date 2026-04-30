package com.ticketing.billing.service.strategy;

import com.ticketing.billing.domain.Cobranca;
import com.ticketing.billing.domain.CobrancaMetodoEnum;
import com.ticketing.billing.integration.PagamentoGatewayClient;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CartaoCreditoCriacaoStrategy implements CobrancaCriacaoStrategy {

    private final PagamentoGatewayClient pagamentoGatewayClient;

    public CartaoCreditoCriacaoStrategy(PagamentoGatewayClient pagamentoGatewayClient) {
        this.pagamentoGatewayClient = pagamentoGatewayClient;
    }

    @Override
    public CobrancaMetodoEnum getMetodo() {
        return CobrancaMetodoEnum.CARTAO_CREDITO;
    }

    @Override
    public void aplicar(Cobranca cobranca) {
        Map<String, String> cartaoData = pagamentoGatewayClient.criarTransacaoCartao(
                cobranca.getIdUsuario(), cobranca.getValorSolicitacao());

        cobranca.setTransactionId(cartaoData.get("transactionId"));
    }
}
