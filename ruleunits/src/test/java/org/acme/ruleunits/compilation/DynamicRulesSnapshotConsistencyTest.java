package org.acme.ruleunits.compilation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.acme.ruleunits.domain.ActivityOccurrence;
import org.acme.ruleunits.domain.WorkOrderEvaluation;
import org.acme.ruleunits.domain.WorkOrderType;
import org.acme.ruleunits.orchestration.DynamicRulesOrchestrator;
import org.acme.ruleunits.snapshot.RuleSetSnapshotManager;
import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;

class DynamicRulesSnapshotConsistencyTest {
    private static final String RA2_JOB_TYPES =
            "jobType in (\"KPVG961\", \"FM3X635\", \"FH1X042\", \"JM5G513\")";

    @Test
    void refreshDuringRa1SaveCannotMixRuleSetVersionsAcrossLaterStages() {
        RuntimeRuleSetCompiler compiler = new RuntimeRuleSetCompiler();
        RuleSetSnapshotManager snapshots = new RuleSetSnapshotManager();
        CompiledRuleSet replacement = null;
        boolean replacementPublished = false;
        try {
            CompiledRuleSet version17 = compiler.compile(RuntimeRuleSetFixture.renderedRuleSet());
            var version17ReleaseId = version17.releaseId();
            snapshots.publish(version17);

            replacement = compiler.compile(versionWithoutRa2Match());
            var replacementReleaseId = replacement.releaseId();
            AtomicBoolean refreshed = new AtomicBoolean();
            List<String> savedStages = new ArrayList<>();
            WorkOrderEvaluation workOrder = new WorkOrderEvaluation(
                    "WO", "KPVG961", WorkOrderType.FINAL,
                    List.of(ActivityOccurrence.original(1, "I51434", null, 1)));
            DynamicRulesOrchestrator orchestrator = new DynamicRulesOrchestrator(
                    code -> code.equals("FG2802"), snapshots);

            CompiledRuleSet publication = replacement;
            var result = orchestrator.execute(workOrder, (stageResult, stage) -> {
                savedStages.add(stage);
                if (stage.equals("RA1") && refreshed.compareAndSet(false, true)) {
                    snapshots.publish(publication);
                }
            });
            replacementPublished = true;

            assertThat(result.executionTrace()).containsExactly("RA1", "RA2", "RA3");
            assertThat(result.firedRules()).isEqualTo(1);
            assertThat(savedStages).containsExactly("RA1", "RA2", "RA3");
            assertThat(activeCodes(workOrder)).containsExactly("FG2802");
            assertThat(KieServices.get().getRepository().getKieModule(version17ReleaseId))
                    .isNull();
            assertThat(KieServices.get().getRepository().getKieModule(replacementReleaseId))
                    .isNotNull();
            try (var current = snapshots.acquire()) {
                assertThat(current.version()).isEqualTo(18L);
            }
        } finally {
            snapshots.close();
            if (!replacementPublished && replacement != null) {
                replacement.close();
            }
        }
    }

    @Test
    void refreshDuringBatchKeepsEveryWorkOrderOnTheBatchVersion() {
        RuntimeRuleSetCompiler compiler = new RuntimeRuleSetCompiler();
        RuleSetSnapshotManager snapshots = new RuleSetSnapshotManager();
        CompiledRuleSet replacement = null;
        AtomicBoolean replacementPublished = new AtomicBoolean();
        try {
            CompiledRuleSet version17 = compiler.compile(RuntimeRuleSetFixture.renderedRuleSet());
            var version17ReleaseId = version17.releaseId();
            snapshots.publish(version17);

            replacement = compiler.compile(versionWithoutRa2Match());
            var replacementReleaseId = replacement.releaseId();
            DynamicRulesOrchestrator orchestrator = new DynamicRulesOrchestrator(
                    code -> code.equals("FG2802"), snapshots);
            WorkOrderEvaluation first = workOrder("WO-1");
            WorkOrderEvaluation second = workOrder("WO-2");

            CompiledRuleSet publication = replacement;
            try (var batch = orchestrator.openBatch()) {
                batch.execute(first, (stageResult, stage) -> {
                    if (stage.equals("RA1")
                            && replacementPublished.compareAndSet(false, true)) {
                        snapshots.publish(publication);
                    }
                });
                batch.execute(second, (stageResult, stage) -> {});

                assertThat(activeCodes(first)).containsExactly("FG2802");
                assertThat(activeCodes(second)).containsExactly("FG2802");
                assertThat(KieServices.get().getRepository().getKieModule(version17ReleaseId))
                        .isNotNull();
                try (var current = snapshots.acquire()) {
                    assertThat(current.version()).isEqualTo(18L);
                }
            }

            assertThat(KieServices.get().getRepository().getKieModule(version17ReleaseId))
                    .isNull();
            assertThat(KieServices.get().getRepository().getKieModule(replacementReleaseId))
                    .isNotNull();

            WorkOrderEvaluation laterBatch = workOrder("WO-3");
            orchestrator.execute(laterBatch, (stageResult, stage) -> {});
            assertThat(activeCodes(laterBatch)).containsExactly("I51434");
        } finally {
            snapshots.close();
            if (!replacementPublished.get() && replacement != null) {
                replacement.close();
            }
        }
    }

    private static WorkOrderEvaluation workOrder(String number) {
        return new WorkOrderEvaluation(
                number, "KPVG961", WorkOrderType.FINAL,
                List.of(ActivityOccurrence.original(1, "I51434", null, 1)));
    }

    private static RenderedRuleSet versionWithoutRa2Match() {
        RenderedRuleSet selected = RuntimeRuleSetFixture.renderedRuleSet();
        List<RenderedRuleStage> stages = selected.stages().stream()
                .map(stage -> stage.stageCode().equals("RA2")
                        ? new RenderedRuleStage(
                                stage.stageCode(),
                                stage.unitDataClassName(),
                                stage.sourcePath(),
                                stage.drl().replace(RA2_JOB_TYPES, "jobType == \"NEVER\""))
                        : stage)
                .toList();
        assertThat(stages.get(1).drl())
                .doesNotContain(RA2_JOB_TYPES)
                .contains("jobType == \"NEVER\"");
        return new RenderedRuleSet(selected.name(), 18, stages);
    }

    private static List<String> activeCodes(WorkOrderEvaluation workOrder) {
        return workOrder.getActivities().stream()
                .filter(ActivityOccurrence::isActive)
                .map(ActivityOccurrence::getCode)
                .toList();
    }
}
