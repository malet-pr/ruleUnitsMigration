package org.acme.ruleunits.compilation;

import java.util.*;
import org.drools.model.codegen.ExecutableModelProject;
import org.drools.ruleunits.api.RuleUnit;
import org.drools.ruleunits.impl.InternalRuleUnit;
import org.kie.api.KieServices;
import org.kie.api.builder.*;
import org.kie.api.runtime.KieContainer;

/**
 * Compiles all rendered stages together into an isolated executable-model module and discovers
 * their runtime Rule Units. Successful candidates retain owned KIE resources until retirement;
 * failed candidates expose no usable runtime state.
 */
public final class RuntimeRuleSetCompiler {
    public CompiledRuleSet compile(RenderedRuleSet rendered) {
        Objects.requireNonNull(rendered);
        KieServices services = KieServices.get();
        String artifact = normalize(rendered.name()) + "-v" + rendered.version()
                + "-" + UUID.randomUUID();
        ReleaseId releaseId = services.newReleaseId(
                "org.acme.ruleunits.runtime", artifact, "1.0.0");
        KieContainer container = null;
        try {
            KieFileSystem fileSystem = services.newKieFileSystem();
            fileSystem.generateAndWritePomXML(releaseId);
            rendered.stages().forEach(stage -> fileSystem.write(stage.sourcePath(), stage.drl()));

            KieBuilder builder = services.newKieBuilder(fileSystem)
                    .buildAll(ExecutableModelProject.class);
            List<String> diagnostics = builder.getResults()
                    .getMessages(Message.Level.ERROR).stream()
                    .map(this::diagnostic)
                    .toList();
            if (!diagnostics.isEmpty()) {
                throw new RuleSetCompilationException(diagnostics);
            }

            container = services.newKieContainer(releaseId);
            Map<String, RuleUnit<?>> units = discoverUnits(rendered, container.getClassLoader());
            return new CompiledRuleSet(
                    rendered.name(), rendered.version(), rendered, releaseId, container, units);
        } catch (RuntimeException | Error exception) {
            cleanup(services, releaseId, container, exception);
            throw exception;
        }
    }

    private void cleanup(KieServices services, ReleaseId releaseId,
            KieContainer container, Throwable failure) {
        if (container != null) {
            try {
                container.dispose();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
        try {
            services.getRepository().removeKieModule(releaseId);
        } catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private Map<String, RuleUnit<?>> discoverUnits(RenderedRuleSet rendered, ClassLoader classLoader) {
        Map<String, RuleUnit<?>> byDataClass = new HashMap<>();
        ServiceLoader.load(RuleUnit.class, classLoader).forEach(unit -> {
            if (unit instanceof InternalRuleUnit<?> internal) {
                byDataClass.put(internal.getRuleUnitDataClass().getCanonicalName(), unit);
            }
        });
        Map<String, RuleUnit<?>> byStage = new LinkedHashMap<>();
        for (RenderedRuleStage stage : rendered.stages()) {
            RuleUnit<?> unit = byDataClass.get(stage.unitDataClassName());
            if (unit == null) {
                throw new RuleSetCompilationException(List.of(
                        "Generated Rule Unit not found for " + stage.unitDataClassName()));
            }
            byStage.put(stage.stageCode(), unit);
        }
        return byStage;
    }

    private String diagnostic(Message message) {
        String text = message.getText() == null ? "KIE compilation error" : message.getText();
        int stackTrace = text.indexOf("Problem stacktrace");
        if (stackTrace >= 0) {
            text = text.substring(0, stackTrace);
        }
        return text.replaceAll("[\\r\\n\\t]+", " ").strip();
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
    }
}
