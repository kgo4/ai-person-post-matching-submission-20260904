package com.example.matching.service.common;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistributedLockServiceTest {

    @Test
    void acquireAndReleaseLock() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock rLock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        DistributedLockService service = new DistributedLockService(redissonClient);
        var handle = service.tryAcquire("test-lock");
        assertThat(handle).isNotNull();
        handle.close();

        verify(rLock).unlock();
    }

    @Test
    void secondAcquireFailsWhileLockHeld() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock rLock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true, false);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        DistributedLockService service = new DistributedLockService(redissonClient);
        var handle1 = service.tryAcquire("test-lock-2");
        assertThat(handle1).isNotNull();
        try {
            var handle2 = service.tryAcquire("test-lock-2");
            assertThat(handle2).isNull();
        } finally {
            handle1.close();
        }

        verify(rLock).unlock();
    }

    @Test
    void closeDoesNotUnlockWhenNotHeldByCurrentThread() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock rLock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(false);

        DistributedLockService service = new DistributedLockService(redissonClient);
        var handle = service.tryAcquire("test-lock-3");
        assertThat(handle).isNotNull();
        handle.close();

        verify(rLock, never()).unlock();
    }
}
