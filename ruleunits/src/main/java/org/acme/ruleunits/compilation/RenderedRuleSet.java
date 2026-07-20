package org.acme.ruleunits.compilation;

import java.util.List;

/**
 * Immutable collection of deterministic DRL stage resources for one named rule-set version. It is
 * compilation input and may contain sensitive generated rule text, so it is not an API or ordinary
 * log payload.
 */
public record RenderedRuleSet(String name, long version, List<RenderedRuleStage> stages) {
    public RenderedRuleSet {
        stages = List.copyOf(stages);
    }
}
