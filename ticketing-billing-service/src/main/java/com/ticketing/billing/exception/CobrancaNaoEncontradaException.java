package com.ticketing.billing.exception;

public class CobrancaNaoEncontradaException extends RuntimeException {

    public CobrancaNaoEncontradaException(Long id) {
        super("Cobranca nao encontrada: " + id);
    }

    public CobrancaNaoEncontradaException(String transactionId) {
        super("Cobranca nao encontrada para transactionId: " + transactionId);
    }
}
