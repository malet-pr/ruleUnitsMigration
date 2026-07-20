package org.acme.ruleunits.snapshot;

import java.util.Objects;
import java.util.Set;
import org.acme.ruleunits.compilation.CompiledRuleSet;
import org.drools.ruleunits.api.RuleUnitData;
import org.drools.ruleunits.api.RuleUnitInstance;

final class CompiledRuleSetSnapshot implements SnapshotRuntime {
    private final String name;
    private final long version;
    private final Set<String> stages;
    private CompiledRuleSet compiledRuleSet;

    CompiledRuleSetSnapshot(CompiledRuleSet compiledRuleSet) {
        this.compiledRuleSet = Objects.requireNonNull(compiledRuleSet);
        this.name = compiledRuleSet.name();
        this.version = compiledRuleSet.version();
        this.stages = Set.copyOf(compiledRuleSet.stages());
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
        return stages;
    }

    @Override
    public <T extends RuleUnitData> RuleUnitInstance<T> createInstance(String stage, T data) {
        CompiledRuleSet current = compiledRuleSet;
        if (current == null) {
            throw new IllegalStateException("Compiled rule-set snapshot is closed");
        }
        return current.createInstance(stage, data);
    }

    @Override
    public void close() {
        CompiledRuleSet current = compiledRuleSet;
        compiledRuleSet = null;
        if (current != null) {
            current.close();
        }
    }
}
