package org.acme.ruleunits.refresh;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import org.acme.ruleunits.snapshot.RuleExecutionUnavailableException;
import org.acme.ruleunits.snapshot.RuleSetSnapshotManager;

/**
 * Application boundary for single-flight lazy initialization and explicit full refresh. It
 * serializes those operations, makes execution unavailable when the first publication fails, and
 * otherwise preserves the coordinator's last-known-good snapshot; it does not load, compile, or
 * execute rules itself.
 */
public final class RuleSetRuntimeService {
    private final RuleSetSnapshotManager snapshots;
    private final RuleSetRefreshCoordinator refreshCoordinator;
    private final ReentrantLock operationLock = new ReentrantLock(true);
    private volatile long completedInitializationAttempts;

    public RuleSetRuntimeService(
            RuleSetSnapshotManager snapshots, RuleSetRefreshCoordinator refreshCoordinator) {
        this.snapshots = Objects.requireNonNull(snapshots);
        this.refreshCoordinator = Objects.requireNonNull(refreshCoordinator);
    }

    public void ensureInitialized() {
        if (snapshots.isAvailable()) {
            return;
        }
        long observedAttempts = completedInitializationAttempts;
        operationLock.lock();
        try {
            if (snapshots.isAvailable()) {
                return;
            }
            if (completedInitializationAttempts != observedAttempts) {
                throw new RuleExecutionUnavailableException();
            }

            RuleSetRefreshResult result;
            try {
                result = refreshCoordinator.refresh();
            } finally {
                completedInitializationAttempts++;
            }
            if (!result.published()) {
                throw new RuleExecutionUnavailableException();
            }
        } finally {
            operationLock.unlock();
        }
    }

    public RuleSetRefreshResult refresh() {
        operationLock.lock();
        try {
            return refreshCoordinator.refresh();
        } finally {
            operationLock.unlock();
        }
    }
}
