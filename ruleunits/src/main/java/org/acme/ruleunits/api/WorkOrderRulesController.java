package org.acme.ruleunits.api;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.acme.ruleunits.orchestration.RuleGroupExecutor;
import org.acme.ruleunits.orchestration.RuleGroupExecutorRegistry;
import org.acme.ruleunits.snapshot.RuleExecutionUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Opt-in HTTP adapter for the legacy-shaped work-order batch operation. It validates request shape
 * and size, resolves a configured group without hardcoding group A, and leaves lookup,
 * deduplication, execution, and persistence to application services.
 */
@RestController
@RequestMapping("/reglas")
@ConditionalOnProperty(
        name = "rulebridge.rules.execution-endpoint-enabled",
        havingValue = "true")
public class WorkOrderRulesController {
    private static final Pattern GROUP_CODE = Pattern.compile("[A-Za-z0-9_-]+");

    private final RuleGroupExecutorRegistry executors;
    private final int maxBatchSize;

    public WorkOrderRulesController(
            RuleGroupExecutorRegistry executors,
            @Value("${rulebridge.rules.execution.max-batch-size:100}") int maxBatchSize) {
        this.executors = executors;
        if (maxBatchSize < 1) {
            throw new IllegalArgumentException("Maximum batch size must be positive");
        }
        this.maxBatchSize = maxBatchSize;
    }

    @PostMapping("/correr-reglas")
    public ResponseEntity<?> execute(
            @RequestParam String agrupador,
            @RequestBody List<String> workOrderNumbers) {
        if (agrupador == null || !GROUP_CODE.matcher(agrupador).matches()) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_RULE_GROUP", null);
        }
        if (workOrderNumbers == null
                || workOrderNumbers.isEmpty()
                || workOrderNumbers.size() > maxBatchSize
                || workOrderNumbers.stream().anyMatch(
                        number -> number == null || number.isBlank())) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_WORK_ORDER_LIST", agrupador);
        }

        RuleGroupExecutor executor = executors.find(agrupador).orElse(null);
        if (executor == null) {
            return error(HttpStatus.NOT_FOUND, "RULE_GROUP_NOT_CONFIGURED", agrupador);
        }

        List<String> uniqueNumbers = List.copyOf(new LinkedHashSet<>(workOrderNumbers));
        try {
            executor.execute(uniqueNumbers);
        } catch (RuleExecutionUnavailableException exception) {
            return error(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "RULE_EXECUTION_UNAVAILABLE",
                    agrupador);
        }
        return ResponseEntity.ok(Boolean.TRUE);
    }

    private static ResponseEntity<RuleExecutionEndpointError> error(
            HttpStatus status, String code, String group) {
        return ResponseEntity.status(status).body(new RuleExecutionEndpointError(code, group));
    }
}
