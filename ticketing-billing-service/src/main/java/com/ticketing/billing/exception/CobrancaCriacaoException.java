package com.ticketing.billing.exception;

public class CobrancaCriacaoException extends RuntimeException {

    public CobrancaCriacaoException(Throwable cause) {
        super("Erro ao criar cobranca.", cause);
    }
}
