package com.ticketing.billing.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CobrancaEmAndamentoException.class)
    public ResponseEntity<Map<String, String>> handleCobrancaEmAndamento(CobrancaEmAndamentoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("erro", ex.getMessage()));
    }

    @ExceptionHandler(CobrancaCriacaoException.class)
    public ResponseEntity<Map<String, String>> handleCobrancaCriacao(CobrancaCriacaoException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("erro", ex.getMessage()));
    }

    @ExceptionHandler(CobrancaNaoEncontradaException.class)
    public ResponseEntity<Map<String, String>> handleCobrancaNaoEncontrada(CobrancaNaoEncontradaException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("erro", ex.getMessage()));
    }
}
