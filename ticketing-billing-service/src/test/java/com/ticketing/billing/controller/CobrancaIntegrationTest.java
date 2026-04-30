package com.ticketing.billing.controller;

import com.ticketing.billing.domain.Cobranca;
import com.ticketing.billing.repository.CobrancaRepository;
import com.ticketing.billing.service.CobrancaEventPublisher;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CobrancaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CobrancaRepository cobrancaRepository;

    @MockitoBean
    private CobrancaEventPublisher cobrancaEventPublisher;

    @Nested
    class CriarCobrancaTest {

        @Test
        void dadoRequestPixValido_quandoCriarCobranca_entaoRetorna201ComDados() throws Exception {
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
        void dadoRequestPixValido_quandoCriarCobranca_entaoPersiteNoBanco() throws Exception {
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

    @Nested
    class ConsultarCobrancaTest {

        @Test
        void dadoCobrancaCriada_quandoConsultarPorId_entaoRetorna200ComPayloadCompleto() throws Exception {
            cobrancaRepository.deleteAll();

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
                    .andExpect(status().isCreated());

            Long id = cobrancaRepository.findAll().getFirst().getId();

            mockMvc.perform(get("/api/v1/cobrancas/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.idUsuario").value("user-001"))
                    .andExpect(jsonPath("$.tipo").value("RECARGA"))
                    .andExpect(jsonPath("$.metodo").value("PIX"))
                    .andExpect(jsonPath("$.status").value("SOLICITADA"))
                    .andExpect(jsonPath("$.valorSolicitado").value(25.50))
                    .andExpect(jsonPath("$.dataCriacao").isNotEmpty())
                    .andExpect(jsonPath("$.dataExpiracao").isNotEmpty());
        }

        @Test
        void dadoIdInexistente_quandoConsultarPorId_entaoRetorna404() throws Exception {
            mockMvc.perform(get("/api/v1/cobrancas/{id}", 99999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.erro").value("Cobranca nao encontrada: 99999"));
        }
    }

    @Nested
    class ValidarCheckoutTest {

        @Test
        void dadoCobrancaCartaoCriada_quandoValidarCheckout_entaoRetorna200ComCobrancaAtualizada() throws Exception {
            cobrancaRepository.deleteAll();

            // Arrange - cria cobranca CARTAO_CREDITO
            String criarRequestBody = """
                    {
                        "valor": 50.00,
                        "tipo": "RECARGA",
                        "metodo": "CARTAO_CREDITO"
                    }
                    """;

            mockMvc.perform(post("/api/v1/cobrancas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(criarRequestBody))
                    .andExpect(status().isCreated());

            Cobranca cobrancaCriada = cobrancaRepository.findAll().getFirst();
            String transactionId = cobrancaCriada.getTransactionId();

            // Act - valida checkout
            String validateBody = """
                    {
                        "cavv": "AAABBB",
                        "xid": "XYZ",
                        "eci": "05"
                    }
                    """;

            mockMvc.perform(post("/api/v1/cobrancas/{transactionId}/validate", transactionId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validateBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FINALIZADA"))
                    .andExpect(jsonPath("$.valorSolicitado").value(50.00))
                    .andExpect(jsonPath("$.valorPago").value(50.00))
                    .andExpect(jsonPath("$.dataFinalizada").isNotEmpty())
                    .andExpect(jsonPath("$.metodo").value("CARTAO_CREDITO"));
        }

        @Test
        void dadoTransactionIdInexistente_quandoValidarCheckout_entaoRetorna404() throws Exception {
            String validateBody = """
                    {
                        "cavv": "AAABBB",
                        "xid": "XYZ",
                        "eci": "05"
                    }
                    """;

            mockMvc.perform(post("/api/v1/cobrancas/{transactionId}/validate", "txn-inexistente")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validateBody))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.erro").value("Cobranca nao encontrada para transactionId: txn-inexistente"));
        }
    }

    @Nested
    class ProcessarWebhookPixTest {

        @Test
        void dadoCobrancaPixCriada_quandoReceberWebhookPix_entaoFinalizaCobranca() throws Exception {
            cobrancaRepository.deleteAll();

            // Arrange - cria cobranca PIX
            String criarRequestBody = """
                    {
                        "valor": 25.50,
                        "tipo": "RECARGA",
                        "metodo": "PIX"
                    }
                    """;

            mockMvc.perform(post("/api/v1/cobrancas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(criarRequestBody))
                    .andExpect(status().isCreated());

            Cobranca cobrancaCriada = cobrancaRepository.findAll().getFirst();
            String txid = cobrancaCriada.getTxid();
            Long idOriginal = cobrancaCriada.getId();

            // Act - envia webhook PIX
            String webhookBody = """
                    {
                        "pix": [
                            {
                                "txid": "%s",
                                "horario": "2026-04-15T13:02:30Z",
                                "valor": 25.50
                            }
                        ]
                    }
                    """.formatted(txid);

            mockMvc.perform(post("/api/v1/cobrancas/webhook/pix")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(webhookBody))
                    .andExpect(status().isOk());

            // Assert - consulta e verifica que foi finalizada
            mockMvc.perform(get("/api/v1/cobrancas/{id}", idOriginal))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FINALIZADA"))
                    .andExpect(jsonPath("$.valorPago").value(25.50))
                    .andExpect(jsonPath("$.dataFinalizada").isNotEmpty());
        }

        @Test
        void dadoPayloadVazio_quandoReceberWebhookPix_entaoRetorna200() throws Exception {
            String webhookBody = """
                    {
                        "pix": []
                    }
                    """;

            mockMvc.perform(post("/api/v1/cobrancas/webhook/pix")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(webhookBody))
                    .andExpect(status().isOk());
        }
    }
}
