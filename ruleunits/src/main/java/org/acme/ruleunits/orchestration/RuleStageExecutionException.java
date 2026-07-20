package org.acme.ruleunits.orchestration;

import java.util.Objects;

/**
 * Marks the RA stage whose execution or action application failed. The persistence service uses
 * the stage boundary to retain earlier commits and avoid saving the failing stage.
 */
public final class RuleStageExecutionException extends RuntimeException {
    private final String stage;

    public RuleStageExecutionException(String stage, RuntimeException cause) {
        super("Rule stage failed: " + stage, cause);
        this.stage = Objects.requireNonNull(stage);
    }

    public String getStage() {
        return stage;
    }
}
