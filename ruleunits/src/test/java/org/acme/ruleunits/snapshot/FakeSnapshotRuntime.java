package org.acme.ruleunits.snapshot;

import static org.mockito.Mockito.mock;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.drools.ruleunits.api.RuleUnitData;
import org.drools.ruleunits.api.RuleUnitInstance;

final class FakeSnapshotRuntime implements SnapshotRuntime {
    private final String name;
    private final long version;
    private final boolean failOnClose;
    private final AtomicInteger closeCalls = new AtomicInteger();
    private final AtomicInteger instanceCalls = new AtomicInteger();

    FakeSnapshotRuntime(long version) {
        this("ACTIVITY_RULES", version, false);
    }

    FakeSnapshotRuntime(String name, long version, boolean failOnClose) {
        this.name = name;
        this.version = version;
        this.failOnClose = failOnClose;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public long version() {
        return version;
    }

    @Override
    public Set<String> stages() {
        return Set.of("RA1", "RA2", "RA3");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends RuleUnitData> RuleUnitInstance<T> createInstance(String stage, T data) {
        instanceCalls.incrementAndGet();
        return (RuleUnitInstance<T>) mock(RuleUnitInstance.class);
    }

    @Override
    public void close() {
        closeCalls.incrementAndGet();
        if (failOnClose) {
            throw new IllegalStateException("unsanitized internal close detail");
        }
    }

    int closeCalls() {
        return closeCalls.get();
    }

    int instanceCalls() {
        return instanceCalls.get();
    }
}
