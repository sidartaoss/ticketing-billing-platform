package com.ticketing.billing.controller;

import com.ticketing.billing.dto.CobrancaBasicoResponseDTO;
import com.ticketing.billing.dto.CobrancaRequestDTO;
import com.ticketing.billing.service.CobrancaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cobrancas")
public class CobrancaController {

    private final CobrancaService cobrancaService;

    public CobrancaController(CobrancaService cobrancaService) {
        this.cobrancaService = cobrancaService;
    }

    @PostMapping
    public ResponseEntity<CobrancaBasicoResponseDTO> criarCobranca(@RequestBody CobrancaRequestDTO request) {
        CobrancaBasicoResponseDTO response = cobrancaService.criarCobranca(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
