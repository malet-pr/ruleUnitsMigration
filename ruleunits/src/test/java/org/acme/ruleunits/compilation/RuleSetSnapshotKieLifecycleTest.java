package org.acme.ruleunits.compilation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.acme.ruleunits.domain.ActivityOccurrence;
import org.acme.ruleunits.domain.WorkOrderEvaluation;
import org.acme.ruleunits.domain.WorkOrderType;
import org.acme.ruleunits.runtime.ra1.Ra1RuntimeUnit;
import org.acme.ruleunits.snapshot.RuleSetLease;
import org.acme.ruleunits.snapshot.RuleSetSnapshotManager;
import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;

class RuleSetSnapshotKieLifecycleTest {
    @Test
    void publicationKeepsRetiredKieResourcesUntilTheirLastLeaseDrains() {
        RuntimeRuleSetCompiler compiler = new RuntimeRuleSetCompiler();
        RuleSetSnapshotManager manager = new RuleSetSnapshotManager();
        RuleSetLease oldLease = null;
        RuleSetLease currentLease = null;
        try {
            CompiledRuleSet version17 = compiler.compile(RuntimeRuleSetFixture.renderedRuleSet());
            ReleaseId version17ReleaseId = version17.releaseId();
            manager.publish(version17);
            oldLease = manager.acquire();

            CompiledRuleSet replacement = compiler.compile(RuntimeRuleSetFixture.renderedRuleSet());
            ReleaseId replacementReleaseId = replacement.releaseId();
            manager.publish(replacement);

            assertThat(oldLease.version()).isEqualTo(17);
            assertThat(KieServices.get().getRepository().getKieModule(version17ReleaseId))
                    .isNotNull();
            assertThat(KieServices.get().getRepository().getKieModule(replacementReleaseId))
                    .isNotNull();

            oldLease.close();
            oldLease = null;
            assertThat(KieServices.get().getRepository().getKieModule(version17ReleaseId))
                    .isNull();

            currentLease = manager.acquire();
            Ra1RuntimeUnit data = new Ra1RuntimeUnit();
            data.getWorkOrders().add(new WorkOrderEvaluation(
                    "WO", "FM3X635", WorkOrderType.FINAL,
                    List.of(
                            ActivityOccurrence.original(1, "6T8121", null, 1),
                            ActivityOccurrence.original(2, "L81494", "CAT2", 1))));
            try (var instance = currentLease.createInstance("RA1", data)) {
                assertThat(instance.fire()).isEqualTo(2);
            }

            manager.close();
            assertThat(KieServices.get().getRepository().getKieModule(replacementReleaseId))
                    .isNotNull();
            currentLease.close();
            currentLease = null;
            assertThat(KieServices.get().getRepository().getKieModule(replacementReleaseId))
                    .isNull();
        } finally {
            if (oldLease != null) {
                oldLease.close();
            }
            if (currentLease != null) {
                currentLease.close();
            }
            manager.close();
        }
    }
}
