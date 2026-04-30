package com.ticketing.billing.exception;

public class CobrancaEmAndamentoException extends RuntimeException {

    public CobrancaEmAndamentoException() {
        super("Geracao de cobranca em andamento.");
    }
}
