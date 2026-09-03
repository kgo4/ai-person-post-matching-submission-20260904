package com.example.matching.infrastructure.llm;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderConcurrencyGateTest {

    @Test
    void limitsConcurrentProviderCallsAcrossCallers() throws Exception {
        AiProviderConcurrencyGate gate = new AiProviderConcurrencyGate(2, 1, 0, 1_000, 1, 0);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();

        for (int i = 0; i < 3; i++) {
            executor.submit(() -> gate.execute(() -> {
                int current = active.incrementAndGet();
                peak.accumulateAndGet(current, Math::max);
                started.countDown();
                try {
                    release.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    active.decrementAndGet();
                }
                return "ok";
            }));
        }

        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(peak.get()).isEqualTo(2);
        release.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        assertThat(peak.get()).isEqualTo(2);
    }

    @Test
    void retriesConnectionResetWithCentralizedPolicy() {
        AiProviderConcurrencyGate gate = new AiProviderConcurrencyGate(1, 1, 0, 0, 3, 0);
        AtomicInteger attempts = new AtomicInteger();

        String result = gate.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RuntimeException(new IOException("Connection reset"));
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void rejectsWhenProviderQueueWaitExpires() throws Exception {
        AiProviderConcurrencyGate gate = new AiProviderConcurrencyGate(1, 1, 0, 0, 1, 0);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> gate.execute(() -> {
            entered.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "held";
        }));
        holder.start();
        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> gate.execute(() -> "unexpected"))
                .isInstanceOf(AiProviderBusyException.class);

        release.countDown();
        holder.join(2_000);
    }
}
