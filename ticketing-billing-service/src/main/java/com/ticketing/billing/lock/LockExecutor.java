package com.ticketing.billing.lock;

import com.ticketing.billing.exception.CobrancaEmAndamentoException;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class LockExecutor {

    private final LockService lockService;

    public LockExecutor(LockService lockService) {
        this.lockService = lockService;
    }

    public <T> T executeWithLock(String key, long ttlSeconds, Supplier<T> action) {
        boolean acquired = lockService.tryLock(key, ttlSeconds);
        if (!acquired) {
            throw new CobrancaEmAndamentoException();
        }
        try {
            return action.get();
        } finally {
            lockService.unlock(key);
        }
    }
}
