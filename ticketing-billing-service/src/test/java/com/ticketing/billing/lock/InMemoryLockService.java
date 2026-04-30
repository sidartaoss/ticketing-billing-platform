package com.ticketing.billing.lock;

import java.util.concurrent.ConcurrentHashMap;

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
