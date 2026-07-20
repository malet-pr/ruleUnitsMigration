package org.acme.ruleunits.local;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.acme.ruleunits.RuleunitsApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.containers.OracleContainer;

/**
 * Development-only launcher for the complete application backed by a disposable Oracle database.
 *
 * <p>This class deliberately lives under {@code src/test}: Testcontainers and the synthetic SQL
 * fixtures are verification infrastructure and are not included in the production artifact.
 */
public final class LocalTestcontainersApplication {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(LocalTestcontainersApplication.class);
    private static final String ORACLE_IMAGE = "gvenzl/oracle-xe:21-slim-faststart";

    private LocalTestcontainersApplication() {}

    public static void main(String[] args) {
        OracleContainer oracle = new OracleContainer(ORACLE_IMAGE);
        AtomicBoolean stopped = new AtomicBoolean();
        Runnable stopOracle = () -> {
            if (stopped.compareAndSet(false, true)) {
                oracle.stop();
            }
        };

        try {
            oracle.start();
            LOGGER.info(
                    "Disposable Oracle Testcontainer is available on a random host port ({}); "
                            + "it will be removed when the application stops",
                    oracle.getMappedPort(1521));

            SpringApplication application = new SpringApplication(RuleunitsApplication.class);
            application.addInitializers(context -> context.getEnvironment()
                    .getPropertySources()
                    .addFirst(new MapPropertySource(
                            "localOracleTestcontainer", applicationProperties(oracle))));
            application.addListeners(
                    (ApplicationListener<ContextClosedEvent>) event -> stopOracle.run());
            application.run(args);
        } catch (RuntimeException | Error failure) {
            stopOracle.run();
            throw failure;
        }
    }

    static Map<String, Object> applicationProperties(OracleContainer oracle) {
        return Map.ofEntries(
                Map.entry("spring.datasource.driver-class-name", oracle.getDriverClassName()),
                Map.entry("spring.datasource.url", oracle.getJdbcUrl()),
                Map.entry("spring.datasource.username", oracle.getUsername()),
                Map.entry("spring.datasource.password", oracle.getPassword()),
                Map.entry("spring.jpa.hibernate.ddl-auto", "none"),
                Map.entry("spring.jpa.open-in-view", "false"),
                Map.entry("spring.sql.init.mode", "always"),
                Map.entry(
                        "spring.sql.init.schema-locations",
                        "classpath:sql/oracle-schema.sql,"
                                + "classpath:sql/oracle-rule-definition-schema.sql"),
                Map.entry(
                        "spring.sql.init.data-locations",
                        "classpath:sql/oracle-data.sql,"
                                + "classpath:sql/oracle-stage17-rule-set-data.sql"),
                Map.entry("rulebridge.rules.execution-endpoint-enabled", "true"));
    }
}
