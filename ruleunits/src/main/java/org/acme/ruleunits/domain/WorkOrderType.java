package org.acme.ruleunits.domain;
/**
 * Normalized rule-domain classification of the legacy work-order type column. It prevents
 * persistence encodings such as F from leaking into rule conditions.
 */
public enum WorkOrderType { FINAL, ADD }
