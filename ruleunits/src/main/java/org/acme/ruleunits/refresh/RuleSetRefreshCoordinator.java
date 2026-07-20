package org.acme.ruleunits.refresh;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.acme.ruleunits.compilation.CompiledRuleSet;
import org.acme.ruleunits.compilation.RenderedRuleSet;
import org.acme.ruleunits.compilation.RuntimeRuleSetCompiler;
import org.acme.ruleunits.compilation.TraditionalDrlRenderer;
import org.acme.ruleunits.loading.LoadedRuleSetDefinition;
import org.acme.ruleunits.loading.RuleSetDefinitionSource;
import org.acme.ruleunits.loading.RuleSetDefinitionValidator;
import org.acme.ruleunits.snapshot.RuleSetSnapshotManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serializes load → validate → render → compile → publish for one configured rule set. Failed
 * candidates produce sanitized reports and never replace the last-known-good snapshot.
 */
public final class RuleSetRefreshCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger(RuleSetRefreshCoordinator.class);

    private final String ruleSetName;
    private final RuleSetDefinitionSource source;
    private final Consumer<LoadedRuleSetDefinition> validation;
    private final Function<LoadedRuleSetDefinition, RenderedRuleSet> assembly;
    private final Function<RenderedRuleSet, CompiledRuleSet> compilation;
    private final Consumer<CompiledRuleSet> publication;
    private final Supplier<String> correlationIds;
    private final Consumer<RuleSetRefreshResult> resultReporter;
    private final ReentrantLock refreshLock = new ReentrantLock(true);

    public RuleSetRefreshCoordinator(
            String ruleSetName,
            RuleSetDefinitionSource source,
            RuleSetDefinitionValidator validator,
            TraditionalDrlRenderer renderer,
            RuntimeRuleSetCompiler compiler,
            RuleSetSnapshotManager snapshots) {
        this(ruleSetName, source, validator::validate, renderer::render, compiler::compile,
                snapshots::publish, () -> UUID.randomUUID().toString(),
                RuleSetRefreshCoordinator::logResult);
    }

    RuleSetRefreshCoordinator(
            String ruleSetName,
            RuleSetDefinitionSource source,
            Consumer<LoadedRuleSetDefinition> validation,
            Function<LoadedRuleSetDefinition, RenderedRuleSet> assembly,
            Function<RenderedRuleSet, CompiledRuleSet> compilation,
            Consumer<CompiledRuleSet> publication,
            Supplier<String> correlationIds,
            Consumer<RuleSetRefreshResult> resultReporter) {
        this.ruleSetName = Objects.requireNonNull(ruleSetName);
        this.source = Objects.requireNonNull(source);
        this.validation = Objects.requireNonNull(validation);
        this.assembly = Objects.requireNonNull(assembly);
        this.compilation = Objects.requireNonNull(compilation);
        this.publication = Objects.requireNonNull(publication);
        this.correlationIds = Objects.requireNonNull(correlationIds);
        this.resultReporter = Objects.requireNonNull(resultReporter);
    }

    public RuleSetRefreshResult refresh() {
        refreshLock.lock();
        try {
            return refreshUnderLock();
        } finally {
            refreshLock.unlock();
        }
    }

    private RuleSetRefreshResult refreshUnderLock() {
        String correlationId = Objects.requireNonNull(correlationIds.get());
        LoadedRuleSetDefinition definition = null;
        RuleSetRefreshPhase phase = RuleSetRefreshPhase.LOAD;
        try {
            definition = Objects.requireNonNull(source.loadActive(ruleSetName),
                    "Rule-set definition source returned null");
            if (!ruleSetName.equals(definition.name())) {
                throw new IllegalStateException("Loaded rule-set name does not match the request");
            }

            phase = RuleSetRefreshPhase.VALIDATE;
            validation.accept(definition);

            phase = RuleSetRefreshPhase.ASSEMBLE;
            RenderedRuleSet rendered = Objects.requireNonNull(assembly.apply(definition),
                    "DRL assembler returned null");

            phase = RuleSetRefreshPhase.COMPILE;
            CompiledRuleSet compiled = Objects.requireNonNull(compilation.apply(rendered),
                    "Rule-set compiler returned null");

            phase = RuleSetRefreshPhase.PUBLISH;
            publication.accept(compiled);

            RuleSetRefreshResult result = RuleSetRefreshResult.published(
                    ruleSetName, definition.version(), correlationId);
            report(result);
            return result;
        } catch (RuntimeException failure) {
            Long attemptedVersion = definition == null ? null : definition.version();
            RuleSetRefreshResult result = RuleSetRefreshResult.failed(
                    ruleSetName, attemptedVersion, correlationId, phase, failure);
            report(result);
            return result;
        }
    }

    private void report(RuleSetRefreshResult result) {
        try {
            resultReporter.accept(result);
        } catch (RuntimeException | Error ignored) {
            // Observability must not change publication or last-known-good behavior.
        }
    }

    private static void logResult(RuleSetRefreshResult result) {
        if (result.published()) {
            LOGGER.info(
                    "rule_set_refresh_published ruleSet={} version={} correlationId={}",
                    result.ruleSetName(), result.attemptedVersion(), result.correlationId());
            return;
        }
        LOGGER.warn(
                "rule_set_refresh_failed ruleSet={} attemptedVersion={} phase={} "
                        + "correlationId={} failureType={} summary={}",
                result.ruleSetName(),
                result.attemptedVersion() == null ? "unknown" : result.attemptedVersion(),
                result.failurePhase(), result.correlationId(), result.failureType(), result.summary());
    }
}
