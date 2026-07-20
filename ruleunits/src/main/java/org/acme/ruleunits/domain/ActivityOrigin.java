package org.acme.ruleunits.domain;
/**
 * Provenance marker distinguishing activities present before rule processing from occurrences
 * created by a rule. It supports persistence attribution and is not an exemption from processing
 * in subsequent stages.
 */
public enum ActivityOrigin { ORIGINAL, RULE }
