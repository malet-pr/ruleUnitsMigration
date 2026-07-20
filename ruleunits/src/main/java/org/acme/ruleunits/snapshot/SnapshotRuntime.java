package org.acme.ruleunits.snapshot;

import java.util.Set;
import org.drools.ruleunits.api.RuleUnitData;
import org.drools.ruleunits.api.RuleUnitInstance;

interface SnapshotRuntime extends AutoCloseable {
    String name();

    long version();

    Set<String> stages();

    <T extends RuleUnitData> RuleUnitInstance<T> createInstance(String stage, T data);

    @Override
    void close();
}
