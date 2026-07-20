package org.acme.ruleunits.catalog;

/**
 * Read boundary used to decide whether an activity code is a valid creation target. Both
 * definition publication and runtime action application require the referenced activity to exist
 * and be active.
 */
@FunctionalInterface
public interface ActivityCatalog {

    boolean existsAndIsActive(String activityCode);
}
