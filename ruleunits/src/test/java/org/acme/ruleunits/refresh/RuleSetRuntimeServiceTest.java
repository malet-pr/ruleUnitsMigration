package org.acme.ruleunits.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.acme.ruleunits.snapshot.RuleExecutionUnavailableException;
import org.acme.ruleunits.snapshot.RuleSetSnapshotManager;
import org.junit.jupiter.api.Test;

class RuleSetRuntimeServiceTest {
    @Test
    void anAvailableSnapshotSkipsLazyRefresh() {
        RuleSetSnapshotManager snapshots = mock(RuleSetSnapshotManager.class);
        RuleSetRefreshCoordinator coordinator = mock(RuleSetRefreshCoordinator.class);
        when(snapshots.isAvailable()).thenReturn(true);
        RuleSetRuntimeService runtime = new RuleSetRuntimeService(snapshots, coordinator);

        runtime.ensureInitialized();
        runtime.ensureInitialized();

        verify(coordinator, never()).refresh();
    }

    @Test
    void concurrentFirstCallsShareOneSuccessfulInitialization() throws Exception {
        RuleSetSnapshotManager snapshots = mock(RuleSetSnapshotManager.class);
        RuleSetRefreshCoordinator coordinator = mock(RuleSetRefreshCoordinator.class);
        AtomicBoolean available = new AtomicBoolean();
        CyclicBarrier initialChecks = new CyclicBarrier(2);
        Set<Thread> callers = ConcurrentHashMap.newKeySet();
        when(snapshots.isAvailable()).thenAnswer(invocation -> {
            if (callers.add(Thread.currentThread())) {
                await(initialChecks);
            }
            return available.get();
        });
        CountDownLatch refreshEntered = new CountDownLatch(1);
        CountDownLatch allowRefresh = new CountDownLatch(1);
        when(coordinator.refresh()).thenAnswer(invocation -> {
            refreshEntered.countDown();
            assertThat(allowRefresh.await(10, TimeUnit.SECONDS)).isTrue();
            available.set(true);
            return published();
        });
        RuleSetRuntimeService runtime = new RuleSetRuntimeService(snapshots, coordinator);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(runtime::ensureInitialized);
            Future<?> second = executor.submit(runtime::ensureInitialized);
            assertThat(refreshEntered.await(10, TimeUnit.SECONDS)).isTrue();

            allowRefresh.countDown();

            first.get();
            second.get();
        }
        verify(coordinator, times(1)).refresh();
    }

    @Test
    void concurrentFailedInitializationIsSharedAndALaterCallRetries() throws Exception {
        RuleSetSnapshotManager snapshots = mock(RuleSetSnapshotManager.class);
        RuleSetRefreshCoordinator coordinator = mock(RuleSetRefreshCoordinator.class);
        AtomicBoolean available = new AtomicBoolean();
        AtomicBoolean coordinateFirstCallers = new AtomicBoolean(true);
        CyclicBarrier initialChecks = new CyclicBarrier(2);
        Set<Thread> callers = ConcurrentHashMap.newKeySet();
        when(snapshots.isAvailable()).thenAnswer(invocation -> {
            if (coordinateFirstCallers.get() && callers.add(Thread.currentThread())) {
                await(initialChecks);
            }
            return available.get();
        });
        CountDownLatch refreshEntered = new CountDownLatch(1);
        CountDownLatch allowRefresh = new CountDownLatch(1);
        when(coordinator.refresh())
                .thenAnswer(invocation -> {
                    refreshEntered.countDown();
                    assertThat(allowRefresh.await(10, TimeUnit.SECONDS)).isTrue();
                    return failed();
                })
                .thenAnswer(invocation -> {
                    available.set(true);
                    return published();
                });
        RuleSetRuntimeService runtime = new RuleSetRuntimeService(snapshots, coordinator);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(runtime::ensureInitialized);
            Future<?> second = executor.submit(runtime::ensureInitialized);
            assertThat(refreshEntered.await(10, TimeUnit.SECONDS)).isTrue();

            allowRefresh.countDown();

            assertUnavailable(first);
            assertUnavailable(second);
        }

        coordinateFirstCallers.set(false);
        runtime.ensureInitialized();

        assertThat(available).isTrue();
        verify(coordinator, times(2)).refresh();
    }

    @Test
    void explicitRefreshAlwaysReturnsTheCoordinatorResult() {
        RuleSetSnapshotManager snapshots = mock(RuleSetSnapshotManager.class);
        RuleSetRefreshCoordinator coordinator = mock(RuleSetRefreshCoordinator.class);
        RuleSetRefreshResult failure = failed();
        when(coordinator.refresh()).thenReturn(failure);
        RuleSetRuntimeService runtime = new RuleSetRuntimeService(snapshots, coordinator);

        assertThat(runtime.refresh()).isSameAs(failure);

        verify(coordinator).refresh();
    }

    private static RuleSetRefreshResult published() {
        return RuleSetRefreshResult.published(
                "ACTIVITY_RULES", 17, "correlation-published");
    }

    private static RuleSetRefreshResult failed() {
        return RuleSetRefreshResult.failed(
                "ACTIVITY_RULES", 18L, "correlation-failed",
                RuleSetRefreshPhase.COMPILE, new IllegalStateException("not exposed"));
    }

    private static void assertUnavailable(Future<?> future) {
        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuleExecutionUnavailableException.class);
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Timed out coordinating lazy initialization", exception);
        }
    }
}
