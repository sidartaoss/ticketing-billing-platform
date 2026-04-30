package com.ticketing.billing.lock;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(RedisConnectionFactory.class)
public class InMemoryLockService implements LockService {

    private final ConcurrentHashMap<String, String> locks = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(String key, long ttlSeconds) {
        return locks.putIfAbsent(key, "locked") == null;
    }

    @Override
    public void unlock(String key) {
        locks.remove(key);
    }
}
