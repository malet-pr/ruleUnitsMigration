package org.acme.ruleunits.oracle;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class OracleIntegrationTest {
 @Container static final OracleContainer ORACLE =
   new OracleContainer("gvenzl/oracle-xe:21-slim-faststart");
 @DynamicPropertySource
 static void properties(DynamicPropertyRegistry registry){
  registry.add("spring.datasource.driver-class-name",ORACLE::getDriverClassName);
  registry.add("spring.datasource.url",ORACLE::getJdbcUrl);
  registry.add("spring.datasource.username",ORACLE::getUsername);
  registry.add("spring.datasource.password",ORACLE::getPassword);
  registry.add("spring.jpa.hibernate.ddl-auto",()->"none");
  registry.add("spring.jpa.open-in-view",()->"false");
 }
}
