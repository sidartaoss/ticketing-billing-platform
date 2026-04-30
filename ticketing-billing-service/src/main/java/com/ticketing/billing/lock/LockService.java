package com.ticketing.billing.lock;

public interface LockService {

    boolean tryLock(String key, long ttlSeconds);

    void unlock(String key);
}
