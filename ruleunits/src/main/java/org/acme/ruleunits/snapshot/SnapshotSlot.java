package org.acme.ruleunits.snapshot;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.drools.ruleunits.api.RuleUnitData;
import org.drools.ruleunits.api.RuleUnitInstance;

final class SnapshotSlot {
    private static final long RETIRED = Long.MIN_VALUE;
    private static final long REFERENCE_MASK = Long.MAX_VALUE;

    private final SnapshotRuntime runtime;
    private final String name;
    private final long version;
    private final Consumer<SnapshotRetirementFailure> retirementFailureHandler;
    private final AtomicLong state = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();

    SnapshotSlot(SnapshotRuntime runtime,
            Consumer<SnapshotRetirementFailure> retirementFailureHandler) {
        this.runtime = Objects.requireNonNull(runtime);
        this.name = runtime.name();
        this.version = runtime.version();
        this.retirementFailureHandler = Objects.requireNonNull(retirementFailureHandler);
    }

    String name() {
        return name;
    }

    long version() {
        return version;
    }

    boolean tryAcquire() {
        while (true) {
            long current = state.get();
            if (isRetired(current)) {
                return false;
            }
            if (references(current) == REFERENCE_MASK) {
                throw new IllegalStateException("Snapshot lease count overflow");
            }
            if (state.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    void release() {
        while (true) {
            long current = state.get();
            long references = references(current);
            if (references == 0) {
                throw new IllegalStateException("Snapshot lease count underflow");
            }
            long updated = current - 1;
            if (state.compareAndSet(current, updated)) {
                if (isRetired(updated) && references(updated) == 0) {
                    closeRuntime();
                }
                return;
            }
        }
    }

    void retire() {
        while (true) {
            long current = state.get();
            if (isRetired(current)) {
                return;
            }
            long retired = current | RETIRED;
            if (state.compareAndSet(current, retired)) {
                if (references(retired) == 0) {
                    closeRuntime();
                }
                return;
            }
        }
    }

    <T extends RuleUnitData> RuleUnitInstance<T> createInstance(String stage, T data) {
        return runtime.createInstance(stage, data);
    }

    boolean isRetired() {
        return isRetired(state.get());
    }

    long leaseCount() {
        return references(state.get());
    }

    private void closeRuntime() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            runtime.close();
        } catch (RuntimeException | Error failure) {
            reportRetirementFailure(failure);
        }
    }

    private void reportRetirementFailure(Throwable failure) {
        try {
            retirementFailureHandler.accept(new SnapshotRetirementFailure(
                    name, version, failure.getClass().getSimpleName(),
                    "Failed to release compiled rule-set snapshot resources"));
        } catch (RuntimeException | Error ignored) {
            // Cleanup/reporting failures must not invalidate an already published snapshot.
        }
    }

    private static boolean isRetired(long state) {
        return (state & RETIRED) != 0;
    }

    private static long references(long state) {
        return state & REFERENCE_MASK;
    }
}
