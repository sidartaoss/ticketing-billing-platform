package com.ticketing.billing.service;

import com.ticketing.billing.domain.Cobranca;
import com.ticketing.billing.domain.CobrancaMetodoEnum;
import com.ticketing.billing.domain.CobrancaStatusEnum;
import com.ticketing.billing.domain.CobrancaTipoEnum;
import com.ticketing.billing.dto.CobrancaBasicoResponseDTO;
import com.ticketing.billing.dto.CobrancaRequestDTO;
import com.ticketing.billing.exception.CobrancaCriacaoException;
import com.ticketing.billing.exception.CobrancaEmAndamentoException;
import com.ticketing.billing.integration.UserContext;
import com.ticketing.billing.lock.LockExecutor;
import com.ticketing.billing.repository.CobrancaRepository;
import com.ticketing.billing.service.strategy.CobrancaCriacaoStrategy;
import com.ticketing.billing.service.strategy.CobrancaCriacaoStrategyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
    private CobrancaCriacaoStrategy pixStrategy;

    @InjectMocks
    private CobrancaService cobrancaService;

    @Test
    void criarCobranca_pix_sucesso() {
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

        CobrancaBasicoResponseDTO response = cobrancaService.criarCobranca(request);

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
    }

    @Test
    void criarCobranca_lockIndisponivel() {
        CobrancaRequestDTO request = new CobrancaRequestDTO(
                new BigDecimal("25.50"), CobrancaTipoEnum.RECARGA, CobrancaMetodoEnum.PIX);

        when(userContext.getIdUsuario()).thenReturn("user-001");

        when(lockExecutor.executeWithLock(anyString(), anyLong(), any()))
                .thenThrow(new CobrancaEmAndamentoException());

        assertThrows(CobrancaEmAndamentoException.class,
                () -> cobrancaService.criarCobranca(request));

        verify(cobrancaRepository, never()).save(any());
    }

    @Test
    void criarCobranca_erroInesperado_mapeiaParaCobrancaCriacaoException() {
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

        CobrancaCriacaoException exception = assertThrows(CobrancaCriacaoException.class,
                () -> cobrancaService.criarCobranca(request));

        assertEquals("Erro ao criar cobranca.", exception.getMessage());
        verify(cobrancaRepository, never()).save(any());
    }
}
