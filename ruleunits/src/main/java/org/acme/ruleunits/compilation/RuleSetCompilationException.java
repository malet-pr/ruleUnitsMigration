package org.acme.ruleunits.compilation;

import java.util.List;

/**
 * Sanitized failure raised when KIE cannot compile a complete rendered candidate. No partially
 * compiled candidate is eligible for publication.
 */
public final class RuleSetCompilationException extends RuntimeException {
    private final List<String> diagnostics;

    public RuleSetCompilationException(List<String> diagnostics) {
        super("Runtime rule-set compilation failed: " + String.join("; ", diagnostics));
        this.diagnostics = List.copyOf(diagnostics);
    }

    public List<String> getDiagnostics() {
        return diagnostics;
    }
}
