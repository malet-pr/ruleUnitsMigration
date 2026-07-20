package org.acme.ruleunits;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the migrated rules service. It starts the production bean graph but
 * leaves rule-set initialization to the lazy runtime boundary.
 */
@SpringBootApplication
public class RuleunitsApplication {

	public static void main(String[] args) {
		SpringApplication.run(RuleunitsApplication.class, args);
	}

}
