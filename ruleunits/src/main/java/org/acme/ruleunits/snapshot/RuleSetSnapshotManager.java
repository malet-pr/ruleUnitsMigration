package org.acme.ruleunits.snapshot;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.acme.ruleunits.compilation.CompiledRuleSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sole owner of published compiled rule sets. Publication atomically retires the prior snapshot,
 * new readers lease only the replacement, and retired resources close after their final lease
 * drains.
 */
public final class RuleSetSnapshotManager implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(RuleSetSnapshotManager.class);

    private final AtomicReference<SnapshotSlot> current = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ReentrantLock publicationLock = new ReentrantLock(true);
    private final Consumer<SnapshotRetirementFailure> retirementFailureHandler;

    public RuleSetSnapshotManager() {
        this(RuleSetSnapshotManager::logRetirementFailure);
    }

    RuleSetSnapshotManager(Consumer<SnapshotRetirementFailure> retirementFailureHandler) {
        this.retirementFailureHandler = Objects.requireNonNull(retirementFailureHandler);
    }

    public void publish(CompiledRuleSet compiledRuleSet) {
        publish(new CompiledRuleSetSnapshot(compiledRuleSet));
    }

    void publish(SnapshotRuntime runtime) {
        Objects.requireNonNull(runtime);
        SnapshotSlot candidate = new SnapshotSlot(runtime, retirementFailureHandler);
        publicationLock.lock();
        try {
            if (closed.get()) {
                candidate.retire();
                throw new IllegalStateException("Rule-set snapshot manager is closed");
            }
            SnapshotSlot previous = current.getAndSet(candidate);
            if (previous != null) {
                previous.retire();
            }
        } finally {
            publicationLock.unlock();
        }
    }

    public RuleSetLease acquire() {
        while (true) {
            SnapshotSlot selected = current.get();
            if (selected == null || closed.get()) {
                throw new RuleExecutionUnavailableException();
            }
            if (!selected.tryAcquire()) {
                continue;
            }
            if (current.get() == selected && !closed.get()) {
                return new RuleSetLease(selected);
            }
            selected.release();
        }
    }

    public boolean isAvailable() {
        return current.get() != null && !closed.get();
    }

    @Override
    public void close() {
        publicationLock.lock();
        try {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            SnapshotSlot previous = current.getAndSet(null);
            if (previous != null) {
                previous.retire();
            }
        } finally {
            publicationLock.unlock();
        }
    }

    private static void logRetirementFailure(SnapshotRetirementFailure failure) {
        LOGGER.warn(
                "rule_snapshot_retirement_failed ruleSet={} version={} failureType={} summary={}",
                failure.ruleSetName(), failure.version(), failure.failureType(), failure.summary());
    }
}
