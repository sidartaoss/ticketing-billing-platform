package com.ticketing.billing.lock;

import com.ticketing.billing.exception.CobrancaEmAndamentoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LockExecutorTest {

    @Mock
    private LockService lockService;

    @InjectMocks
    private LockExecutor lockExecutor;

    @Test
    void executeWithLock_sucesso() {
        when(lockService.tryLock("key", 5)).thenReturn(true);

        String result = lockExecutor.executeWithLock("key", 5, () -> "ok");

        assertEquals("ok", result);
        verify(lockService).tryLock("key", 5);
        verify(lockService).unlock("key");
    }

    @Test
    void executeWithLock_lockIndisponivel_lancaExcecao() {
        when(lockService.tryLock("key", 5)).thenReturn(false);

        assertThrows(CobrancaEmAndamentoException.class,
                () -> lockExecutor.executeWithLock("key", 5, () -> "ok"));

        verify(lockService, never()).unlock(anyString());
    }

    @Test
    void executeWithLock_garanteUnlockNoFinally_mesmoComExcecao() {
        when(lockService.tryLock("key", 5)).thenReturn(true);

        assertThrows(RuntimeException.class,
                () -> lockExecutor.executeWithLock("key", 5, () -> {
                    throw new RuntimeException("Erro na acao");
                }));

        verify(lockService).unlock("key");
    }
}
