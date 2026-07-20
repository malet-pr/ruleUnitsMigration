package org.acme.ruleunits.snapshot;

import java.util.concurrent.atomic.AtomicBoolean;
import org.drools.ruleunits.api.RuleUnitData;
import org.drools.ruleunits.api.RuleUnitInstance;

/**
 * Reader-owned reference to one immutable compiled snapshot. It may create concurrent Rule Unit
 * instances until closed; closing releases the reader count but never directly closes the compiled
 * rule set.
 */
public final class RuleSetLease implements AutoCloseable {
    private final SnapshotSlot slot;
    private final AtomicBoolean closed = new AtomicBoolean();

    RuleSetLease(SnapshotSlot slot) {
        this.slot = slot;
    }

    public String ruleSetName() {
        return slot.name();
    }

    public long version() {
        return slot.version();
    }

    public <T extends RuleUnitData> RuleUnitInstance<T> createInstance(String stage, T data) {
        if (closed.get()) {
            throw new IllegalStateException("Rule-set lease is closed");
        }
        return slot.createInstance(stage, data);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            slot.release();
        }
    }
}
