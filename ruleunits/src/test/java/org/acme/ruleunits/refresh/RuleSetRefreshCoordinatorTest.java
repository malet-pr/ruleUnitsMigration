package org.acme.ruleunits.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.acme.ruleunits.compilation.CompiledRuleSet;
import org.acme.ruleunits.compilation.RenderedRuleSet;
import org.acme.ruleunits.loading.LoadedRuleSetDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RuleSetRefreshCoordinatorTest {
    @Test
    void executesEveryPhaseInOrderAndReportsThePublishedVersion() {
        List<String> calls = new ArrayList<>();
        List<RuleSetRefreshResult> reports = new ArrayList<>();
        LoadedRuleSetDefinition definition = definition(17);
        RenderedRuleSet rendered = rendered(17);
        CompiledRuleSet compiled = mock(CompiledRuleSet.class);
        RuleSetRefreshCoordinator coordinator = new RuleSetRefreshCoordinator(
                "ACTIVITY_RULES",
                name -> {
                    calls.add("LOAD");
                    return definition;
                },
                loaded -> calls.add("VALIDATE"),
                loaded -> {
                    calls.add("ASSEMBLE");
                    return rendered;
                },
                assembled -> {
                    calls.add("COMPILE");
                    return compiled;
                },
                candidate -> calls.add("PUBLISH"),
                () -> "correlation-17",
                reports::add);

        RuleSetRefreshResult result = coordinator.refresh();

        assertThat(calls).containsExactly("LOAD", "VALIDATE", "ASSEMBLE", "COMPILE", "PUBLISH");
        assertThat(result).isEqualTo(RuleSetRefreshResult.published(
                "ACTIVITY_RULES", 17, "correlation-17"));
        assertThat(reports).containsExactly(result);
    }

    @ParameterizedTest
    @EnumSource(RuleSetRefreshPhase.class)
    void reportsSanitizedPhaseFailureAndSkipsEveryLaterPhase(RuleSetRefreshPhase failurePhase) {
        List<RuleSetRefreshPhase> calls = new ArrayList<>();
        List<RuleSetRefreshResult> reports = new ArrayList<>();
        LoadedRuleSetDefinition definition = definition(17);
        CompiledRuleSet compiled = mock(CompiledRuleSet.class);
        RuleSetRefreshCoordinator coordinator = new RuleSetRefreshCoordinator(
                "ACTIVITY_RULES",
                name -> phase(calls, RuleSetRefreshPhase.LOAD, failurePhase, definition),
                loaded -> phase(calls, RuleSetRefreshPhase.VALIDATE, failurePhase),
                loaded -> phase(calls, RuleSetRefreshPhase.ASSEMBLE, failurePhase, rendered(17)),
                assembled -> phase(calls, RuleSetRefreshPhase.COMPILE, failurePhase, compiled),
                candidate -> phase(calls, RuleSetRefreshPhase.PUBLISH, failurePhase),
                () -> "correlation-failure",
                reports::add);

        RuleSetRefreshResult result = coordinator.refresh();

        assertThat(result.status()).isEqualTo(RuleSetRefreshStatus.FAILED);
        assertThat(result.failurePhase()).isEqualTo(failurePhase);
        assertThat(result.attemptedVersion())
                .isEqualTo(failurePhase == RuleSetRefreshPhase.LOAD ? null : 17L);
        assertThat(result.failureType()).isEqualTo("IllegalStateException");
        assertThat(result.summary()).doesNotContain("secret generated DRL");
        assertThat(calls.getLast()).isEqualTo(failurePhase);
        assertThat(calls).containsExactlyElementsOf(
                List.of(RuleSetRefreshPhase.values()).subList(0, failurePhase.ordinal() + 1));
        assertThat(reports).containsExactly(result);
    }

    @Test
    void serializesTheCompleteRefreshPipeline() throws Exception {
        AtomicInteger loadCall = new AtomicInteger();
        AtomicInteger correlation = new AtomicInteger();
        CountDownLatch firstLoadEntered = new CountDownLatch(1);
        CountDownLatch allowFirstLoad = new CountDownLatch(1);
        CountDownLatch secondLoadEntered = new CountDownLatch(1);
        RuleSetRefreshCoordinator coordinator = new RuleSetRefreshCoordinator(
                "ACTIVITY_RULES",
                name -> {
                    int call = loadCall.incrementAndGet();
                    if (call == 1) {
                        firstLoadEntered.countDown();
                        await(allowFirstLoad);
                    } else {
                        secondLoadEntered.countDown();
                    }
                    return definition(call);
                },
                loaded -> {},
                loaded -> rendered(loaded.version()),
                assembled -> mock(CompiledRuleSet.class),
                candidate -> {},
                () -> "correlation-" + correlation.incrementAndGet(),
                result -> {});

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<RuleSetRefreshResult> first = executor.submit(coordinator::refresh);
            assertThat(firstLoadEntered.await(10, TimeUnit.SECONDS)).isTrue();
            Future<RuleSetRefreshResult> second = executor.submit(coordinator::refresh);

            assertThat(secondLoadEntered.await(200, TimeUnit.MILLISECONDS)).isFalse();
            allowFirstLoad.countDown();

            assertThat(first.get().attemptedVersion()).isEqualTo(1L);
            assertThat(second.get().attemptedVersion()).isEqualTo(2L);
        }
    }

    @Test
    void aReportingFailureCannotChangeASuccessfulPublication() {
        RuleSetRefreshCoordinator coordinator = coordinatorWithReporter(result -> {
            throw new IllegalStateException("logging backend failed");
        });

        RuleSetRefreshResult result = coordinator.refresh();

        assertThat(result.published()).isTrue();
        assertThat(result.attemptedVersion()).isEqualTo(17L);
    }

    private RuleSetRefreshCoordinator coordinatorWithReporter(
            Consumer<RuleSetRefreshResult> reporter) {
        return new RuleSetRefreshCoordinator(
                "ACTIVITY_RULES",
                name -> definition(17),
                loaded -> {},
                loaded -> rendered(17),
                assembled -> mock(CompiledRuleSet.class),
                candidate -> {},
                () -> "correlation-17",
                reporter);
    }

    private static void phase(List<RuleSetRefreshPhase> calls,
            RuleSetRefreshPhase current, RuleSetRefreshPhase failurePhase) {
        calls.add(current);
        if (current == failurePhase) {
            throw new IllegalStateException("secret generated DRL and verbose KIE diagnostics");
        }
    }

    private static <T> T phase(List<RuleSetRefreshPhase> calls,
            RuleSetRefreshPhase current, RuleSetRefreshPhase failurePhase, T value) {
        phase(calls, current, failurePhase);
        return value;
    }

    private static LoadedRuleSetDefinition definition(long version) {
        LocalDateTime time = LocalDateTime.parse("2026-07-18T20:00:00");
        return new LoadedRuleSetDefinition(
                "ACTIVITY_RULES", version, time, time.plusSeconds(1), List.of());
    }

    private static RenderedRuleSet rendered(long version) {
        return new RenderedRuleSet("ACTIVITY_RULES", version, List.of());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test coordination");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating test", exception);
        }
    }
}
