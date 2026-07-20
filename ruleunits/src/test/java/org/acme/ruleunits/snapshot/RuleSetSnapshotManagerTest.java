package org.acme.ruleunits.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.acme.ruleunits.runtime.ra1.Ra1RuntimeUnit;
import org.junit.jupiter.api.Test;

class RuleSetSnapshotManagerTest {
    @Test
    void executionIsUnavailableBeforeTheInitialSnapshotAndAfterShutdown() {
        RuleSetSnapshotManager manager = new RuleSetSnapshotManager();

        assertThat(manager.isAvailable()).isFalse();
        assertThatThrownBy(manager::acquire)
                .isInstanceOf(RuleExecutionUnavailableException.class);

        FakeSnapshotRuntime runtime = new FakeSnapshotRuntime(17);
        manager.publish(runtime);
        assertThat(manager.isAvailable()).isTrue();
        manager.close();

        assertThat(runtime.closeCalls()).isEqualTo(1);
        assertThat(manager.isAvailable()).isFalse();
        assertThatThrownBy(manager::acquire)
                .isInstanceOf(RuleExecutionUnavailableException.class);
    }

    @Test
    void publicationServesTheNewSnapshotWhileTheRetiredSnapshotDrains() {
        RuleSetSnapshotManager manager = new RuleSetSnapshotManager();
        FakeSnapshotRuntime version17 = new FakeSnapshotRuntime(17);
        FakeSnapshotRuntime version18 = new FakeSnapshotRuntime(18);
        manager.publish(version17);

        RuleSetLease oldLease = manager.acquire();
        manager.publish(version18);

        assertThat(oldLease.version()).isEqualTo(17);
        assertThat(version17.closeCalls()).isZero();
        try (RuleSetLease currentLease = manager.acquire()) {
            assertThat(currentLease.version()).isEqualTo(18);
        }

        oldLease.close();
        oldLease.close();

        assertThat(version17.closeCalls()).isEqualTo(1);
        assertThat(version18.closeCalls()).isZero();
        manager.close();
        assertThat(version18.closeCalls()).isEqualTo(1);
    }

    @Test
    void aLeaseCreatesInstancesOnlyUntilItIsClosed() {
        RuleSetSnapshotManager manager = new RuleSetSnapshotManager();
        FakeSnapshotRuntime runtime = new FakeSnapshotRuntime(17);
        manager.publish(runtime);
        RuleSetLease lease = manager.acquire();

        try (var instance = lease.createInstance("RA1", new Ra1RuntimeUnit())) {
            assertThat(instance).isNotNull();
        }
        lease.close();

        assertThat(runtime.instanceCalls()).isEqualTo(1);
        assertThatThrownBy(() -> lease.createInstance("RA1", new Ra1RuntimeUnit()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lease is closed");
        manager.close();
    }

    @Test
    void concurrentReadersKeepTheRetiredSnapshotOpenUntilEveryLeaseIsReleased() throws Exception {
        RuleSetSnapshotManager manager = new RuleSetSnapshotManager();
        FakeSnapshotRuntime version17 = new FakeSnapshotRuntime(17);
        FakeSnapshotRuntime version18 = new FakeSnapshotRuntime(18);
        manager.publish(version17);
        int readerCount = 24;
        CountDownLatch acquired = new CountDownLatch(readerCount);
        CountDownLatch release = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(readerCount)) {
            List<Future<Long>> readers = new ArrayList<>();
            for (int index = 0; index < readerCount; index++) {
                readers.add(executor.submit(() -> {
                    try (RuleSetLease lease = manager.acquire()) {
                        acquired.countDown();
                        release.await();
                        return lease.version();
                    }
                }));
            }
            assertThat(acquired.await(10, TimeUnit.SECONDS)).isTrue();

            manager.publish(version18);
            assertThat(version17.closeCalls()).isZero();
            try (RuleSetLease lease = manager.acquire()) {
                assertThat(lease.version()).isEqualTo(18);
            }

            release.countDown();
            for (Future<Long> reader : readers) {
                assertThat(reader.get()).isEqualTo(17);
            }
        }

        assertThat(version17.closeCalls()).isEqualTo(1);
        manager.close();
        assertThat(version18.closeCalls()).isEqualTo(1);
    }

    @Test
    void concurrentPublicationsRetireEveryDisplacedSnapshotExactlyOnce() throws Exception {
        RuleSetSnapshotManager manager = new RuleSetSnapshotManager();
        List<FakeSnapshotRuntime> runtimes = java.util.stream.LongStream.rangeClosed(1, 16)
                .mapToObj(FakeSnapshotRuntime::new)
                .toList();
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(runtimes.size())) {
            List<Future<Object>> publishers = runtimes.stream()
                    .map(runtime -> executor.submit(() -> {
                        start.await();
                        manager.publish(runtime);
                        return null;
                    }))
                    .toList();
            start.countDown();
            for (Future<?> publisher : publishers) {
                publisher.get();
            }
        }
        manager.close();

        assertThat(runtimes).allSatisfy(runtime ->
                assertThat(runtime.closeCalls()).isEqualTo(1));
    }

    @Test
    void publishingAfterShutdownClosesTheRejectedCandidate() {
        RuleSetSnapshotManager manager = new RuleSetSnapshotManager();
        manager.close();
        FakeSnapshotRuntime rejected = new FakeSnapshotRuntime(18);

        assertThatThrownBy(() -> manager.publish(rejected))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("manager is closed");
        assertThat(rejected.closeCalls()).isEqualTo(1);
    }
}
