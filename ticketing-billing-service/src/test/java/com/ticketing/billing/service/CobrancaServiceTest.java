package com.ticketing.billing.service;

import com.ticketing.billing.domain.Cobranca;
import com.ticketing.billing.domain.CobrancaMetodoEnum;
import com.ticketing.billing.domain.CobrancaStatusEnum;
import com.ticketing.billing.domain.CobrancaTipoEnum;
import com.ticketing.billing.dto.CheckoutValidationRequestDTO;
import com.ticketing.billing.dto.CobrancaBasicoResponseDTO;
import com.ticketing.billing.dto.CobrancaCompletoResponseDTO;
import com.ticketing.billing.dto.CobrancaRequestDTO;
import com.ticketing.billing.dto.PixWebhookDTO;
import com.ticketing.billing.exception.CobrancaCriacaoException;
import com.ticketing.billing.exception.CobrancaEmAndamentoException;
import com.ticketing.billing.exception.CobrancaNaoEncontradaException;
import com.ticketing.billing.integration.CheckoutValidationClient;
import com.ticketing.billing.integration.StatusConsultaExternaClient;
import com.ticketing.billing.integration.UserContext;
import com.ticketing.billing.lock.LockExecutor;
import com.ticketing.billing.repository.CobrancaRepository;
import com.ticketing.billing.service.strategy.CobrancaCriacaoStrategy;
import com.ticketing.billing.service.strategy.CobrancaCriacaoStrategyRegistry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CobrancaServiceTest {

    @Mock
    private CobrancaRepository cobrancaRepository;

    @Mock
    private LockExecutor lockExecutor;

    @Mock
    private UserContext userContext;

    @Mock
    private CobrancaCriacaoStrategyRegistry strategyRegistry;

    @Mock
    private StatusConsultaExternaClient statusConsultaExternaClient;

    @Mock
    private CheckoutValidationClient checkoutValidationClient;

    @Mock
    private CobrancaEventPublisher cobrancaEventPublisher;

    @Mock
    private CobrancaCriacaoStrategy pixStrategy;

    @InjectMocks
    private CobrancaService cobrancaService;

    @Nested
    class CriarCobrancaTest {

        @Test
        void dadoRequestPixValido_quandoCriarCobranca_entaoRetornaResponseComIdEPublicaEvento() {
            // Arrange
            CobrancaRequestDTO request = new CobrancaRequestDTO(
                    new BigDecimal("25.50"), CobrancaTipoEnum.RECARGA, CobrancaMetodoEnum.PIX);

            when(userContext.getIdUsuario()).thenReturn("user-001");
            when(userContext.getGivenName()).thenReturn("Joao");
            when(userContext.getFamilyName()).thenReturn("Silva");

            when(lockExecutor.executeWithLock(anyString(), anyLong(), any()))
                    .thenAnswer(invocation -> {
                        Supplier<?> action = invocation.getArgument(2);
                        return action.get();
                    });

            when(strategyRegistry.getStrategy(CobrancaMetodoEnum.PIX)).thenReturn(pixStrategy);

            when(cobrancaRepository.save(any(Cobranca.class))).thenAnswer(invocation -> {
                Cobranca c = invocation.getArgument(0);
                c.setId(1L);
                return c;
            });

            // Act
            CobrancaBasicoResponseDTO response = cobrancaService.criarCobranca(request);

            // Assert
            assertNotNull(response);
            assertEquals(1L, response.id());
            verify(lockExecutor).executeWithLock(eq("cobrancas:user-001"), eq(5L), any());
            verify(pixStrategy).aplicar(any(Cobranca.class));
            verify(cobrancaRepository).save(argThat(cobranca -> {
                assertEquals(CobrancaStatusEnum.SOLICITADA, cobranca.getStatus());
                assertEquals("Joao Silva", cobranca.getNomeSolicitante());
                assertEquals(new BigDecimal("25.50"), cobranca.getValorSolicitacao());
                assertEquals(CobrancaMetodoEnum.PIX, cobranca.getMetodo());
                assertEquals(CobrancaTipoEnum.RECARGA, cobranca.getTipo());
                return true;
            }));
            verify(cobrancaEventPublisher).publicarCobrancaCriada(argThat(cobranca ->
                    cobranca.getId().equals(1L)));
        }

        @Test
        void dadoLockIndisponivel_quandoCriarCobranca_entaoLancaCobrancaEmAndamentoException() {
            // Arrange
            CobrancaRequestDTO request = new CobrancaRequestDTO(
                    new BigDecimal("25.50"), CobrancaTipoEnum.RECARGA, CobrancaMetodoEnum.PIX);

            when(userContext.getIdUsuario()).thenReturn("user-001");

            when(lockExecutor.executeWithLock(anyString(), anyLong(), any()))
                    .thenThrow(new CobrancaEmAndamentoException());

            // Act & Assert
            assertThrows(CobrancaEmAndamentoException.class,
                    () -> cobrancaService.criarCobranca(request));

            verify(cobrancaRepository, never()).save(any());
            verify(cobrancaEventPublisher, never()).publicarCobrancaCriada(any());
        }

        @Test
        void dadoErroInesperado_quandoCriarCobranca_entaoLancaCobrancaCriacaoException() {
            // Arrange
            CobrancaRequestDTO request = new CobrancaRequestDTO(
                    new BigDecimal("25.50"), CobrancaTipoEnum.RECARGA, CobrancaMetodoEnum.PIX);

            when(userContext.getIdUsuario()).thenReturn("user-001");
            when(userContext.getGivenName()).thenReturn("Joao");
            when(userContext.getFamilyName()).thenReturn("Silva");

            when(lockExecutor.executeWithLock(anyString(), anyLong(), any()))
                    .thenAnswer(invocation -> {
                        Supplier<?> action = invocation.getArgument(2);
                        return action.get();
                    });

            when(strategyRegistry.getStrategy(CobrancaMetodoEnum.PIX))
                    .thenThrow(new RuntimeException("Erro inesperado"));

            // Act & Assert
            CobrancaCriacaoException exception = assertThrows(CobrancaCriacaoException.class,
                    () -> cobrancaService.criarCobranca(request));

            assertEquals("Erro ao criar cobranca.", exception.getMessage());
            verify(cobrancaRepository, never()).save(any());
            verify(cobrancaEventPublisher, never()).publicarCobrancaCriada(any());
        }
    }

    @Nested
    class ConsultarCobrancaPorIdTest {

        @Test
        void dadoCobrancaExistente_quandoConsultarPorId_entaoRetornaCobrancaCompleta() {
            // Arrange
            Cobranca cobranca = buildCobrancaBase();
            cobranca.setId(1L);
            cobranca.setStatus(CobrancaStatusEnum.FINALIZADA);

            when(cobrancaRepository.findById(1L)).thenReturn(Optional.of(cobranca));
            when(cobrancaRepository.findTopByCobrancaPaiIdOrderByIdDesc(1L)).thenReturn(Optional.empty());

            // Act
            CobrancaCompletoResponseDTO response = cobrancaService.consultarCobrancaPorId(1L);

            // Assert
            assertNotNull(response);
            assertEquals(1L, response.id());
            assertEquals("user-001", response.idUsuario());
            assertEquals("RECARGA", response.tipo());
            assertEquals("PIX", response.metodo());
            assertEquals("FINALIZADA", response.status());
            assertEquals(new BigDecimal("25.50"), response.valorSolicitado());
            assertNotNull(response.dataCriacao());
            verify(statusConsultaExternaClient, never()).consultarStatus(any());
        }

        @Test
        void dadoCobrancaInexistente_quandoConsultarPorId_entaoLancaCobrancaNaoEncontradaException() {
            // Arrange
            when(cobrancaRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            CobrancaNaoEncontradaException exception = assertThrows(
                    CobrancaNaoEncontradaException.class,
                    () -> cobrancaService.consultarCobrancaPorId(99L));

            assertEquals("Cobranca nao encontrada: 99", exception.getMessage());
        }

        @Test
        void dadoCobrancaPixComStatusSolicitada_quandoStatusExternoMudou_entaoPersisteNovaVersao() {
            // Arrange
            Cobranca cobranca = buildCobrancaBase();
            cobranca.setId(1L);
            cobranca.setStatus(CobrancaStatusEnum.SOLICITADA);
            cobranca.setTxid("txid-123");

            when(cobrancaRepository.findById(1L)).thenReturn(Optional.of(cobranca));
            when(cobrancaRepository.findTopByCobrancaPaiIdOrderByIdDesc(1L)).thenReturn(Optional.empty());
            when(statusConsultaExternaClient.consultarStatus("txid-123")).thenReturn(CobrancaStatusEnum.FINALIZADA);
            when(cobrancaRepository.save(any(Cobranca.class))).thenAnswer(invocation -> {
                Cobranca c = invocation.getArgument(0);
                c.setId(2L);
                return c;
            });

            // Act
            CobrancaCompletoResponseDTO response = cobrancaService.consultarCobrancaPorId(1L);

            // Assert
            assertEquals(2L, response.id());
            assertEquals("FINALIZADA", response.status());
            verify(cobrancaRepository).save(argThat(novaVersao -> {
                assertEquals(1L, novaVersao.getCobrancaPaiId());
                assertEquals(CobrancaStatusEnum.FINALIZADA, novaVersao.getStatus());
                assertEquals("txid-123", novaVersao.getTxid());
                return true;
            }));
        }

        @Test
        void dadoCobrancaPixComStatusSolicitada_quandoStatusExternoNaoMudou_entaoRetornaCobrancaAtual() {
            // Arrange
            Cobranca cobranca = buildCobrancaBase();
            cobranca.setId(1L);
            cobranca.setStatus(CobrancaStatusEnum.SOLICITADA);
            cobranca.setTxid("txid-123");

            when(cobrancaRepository.findById(1L)).thenReturn(Optional.of(cobranca));
            when(cobrancaRepository.findTopByCobrancaPaiIdOrderByIdDesc(1L)).thenReturn(Optional.empty());
            when(statusConsultaExternaClient.consultarStatus("txid-123")).thenReturn(null);

            // Act
            CobrancaCompletoResponseDTO response = cobrancaService.consultarCobrancaPorId(1L);

            // Assert
            assertEquals(1L, response.id());
            assertEquals("SOLICITADA", response.status());
            verify(cobrancaRepository, never()).save(any());
        }

        @Test
        void dadoCobrancaComVersaoFilha_quandoConsultarPorId_entaoRetornaVersaoMaisRecente() {
            // Arrange
            Cobranca original = buildCobrancaBase();
            original.setId(1L);
            original.setStatus(CobrancaStatusEnum.SOLICITADA);

            Cobranca versaoFilha = buildCobrancaBase();
            versaoFilha.setId(3L);
            versaoFilha.setStatus(CobrancaStatusEnum.FINALIZADA);
            versaoFilha.setCobrancaPaiId(1L);

            when(cobrancaRepository.findById(1L)).thenReturn(Optional.of(original));
            when(cobrancaRepository.findTopByCobrancaPaiIdOrderByIdDesc(1L)).thenReturn(Optional.of(versaoFilha));

            // Act
            CobrancaCompletoResponseDTO response = cobrancaService.consultarCobrancaPorId(1L);

            // Assert
            assertEquals(3L, response.id());
            assertEquals("FINALIZADA", response.status());
        }

        @Test
        void dadoCobrancaCartaoCredito_quandoConsultarPorId_entaoNaoConsultaStatusExterno() {
            // Arrange
            Cobranca cobranca = buildCobrancaBase();
            cobranca.setId(1L);
            cobranca.setMetodo(CobrancaMetodoEnum.CARTAO_CREDITO);
            cobranca.setStatus(CobrancaStatusEnum.SOLICITADA);

            when(cobrancaRepository.findById(1L)).thenReturn(Optional.of(cobranca));
            when(cobrancaRepository.findTopByCobrancaPaiIdOrderByIdDesc(1L)).thenReturn(Optional.empty());

            // Act
            CobrancaCompletoResponseDTO response = cobrancaService.consultarCobrancaPorId(1L);

            // Assert
            assertEquals("CARTAO_CREDITO", response.metodo());
            verify(statusConsultaExternaClient, never()).consultarStatus(any());
        }

        @Test
        void dadoCobrancaPixComStatusFinalizada_quandoConsultarPorId_entaoNaoConsultaStatusExterno() {
            // Arrange
            Cobranca cobranca = buildCobrancaBase();
            cobranca.setId(1L);
            cobranca.setStatus(CobrancaStatusEnum.FINALIZADA);
            cobranca.setTxid("txid-123");

            when(cobrancaRepository.findById(1L)).thenReturn(Optional.of(cobranca));
            when(cobrancaRepository.findTopByCobrancaPaiIdOrderByIdDesc(1L)).thenReturn(Optional.empty());

            // Act
            CobrancaCompletoResponseDTO response = cobrancaService.consultarCobrancaPorId(1L);

            // Assert
            assertEquals("FINALIZADA", response.status());
            verify(statusConsultaExternaClient, never()).consultarStatus(any());
        }
    }

    @Nested
    class ProcessarNotificacaoWebhookPixTest {

        @Test
        void dadoCobrancaPendente_quandoReceberWebhookPix_entaoFinalizaCobranca() {
            // Arrange
            Cobranca cobranca = buildCobrancaBase();
            cobranca.setId(10L);
            cobranca.setStatus(CobrancaStatusEnum.SOLICITADA);
            cobranca.setTxid("txid-abc");

            when(cobrancaRepository.findTopByTxidOrderByIdDesc("txid-abc"))
                    .thenReturn(Optional.of(cobranca));
            when(cobrancaRepository.save(any(Cobranca.class))).thenAnswer(invocation -> {
                Cobranca c = invocation.getArgument(0);
                c.setId(11L);
                return c;
            });

            PixWebhookDTO dto = new PixWebhookDTO(List.of(
                    new PixWebhookDTO.PixItemDTO("txid-abc", "2026-04-15T13:02:30Z", new BigDecimal("25.50"))
            ));

            // Act
            cobrancaService.processarNotificacaoWebhookPix(dto);

            // Assert
            verify(cobrancaRepository).save(argThat(novaVersao -> {
                assertEquals(CobrancaStatusEnum.FINALIZADA, novaVersao.getStatus());
                assertEquals(new BigDecimal("25.50"), novaVersao.getValorPago());
                assertNotNull(novaVersao.getDataFinalizada());
                assertEquals(10L, novaVersao.getCobrancaPaiId());
                assertEquals("txid-abc", novaVersao.getTxid());
                assertEquals("user-001", novaVersao.getIdUsuario());
                return true;
            }));
        }

        @Test
        void dadoCobrancaJaFinalizada_quandoReceberWebhookPix_entaoIgnora() {
            // Arrange
            Cobranca cobranca = buildCobrancaBase();
            cobranca.setId(10L);
            cobranca.setStatus(CobrancaStatusEnum.FINALIZADA);
            cobranca.setTxid("txid-abc");

            when(cobrancaRepository.findTopByTxidOrderByIdDesc("txid-abc"))
                    .thenReturn(Optional.of(cobranca));

            PixWebhookDTO dto = new PixWebhookDTO(List.of(
                    new PixWebhookDTO.PixItemDTO("txid-abc", "2026-04-15T13:02:30Z", new BigDecimal("25.50"))
            ));

            // Act
            cobrancaService.processarNotificacaoWebhookPix(dto);

            // Assert
            verify(cobrancaRepository, never()).save(any());
        }

        @Test
        void dadoPayloadNulo_quandoReceberWebhookPix_entaoNaoProcessa() {
            // Act
            cobrancaService.processarNotificacaoWebhookPix(null);

            // Assert
            verifyNoInteractions(cobrancaRepository);
        }

        @Test
        void dadoListaPixVazia_quandoReceberWebhookPix_entaoNaoProcessa() {
            // Arrange
            PixWebhookDTO dto = new PixWebhookDTO(Collections.emptyList());

            // Act
            cobrancaService.processarNotificacaoWebhookPix(dto);

            // Assert
            verifyNoInteractions(cobrancaRepository);
        }

        @Test
        void dadoTxidVazio_quandoReceberWebhookPix_entaoIgnoraItem() {
            // Arrange
            PixWebhookDTO dto = new PixWebhookDTO(List.of(
                    new PixWebhookDTO.PixItemDTO("", "2026-04-15T13:02:30Z", new BigDecimal("25.50"))
            ));

            // Act
            cobrancaService.processarNotificacaoWebhookPix(dto);

            // Assert
            verify(cobrancaRepository, never()).findTopByTxidOrderByIdDesc(any());
            verify(cobrancaRepository, never()).save(any());
        }

        @Test
        void dadoTxidInexistente_quandoReceberWebhookPix_entaoIgnoraItem() {
            // Arrange
            when(cobrancaRepository.findTopByTxidOrderByIdDesc("txid-inexistente"))
                    .thenReturn(Optional.empty());

            PixWebhookDTO dto = new PixWebhookDTO(List.of(
                    new PixWebhookDTO.PixItemDTO("txid-inexistente", "2026-04-15T13:02:30Z", new BigDecimal("25.50"))
            ));

            // Act
            cobrancaService.processarNotificacaoWebhookPix(dto);

            // Assert
            verify(cobrancaRepository, never()).save(any());
        }

        @Test
        void dadoMultiplosItens_quandoReceberWebhookPix_entaoProcessaCadaUm() {
            // Arrange
            Cobranca cobrancaPendente = buildCobrancaBase();
            cobrancaPendente.setId(10L);
            cobrancaPendente.setStatus(CobrancaStatusEnum.SOLICITADA);
            cobrancaPendente.setTxid("txid-pendente");

            Cobranca cobrancaFinalizada = buildCobrancaBase();
            cobrancaFinalizada.setId(20L);
            cobrancaFinalizada.setStatus(CobrancaStatusEnum.FINALIZADA);
            cobrancaFinalizada.setTxid("txid-finalizada");

            when(cobrancaRepository.findTopByTxidOrderByIdDesc("txid-pendente"))
                    .thenReturn(Optional.of(cobrancaPendente));
            when(cobrancaRepository.findTopByTxidOrderByIdDesc("txid-finalizada"))
                    .thenReturn(Optional.of(cobrancaFinalizada));
            when(cobrancaRepository.save(any(Cobranca.class))).thenAnswer(invocation -> {
                Cobranca c = invocation.getArgument(0);
                c.setId(11L);
                return c;
            });

            PixWebhookDTO dto = new PixWebhookDTO(List.of(
                    new PixWebhookDTO.PixItemDTO("txid-pendente", "2026-04-15T13:02:30Z", new BigDecimal("25.50")),
                    new PixWebhookDTO.PixItemDTO("txid-finalizada", "2026-04-15T13:03:00Z", new BigDecimal("50.00"))
            ));

            // Act
            cobrancaService.processarNotificacaoWebhookPix(dto);

            // Assert
            verify(cobrancaRepository, times(1)).save(argThat(novaVersao -> {
                assertEquals(CobrancaStatusEnum.FINALIZADA, novaVersao.getStatus());
                assertEquals(new BigDecimal("25.50"), novaVersao.getValorPago());
                assertEquals(10L, novaVersao.getCobrancaPaiId());
                return true;
            }));
        }
    }

    @Nested
    class ValidarCheckoutTest {

        @Test
        void dadoCobrancaExistente_quandoValidarCheckout_entaoAtualizaERetornaCobrancaFinalizada() {
            // Arrange
            Cobranca cobranca = buildCobrancaBase();
            cobranca.setId(1L);
            cobranca.setMetodo(CobrancaMetodoEnum.CARTAO_CREDITO);
            cobranca.setStatus(CobrancaStatusEnum.SOLICITADA);
            cobranca.setTransactionId("txn-123");

            when(cobrancaRepository.findTopByTransactionIdOrderByIdDesc("txn-123"))
                    .thenReturn(Optional.of(cobranca));
            when(cobrancaRepository.save(any(Cobranca.class))).thenAnswer(invocation -> {
                Cobranca c = invocation.getArgument(0);
                c.setId(2L);
                return c;
            });

            CheckoutValidationRequestDTO request = new CheckoutValidationRequestDTO("AAABBB", "XYZ", "05");

            // Act
            CobrancaCompletoResponseDTO response = cobrancaService.validarCheckout("txn-123", request);

            // Assert
            assertNotNull(response);
            assertEquals(2L, response.id());
            assertEquals("FINALIZADA", response.status());
            assertEquals(new BigDecimal("25.50"), response.valorSolicitado());
            assertNotNull(response.dataFinalizada());
            verify(checkoutValidationClient).validarCheckout("txn-123", "AAABBB", "XYZ", "05");
            verify(cobrancaRepository).save(argThat(novaVersao -> {
                assertEquals(CobrancaStatusEnum.FINALIZADA, novaVersao.getStatus());
                assertEquals(new BigDecimal("25.50"), novaVersao.getValorPago());
                assertNotNull(novaVersao.getDataFinalizada());
                assertEquals(1L, novaVersao.getCobrancaPaiId());
                assertEquals("txn-123", novaVersao.getTransactionId());
                return true;
            }));
        }

        @Test
        void dadoTransactionIdInexistente_quandoValidarCheckout_entaoLancaCobrancaNaoEncontrada() {
            // Arrange
            when(cobrancaRepository.findTopByTransactionIdOrderByIdDesc("txn-inexistente"))
                    .thenReturn(Optional.empty());

            CheckoutValidationRequestDTO request = new CheckoutValidationRequestDTO("AAABBB", "XYZ", "05");

            // Act & Assert
            CobrancaNaoEncontradaException exception = assertThrows(
                    CobrancaNaoEncontradaException.class,
                    () -> cobrancaService.validarCheckout("txn-inexistente", request));

            assertEquals("Cobranca nao encontrada para transactionId: txn-inexistente", exception.getMessage());
            verify(checkoutValidationClient, never()).validarCheckout(any(), any(), any(), any());
            verify(cobrancaRepository, never()).save(any());
        }

        @Test
        void dadoCobrancaJaFinalizada_quandoValidarCheckout_entaoCriaNovaVersao() {
            // Arrange
            Cobranca cobranca = buildCobrancaBase();
            cobranca.setId(1L);
            cobranca.setMetodo(CobrancaMetodoEnum.CARTAO_CREDITO);
            cobranca.setStatus(CobrancaStatusEnum.FINALIZADA);
            cobranca.setTransactionId("txn-456");
            cobranca.setValorPago(new BigDecimal("25.50"));
            cobranca.setDataFinalizada(LocalDateTime.of(2026, 4, 15, 12, 0, 0));

            when(cobrancaRepository.findTopByTransactionIdOrderByIdDesc("txn-456"))
                    .thenReturn(Optional.of(cobranca));
            when(cobrancaRepository.save(any(Cobranca.class))).thenAnswer(invocation -> {
                Cobranca c = invocation.getArgument(0);
                c.setId(3L);
                return c;
            });

            CheckoutValidationRequestDTO request = new CheckoutValidationRequestDTO("CCC", "DDD", "02");

            // Act
            CobrancaCompletoResponseDTO response = cobrancaService.validarCheckout("txn-456", request);

            // Assert
            assertNotNull(response);
            assertEquals(3L, response.id());
            assertEquals("FINALIZADA", response.status());
            verify(checkoutValidationClient).validarCheckout("txn-456", "CCC", "DDD", "02");
            verify(cobrancaRepository).save(any(Cobranca.class));
        }

        @Test
        void dadoCobrancaCartaoSolicitada_quandoValidarCheckout_entaoRepassaDadosCompletos() {
            // Arrange
            Cobranca cobranca = buildCobrancaBase();
            cobranca.setId(5L);
            cobranca.setMetodo(CobrancaMetodoEnum.CARTAO_CREDITO);
            cobranca.setStatus(CobrancaStatusEnum.SOLICITADA);
            cobranca.setTransactionId("txn-789");

            when(cobrancaRepository.findTopByTransactionIdOrderByIdDesc("txn-789"))
                    .thenReturn(Optional.of(cobranca));
            when(cobrancaRepository.save(any(Cobranca.class))).thenAnswer(invocation -> {
                Cobranca c = invocation.getArgument(0);
                c.setId(6L);
                return c;
            });

            CheckoutValidationRequestDTO request = new CheckoutValidationRequestDTO("AAABBB", "XYZ", "05");

            // Act
            CobrancaCompletoResponseDTO response = cobrancaService.validarCheckout("txn-789", request);

            // Assert
            assertEquals(6L, response.id());
            assertEquals("CARTAO_CREDITO", response.metodo());
            assertEquals("FINALIZADA", response.status());
            assertEquals(new BigDecimal("25.50"), response.valorSolicitado());
            assertEquals(new BigDecimal("25.50"), response.valorPago());
            assertNotNull(response.dataFinalizada());
            assertNotNull(response.dataCriacao());
        }
    }

    private Cobranca buildCobrancaBase() {
        Cobranca cobranca = new Cobranca();
        cobranca.setIdUsuario("user-001");
        cobranca.setNomeSolicitante("Joao Silva");
        cobranca.setTipo(CobrancaTipoEnum.RECARGA);
        cobranca.setMetodo(CobrancaMetodoEnum.PIX);
        cobranca.setValorSolicitacao(new BigDecimal("25.50"));
        cobranca.setDataCriacao(LocalDateTime.of(2026, 4, 15, 10, 0, 0));
        return cobranca;
    }
}
