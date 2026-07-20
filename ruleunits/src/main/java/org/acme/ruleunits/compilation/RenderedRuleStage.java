package org.acme.ruleunits.compilation;

/**
 * One rendered stage resource, binding a stage code and Rule Unit identity to its traditional DRL.
 * Stage identities must remain distinct to avoid generated-class collisions.
 */
public record RenderedRuleStage(
        String stageCode,
        String unitDataClassName,
        String sourcePath,
        String drl) {}
