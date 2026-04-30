package com.ticketing.billing.controller;

import com.ticketing.billing.domain.Cobranca;
import com.ticketing.billing.lock.InMemoryLockService;
import com.ticketing.billing.lock.LockService;
import com.ticketing.billing.repository.CobrancaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CobrancaIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public LockService inMemoryLockService() {
            return new InMemoryLockService();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CobrancaRepository cobrancaRepository;

    @Test
    void criarCobranca_pix_retorna201ComDados() throws Exception {
        String requestBody = """
                {
                    "valor": 25.50,
                    "tipo": "RECARGA",
                    "metodo": "PIX"
                }
                """;

        mockMvc.perform(post("/api/v1/cobrancas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.txid").isNotEmpty())
                .andExpect(jsonPath("$.copiaECola").isNotEmpty())
                .andExpect(jsonPath("$.dataExpiracao").isNotEmpty());
    }

    @Test
    void criarCobranca_pix_persisteNoBanco() throws Exception {
        cobrancaRepository.deleteAll();

        String requestBody = """
                {
                    "valor": 100.00,
                    "tipo": "RECARGA",
                    "metodo": "PIX"
                }
                """;

        mockMvc.perform(post("/api/v1/cobrancas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        List<Cobranca> cobrancas = cobrancaRepository.findAll();
        assertEquals(1, cobrancas.size());

        Cobranca cobranca = cobrancas.getFirst();
        assertEquals("user-001", cobranca.getIdUsuario());
        assertEquals("Joao Silva", cobranca.getNomeSolicitante());
        assertNotNull(cobranca.getTxid());
        assertNotNull(cobranca.getCopiaECola());
        assertNotNull(cobranca.getDataExpiracao());
    }
}
