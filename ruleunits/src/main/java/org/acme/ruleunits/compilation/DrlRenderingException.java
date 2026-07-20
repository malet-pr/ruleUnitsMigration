package org.acme.ruleunits.compilation;

/**
 * Reports failure to assemble validated structured definitions into traditional DRL. Callers must
 * treat the candidate as unpublished and keep any current last-known-good snapshot.
 */
public final class DrlRenderingException extends RuntimeException {
    public DrlRenderingException(String message) {
        super(message);
    }
}
