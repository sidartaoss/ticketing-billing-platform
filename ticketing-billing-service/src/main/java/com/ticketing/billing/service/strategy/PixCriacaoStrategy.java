package com.ticketing.billing.service.strategy;

import com.ticketing.billing.domain.Cobranca;
import com.ticketing.billing.domain.CobrancaMetodoEnum;
import com.ticketing.billing.integration.PagamentoGatewayClient;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PixCriacaoStrategy implements CobrancaCriacaoStrategy {

    private static final long EXPIRACAO_MINUTOS = 30;

    private final PagamentoGatewayClient pagamentoGatewayClient;

    public PixCriacaoStrategy(PagamentoGatewayClient pagamentoGatewayClient) {
        this.pagamentoGatewayClient = pagamentoGatewayClient;
    }

    @Override
    public CobrancaMetodoEnum getMetodo() {
        return CobrancaMetodoEnum.PIX;
    }

    @Override
    public void aplicar(Cobranca cobranca) {
        Map<String, String> pixData = pagamentoGatewayClient.gerarPix(
                cobranca.getIdUsuario(), cobranca.getValorSolicitacao());

        cobranca.setTxid(pixData.get("txid"));
        cobranca.setCopiaECola(pixData.get("copiaECola"));
        cobranca.setDataExpiracao(cobranca.getDataCriacao().plusMinutes(EXPIRACAO_MINUTOS));
    }
}
