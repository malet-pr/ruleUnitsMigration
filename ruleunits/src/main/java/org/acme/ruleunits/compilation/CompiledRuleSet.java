package org.acme.ruleunits.compilation;

import java.util.Map;
import java.util.Set;
import org.drools.ruleunits.api.*;
import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;

/**
 * Owns one isolated runtime KIE container, its discovered Rule Units, and the repository module
 * for a rule-set version. Published instances are immutable for concurrent readers; only the
 * snapshot owner closes them after all leases drain.
 */
public final class CompiledRuleSet implements AutoCloseable {
    private final String name;
    private final long version;
    private final RenderedRuleSet rendered;
    private final ReleaseId releaseId;
    private KieContainer container;
    private Map<String, RuleUnit<?>> units;
    private volatile boolean closed;

    CompiledRuleSet(String name, long version, RenderedRuleSet rendered,
            ReleaseId releaseId, KieContainer container, Map<String, RuleUnit<?>> units) {
        this.name = name;
        this.version = version;
        this.rendered = rendered;
        this.releaseId = releaseId;
        this.container = container;
        this.units = Map.copyOf(units);
    }

    public String name() { return name; }
    public long version() { return version; }
    public RenderedRuleSet rendered() { return rendered; }
    ReleaseId releaseId() { return releaseId; }

    public Set<String> stages() { return units.keySet(); }

    @SuppressWarnings("unchecked")
    public <T extends RuleUnitData> RuleUnitInstance<T> createInstance(String stage, T data) {
        if (closed) throw new IllegalStateException("Compiled rule set is closed");
        RuleUnit<?> unit = units.get(stage);
        if (unit == null) throw new IllegalArgumentException("Unknown rule stage: " + stage);
        return ((RuleUnit<T>) unit).createInstance(data);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        KieContainer containerToDispose = container;
        container = null;
        units = Map.of();
        try {
            containerToDispose.dispose();
        } finally {
            KieServices.get().getRepository().removeKieModule(releaseId);
        }
    }
}
