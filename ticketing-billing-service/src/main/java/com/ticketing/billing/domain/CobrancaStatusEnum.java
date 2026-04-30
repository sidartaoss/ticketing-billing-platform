package com.ticketing.billing.domain;

public enum CobrancaStatusEnum {

    SOLICITADA(2),
    EXPIRADA(3),
    ERRO_APROVACAO_PEDIDO(4),
    FINALIZADA(5),
    EM_REPROCESSAMENTO(6),
    ERRO_ANALISE_PENDENTE(9);

    private final int code;

    CobrancaStatusEnum(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
