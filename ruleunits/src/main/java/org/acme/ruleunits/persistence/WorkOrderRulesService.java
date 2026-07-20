package org.acme.ruleunits.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.acme.ruleunits.action.InvalidActivityAdditionException;
import org.acme.ruleunits.catalog.ActivityCatalog;
import org.acme.ruleunits.domain.WorkOrderEvaluation;
import org.acme.ruleunits.orchestration.RuleStageExecutionException;
import org.acme.ruleunits.orchestration.SelectedRulesOrchestrator;
import org.acme.ruleunits.orchestration.WorkOrderRulesBatch;
import org.acme.ruleunits.orchestration.WorkOrderRulesEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application service that deduplicates and loads requested work orders, opens one batch snapshot,
 * executes them sequentially, and persists every successful RA stage. Missing work orders are
 * ignored, nonmatching work orders are still saved, and a blocked stage retains earlier commits
 * while recording an incident.
 */
public final class WorkOrderRulesService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkOrderRulesService.class);
    private static final int MAX_ERROR_DETAIL_LENGTH = 500;

    private final WorkOrderRepository repository;
    private final WorkOrderMapper mapper;
    private final WorkOrderRulesEngine rulesEngine;
    private final RuleIncidentRecorder incidentRecorder;
    private final Clock clock;

    public WorkOrderRulesService(WorkOrderRepository repository, WorkOrderMapper mapper,
            ActivityCatalog activityCatalog) {
        this(repository, mapper, staticEngine(activityCatalog),
                RuleIncidentRecorder.noOp(), Clock.systemUTC());
    }

    public WorkOrderRulesService(WorkOrderRepository repository, WorkOrderMapper mapper,
            ActivityCatalog activityCatalog, RuleIncidentRecorder incidentRecorder, Clock clock) {
        this(repository, mapper, staticEngine(activityCatalog), incidentRecorder, clock);
    }

    public WorkOrderRulesService(WorkOrderRepository repository, WorkOrderMapper mapper,
            WorkOrderRulesEngine rulesEngine) {
        this(repository, mapper, rulesEngine, RuleIncidentRecorder.noOp(), Clock.systemUTC());
    }

    public WorkOrderRulesService(WorkOrderRepository repository, WorkOrderMapper mapper,
            WorkOrderRulesEngine rulesEngine, RuleIncidentRecorder incidentRecorder, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.mapper = Objects.requireNonNull(mapper);
        this.rulesEngine = Objects.requireNonNull(rulesEngine);
        this.incidentRecorder = Objects.requireNonNull(incidentRecorder);
        this.clock = Objects.requireNonNull(clock);
    }

    public WorkOrderProcessingOutcome process(String workOrderNumber) {
        Optional<WorkOrderRecord> found = repository.findByNumber(workOrderNumber);
        if (found.isEmpty()) {
            return WorkOrderProcessingOutcome.missing();
        }
        try (WorkOrderRulesBatch batch = rulesEngine.openBatch()) {
            return process(found.orElseThrow(), batch);
        }
    }

    public List<WorkOrderProcessingOutcome> processBatch(List<String> workOrderNumbers) {
        Objects.requireNonNull(workOrderNumbers);
        LinkedHashSet<String> uniqueNumbers = new LinkedHashSet<>(workOrderNumbers);
        List<WorkOrderRecord> found = new ArrayList<>();
        for (String workOrderNumber : uniqueNumbers) {
            repository.findByNumber(Objects.requireNonNull(workOrderNumber))
                    .filter(record -> !record.getActivities().isEmpty())
                    .ifPresent(found::add);
        }
        if (found.isEmpty()) {
            return List.of();
        }

        List<WorkOrderProcessingOutcome> outcomes = new ArrayList<>(found.size());
        try (WorkOrderRulesBatch batch = rulesEngine.openBatch()) {
            found.forEach(record -> outcomes.add(process(record, batch)));
        }
        return List.copyOf(outcomes);
    }

    private WorkOrderProcessingOutcome process(
            WorkOrderRecord record, WorkOrderRulesBatch batch) {
        LOGGER.info("### Evaluando OT: {}", record.getNumber());
        logState("Estado inicial", record);
        WorkOrderEvaluation domain = mapper.toDomain(record);

        try {
            batch.execute(
                    domain,
                    (stageResult, stage) -> saveCompletedStage(record, stageResult, stage));
            if (record.getId() != null) {
                incidentRecorder.resolveOpenForWorkOrder(record.getId(), clock.instant());
            }
            logState("Estado final", record);
            return outcome(record);
        } catch (RuleStageExecutionException exception) {
            recordFailure(record, domain, exception);
            logState("Estado final", record);
            LOGGER.warn(
                    "Ejecución de reglas bloqueada: OT: {} - ÚLTIMA ETAPA: {} - ETAPA FALLIDA: {}",
                    record.getNumber(), record.getLastCompletedStage(), record.getFailedStage());
            return outcome(record);
        }
    }

    private void logState(String label, WorkOrderRecord record) {
        List<String> activities = record.getActivities().stream()
                .filter(ActivityRecord::isActive)
                .map(activity -> activity.getCode() + "-" + activity.getQuantity())
                .toList();
        LOGGER.info("{}: OT: {} - ACTIVIDADES: {}", label, record.getNumber(), activities);
    }

    private void saveCompletedStage(
            WorkOrderRecord record, WorkOrderEvaluation stageResult, String stage) {
        mapper.updateRecord(stageResult, record);
        record.setLastCompletedStage(stage);
        record.setFailedStage(null);
        record.setRuleErrorCode(null);
        record.setRuleErrorDetail(null);
        record.setRuleProcessingStatus(
                stage.equals("RA3") ? RuleProcessingStatus.COMPLETED : RuleProcessingStatus.IN_PROGRESS);
        repository.save(record);
    }

    private void recordFailure(WorkOrderRecord record, WorkOrderEvaluation lastSuccessfulState,
            RuleStageExecutionException exception) {
        mapper.updateRecord(lastSuccessfulState, record);
        record.setRuleProcessingStatus(RuleProcessingStatus.BLOCKED);
        record.setFailedStage(exception.getStage());
        Throwable cause = exception.getCause();
        String errorCode = cause instanceof InvalidActivityAdditionException
                ? "INVALID_ACTIVITY_ADDITION" : "RULE_STAGE_FAILURE";
        String detail = sanitize(cause == null ? exception.getMessage() : cause.getMessage());
        record.setRuleErrorCode(errorCode);
        record.setRuleErrorDetail(detail);
        if (record.getId() != null) {
            String ruleName = cause instanceof InvalidActivityAdditionException invalid
                    ? invalid.getRuleName() : null;
            incidentRecorder.record(new RuleIncidentRecorder.RuleIncident(
                    record.getId(), ruleName, exception.getStage(), errorCode, detail,
                    Instant.now(clock), UUID.randomUUID()));
        }
    }

    private String sanitize(String message) {
        String sanitized = message == null
                ? "Rule stage failed" : message.replaceAll("[\\r\\n\\t]+", " ").strip();
        return sanitized.length() <= MAX_ERROR_DETAIL_LENGTH
                ? sanitized : sanitized.substring(0, MAX_ERROR_DETAIL_LENGTH);
    }

    private WorkOrderProcessingOutcome outcome(WorkOrderRecord record) {
        return new WorkOrderProcessingOutcome(true, record.getRuleProcessingStatus(),
                record.getLastCompletedStage(), record.getFailedStage());
    }

    private static WorkOrderRulesEngine staticEngine(ActivityCatalog activityCatalog) {
        Objects.requireNonNull(activityCatalog);
        return (workOrder, stageSaver) -> new SelectedRulesOrchestrator(
                activityCatalog, stageSaver).execute(workOrder);
    }
}
